package com.fuoins.opponote

import android.app.Application
import android.app.Instrumentation
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

    /**
     * 双保险获取 Application：
     * 1. Instrumentation.callApplicationOnCreate —— 系统必然调用，参数就是 Application；
     * 2. Application.onCreate —— 兜底，兼容个别定制 ROM。
     * 任一先到即安装，另一路因 [installed] 直接退出。
     */
    private fun hookApplicationCreate(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            val method = Instrumentation::class.java
                .getDeclaredMethod("callApplicationOnCreate", Application::class.java)
            method.isAccessible = true
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.args.getOrNull(0) as? Application ?: return
                    if (!installed.compareAndSet(false, true)) return
                    runCatching { install(app, lpparam) }
                        .onFailure { XLog.e("install features failed", it) }
                }
            })
            XLog.i("hook Instrumentation.callApplicationOnCreate ok")
        }.onFailure { XLog.e("hook callApplicationOnCreate failed", it) }

        runCatching {
            val onCreate = Application::class.java.getDeclaredMethod("onCreate")
            onCreate.isAccessible = true
            XposedBridge.hookMethod(onCreate, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as? Application ?: return
                    if (!installed.compareAndSet(false, true)) return
                    runCatching { install(app, lpparam) }
                        .onFailure { XLog.e("install features failed", it) }
                }
            })
            XLog.i("hook Application.onCreate ok")
        }.onFailure { XLog.e("hook Application.onCreate failed", it) }
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
        XLog.i("all features processed, flushing log")
        LogSink.flushNow()
    }

    companion object {
        const val TARGET_PACKAGE = "com.coloros.note"
        const val MODULE_PACKAGE = "com.fuoins.opponote"
    }
}
