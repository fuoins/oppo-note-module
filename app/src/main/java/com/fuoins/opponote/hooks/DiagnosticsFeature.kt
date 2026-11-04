package com.fuoins.opponote.hooks

import android.os.Bundle
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.XC_MethodHook

/**
 * 框架自检：只写日志，确认注入与 Hook 链路是否正常。
 */
object DiagnosticsFeature : Feature {

    override val id = "diagnostics"
    override val title = "框架自检"
    override val summary = "记录注入状态与关键类信息，仅写日志"
    override val enabledByDefault = false

    private val PROBE_CLASSES = listOf(
        "com.nearme.note.MyApplication",
        "com.nearme.note.main.MainActivity",
        "com.nearme.note.setting.SettingFragment",
        "com.nearme.note.activity.list.TodoAdapter",
    )

    override fun install(ctx: HookContext) {
        XLog.i("diagnostics: process=${ctx.processName}, main=${ctx.isMainProcess}")

        PROBE_CLASSES.forEach { name ->
            val state = if (ctx.findClass(name) != null) "OK" else "MISSING"
            XLog.i("diagnostics: $name -> $state")
        }

        ctx.hookMethod(
            "com.nearme.note.main.MainActivity",
            "onCreate",
            Bundle::class.java,
            after = { _: XC_MethodHook.MethodHookParam -> XLog.i("diagnostics: MainActivity.onCreate reached") },
        )
    }
}
