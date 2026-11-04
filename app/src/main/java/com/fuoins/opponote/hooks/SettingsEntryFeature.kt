package com.fuoins.opponote.hooks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.fuoins.opponote.ModuleEntry
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Proxy

/**
 * 在便签「设置」主列表中插入「模块设置」入口。
 *
 * 关键约束：本模块以 compileOnly 引用 androidx.preference，APK 内并未打包该类，
 * 而模块类由 Xposed 的模块 ClassLoader 加载（父加载器是 BootClassLoader），
 * 因此**不能**在代码里直接出现 androidx.preference 的类型引用，
 * 否则方法一执行就抛 NoClassDefFoundError，入口会静默消失。
 * 这里全部改为反射，类型只以字符串形式存在。
 */
object SettingsEntryFeature : Feature {

    private const val KEY = "fuoins_module_settings"
    private const val TITLE = "模块设置"
    private const val SUMMARY = "批量与排版 · 待办增强 · 首页样式 · 运行日志"

    override val id = "settings_entry"
    override val title = "设置页入口"
    override val summary = "在便签「设置」中添加「模块设置」"
    override val enabledByDefault = true

    override fun install(ctx: HookContext) {
        // 设置页可能不在主进程，所有进程都尝试挂载，代价极低
        val methods = ctx.findMethods(
            "com.nearme.note.setting.SettingFragment",
            "onCreatePreferences",
        )
        if (methods.isEmpty()) {
            XLog.w("settings entry: SettingFragment#onCreatePreferences 未找到")
            return
        }

        var hooked = 0
        methods.forEach { method ->
            if (ctx.hook(method, after = { param: XC_MethodHook.MethodHookParam ->
                    runCatching { addEntry(param.thisObject, ctx) }
                        .onFailure { XLog.e("add settings entry failed", it) }
                })
            ) hooked++
        }
        XLog.i("settings entry hooked=$hooked in ${ctx.processName}")
    }

    private fun addEntry(fragment: Any?, ctx: HookContext) {
        if (fragment == null) return
        val cl = fragment.javaClass.classLoader ?: return

        val screen = runCatching {
            fragment.javaClass.getMethod("getPreferenceScreen").invoke(fragment)
        }.getOrNull()

        if (screen == null) {
            // onCreatePreferences 阶段可能尚未 setPreferenceScreen，推迟一帧再试
            XLog.i("settings entry: screen 尚未就绪，延迟重试")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching { addEntry(fragment, ctx) }
                    .onFailure { XLog.e("add settings entry (retry) failed", it) }
            }, 120)
            return
        }

        val prefClass = Class.forName("androidx.preference.Preference", false, cl)

        val exists = runCatching {
            screen.javaClass.getMethod("findPreference", CharSequence::class.java)
                .invoke(screen, KEY)
        }.getOrNull()
        if (exists != null) return

        val context = runCatching {
            fragment.javaClass.getMethod("requireContext").invoke(fragment) as? Context
        }.getOrNull() ?: ctx.app

        val preference = prefClass.getConstructor(Context::class.java).newInstance(context)
        prefClass.getMethod("setKey", String::class.java).invoke(preference, KEY)
        prefClass.getMethod("setTitle", CharSequence::class.java).invoke(preference, TITLE)
        prefClass.getMethod("setSummary", CharSequence::class.java).invoke(preference, SUMMARY)
        prefClass.getMethod("setPersistent", Boolean::class.javaPrimitiveType)
            .invoke(preference, false)

        attachClickListener(prefClass, preference, context, cl)

        runCatching {
            screen.javaClass.getMethod("addPreference", prefClass).invoke(screen, preference)
            XLog.i("settings entry added")
        }.onFailure { XLog.e("settings entry addPreference failed", it) }
    }

    /** 用动态代理实现 Preference.OnPreferenceClickListener，避免编译期依赖 */
    private fun attachClickListener(
        prefClass: Class<*>,
        preference: Any,
        context: Context,
        classLoader: ClassLoader,
    ) {
        val listenerClass = Class.forName(
            "androidx.preference.Preference\$OnPreferenceClickListener",
            false,
            classLoader,
        )
        val proxy = Proxy.newProxyInstance(
            classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            when (method.name) {
                "onPreferenceClick" -> {
                    openModuleSettings(context)
                    true
                }
                "equals" -> args?.firstOrNull()?.let { it === preference } ?: false
                "hashCode" -> System.identityHashCode(preference)
                "toString" -> "OPPONoteSettingsClick"
                else -> null
            }
        }
        prefClass.getMethod("setOnPreferenceClickListener", listenerClass)
            .invoke(preference, proxy)
    }

    private fun openModuleSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent().apply {
                    setClassName(ModuleEntry.MODULE_PACKAGE, "com.fuoins.opponote.ui.SettingsActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { XLog.e("open module settings failed", it) }
    }
}
