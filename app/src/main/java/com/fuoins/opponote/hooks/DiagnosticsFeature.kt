package com.fuoins.opponote.hooks

import android.os.Bundle
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog

/**
 * 框架自检：只读日志，不改变便签任何行为。
 * 用于确认模块是否成功注入、目标类是否可见、Hook 链路是否通畅。
 */
object DiagnosticsFeature : Feature {

    override val id = "diagnostics"
    override val title = "框架自检"
    override val summary = "记录模块注入状态与关键类信息，仅写日志，不修改便签行为"
    override val enabledByDefault = true

    private val PROBE_CLASSES = listOf(
        "com.nearme.note.MyApplication",
        "com.nearme.note.main.MainActivity",
        "com.nearme.note.db.NotesProvider",
    )

    override fun install(ctx: HookContext) {
        XLog.i("diagnostics: process=${ctx.processName}, mainProcess=${ctx.isMainProcess}")
        XLog.i("diagnostics: classLoader=${ctx.classLoader}")

        PROBE_CLASSES.forEach { name ->
            val state = if (ctx.findClass(name) != null) "OK" else "MISSING"
            XLog.i("diagnostics: $name -> $state")
        }

        // 只观察启动回调，用于验证 Hook 是否真正生效
        ctx.hookMethod("com.nearme.note.main.MainActivity", "onCreate", Bundle::class.java) { chain ->
            XLog.i("diagnostics: MainActivity.onCreate reached, hook chain works")
            chain.proceed()
        }
    }
}
