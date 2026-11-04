package com.fuoins.opponote

import android.app.Application
import com.fuoins.opponote.core.FeatureRegistry
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.core.Prefs
import com.fuoins.opponote.util.LogSink
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块入口，由 assets/xposed_init 指定。
 * 使用传统 XposedBridge API（82），兼容所有 LSPosed 版本，不受 libxposed API 版本迭代影响。
 */
class ModuleEntry : IXposedHookLoadPackage {

    private val installed = AtomicBoolean(false)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XLog.i("loadPackage: process=${lpparam.processName}, first=${lpparam.isFirstApplication}")
        runCatching { hookApplicationCreate(lpparam) }
            .onFailure { XLog.e("hook Application.onCreate failed", it) }
    }

    private fun hookApplicationCreate(lpparam: XC_LoadPackage.LoadPackageParam) {
        val onCreate = Application::class.java.getDeclaredMethod("onCreate")
        XposedBridge.hookMethod(onCreate, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val app = param.thisObject as? Application ?: return
                if (!installed.compareAndSet(false, true)) return
                runCatching { install(app, lpparam) }
                    .onFailure { XLog.e("install features failed", it) }
            }
        })
        XLog.i("waiting for Application.onCreate")
    }

    private fun install(app: Application, lpparam: XC_LoadPackage.LoadPackageParam) {
        // 先接入日志通道，后续任何一步失败都能在「运行日志」里看到
        LogSink.attach(app)
        XLog.i("Application ready, installing features")

        val context = HookContext(
            app = app,
            classLoader = lpparam.classLoader,
            prefs = Prefs.load(app),
            processName = lpparam.processName ?: TARGET_PACKAGE,
            targetPackage = TARGET_PACKAGE,
        )
        FeatureRegistry.installAll(context)
        LogSink.flushNow()
    }

    companion object {
        const val TARGET_PACKAGE = "com.coloros.note"
        const val MODULE_PACKAGE = "com.fuoins.opponote"
    }
}
