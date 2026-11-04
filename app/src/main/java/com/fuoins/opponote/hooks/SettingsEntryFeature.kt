package com.fuoins.opponote.hooks

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.robv.android.xposed.XC_MethodHook
import com.fuoins.opponote.ModuleEntry
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog

/**
 * 在便签「设置」主列表中插入「模块设置」入口。
 *
 * 便签设置页由 com.nearme.note.setting.SettingsActivity 承载，
 * 实际列表是 SettingFragment（PreferenceFragmentCompat）。
 */
object SettingsEntryFeature : Feature {

    private const val KEY = "fuoins_module_settings"

    override val id = "settings_entry"
    override val title = "设置页入口"
    override val summary = "在便签「设置」中添加「模块设置」"
    override val enabledByDefault = true

    override fun install(ctx: HookContext) {
        if (!ctx.isMainProcess) {
            XLog.i("settings entry skipped: process=${ctx.processName}")
            return
        }

        val hooked = ctx.hookMethod(
            "com.nearme.note.setting.SettingFragment",
            "onCreatePreferences",
            Bundle::class.java,
            String::class.java,
            after = { param: XC_MethodHook.MethodHookParam ->
                runCatching { addEntry(param.thisObject) }
                    .onFailure { XLog.e("add settings entry failed", it) }
            },
        )
        XLog.i("settings entry hooked=$hooked")
    }

    private fun addEntry(fragment: Any) {
        val prefFragment = fragment as? PreferenceFragmentCompat
        if (prefFragment == null) {
            XLog.w("SettingFragment 不是 PreferenceFragmentCompat: ${fragment.javaClass.name}")
            return
        }
        val screen = prefFragment.preferenceScreen ?: return
        if (screen.findPreference<Preference>(KEY) != null) return

        val context = prefFragment.requireContext()
        val preference = Preference(context).apply {
            key = KEY
            title = "模块设置"
            summary = "批量与排版 · 待办增强 · 首页样式"
            setOnPreferenceClickListener {
                runCatching {
                    context.startActivity(
                        Intent().apply {
                            setClassName(
                                ModuleEntry.MODULE_PACKAGE,
                                "com.fuoins.opponote.ui.SettingsActivity",
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }.onFailure { XLog.e("open module settings failed", it) }
                true
            }
        }
        screen.addPreference(preference)
        XLog.i("settings entry added")
    }
}
