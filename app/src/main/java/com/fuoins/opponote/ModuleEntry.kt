package com.fuoins.opponote

import com.fuoins.opponote.core.FeatureRegistry
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.core.Prefs
import com.fuoins.opponote.util.XLog
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 模块入口，由 assets/xposed_init 指定。
 * 生命周期：onModuleLoaded（每个进程一次）→ onPackageLoaded（每个包一次）。
 */
class ModuleEntry : XposedModule() {

    private var processName: String = ""

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
        XLog.attach(this)
        XLog.i("module loaded: process=$processName, api=$apiVersion")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE) return

        XLog.attach(this)
        if (processName.isEmpty()) processName = TARGET_PACKAGE
        XLog.i("package loaded: ${param.packageName}, first=${param.isFirstPackage}, process=$processName")

        val context = HookContext(
            module = this,
            classLoader = param.defaultClassLoader,
            prefs = Prefs(getRemotePreferences(Prefs.GROUP)),
            processName = processName,
            targetPackage = TARGET_PACKAGE,
        )
        FeatureRegistry.installAll(context)
    }

    companion object {
        const val TARGET_PACKAGE = "com.coloros.note"
    }
}
