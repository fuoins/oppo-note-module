package com.fuoins.opponote.core

import android.content.Context
import java.io.File

/**
 * 模块进程侧的日志落盘。
 *
 * 宿主（便签）进程通过 [ConfigProvider] 把日志送进来，这里做环形裁剪，
 * 上限 [MAX_BYTES]，超出后丢掉前半部分，只保留最近的记录。
 */
object LogStore {

    private const val MAX_BYTES = 512 * 1024
    private const val TRIM_KEEP = 256 * 1024

    private val lock = Any()

    fun file(context: Context): File = File(context.filesDir, "logs/module.log")

    fun append(context: Context, text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            runCatching {
                val target = file(context)
                target.parentFile?.mkdirs()
                target.appendText(text)
                trimIfNeeded(target)
            }
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        runCatching {
            val target = file(context)
            if (target.exists()) target.readText() else ""
        }.getOrDefault("")
    }

    fun size(context: Context): Long = synchronized(lock) {
        runCatching { file(context).length() }.getOrDefault(0L)
    }

    fun clear(context: Context) {
        synchronized(lock) { runCatching { file(context).delete() } }
    }

    private fun trimIfNeeded(target: File) {
        if (target.length() <= MAX_BYTES) return

        val kept = runCatching {
            val all = target.readText()
            val start = (all.length - TRIM_KEEP).coerceAtLeast(0)
            // 从换行处切开，避免留下半行
            val cut = all.indexOf('\n', start).let { if (it >= 0 && it + 1 < all.length) it + 1 else start }
            val head = "----- 较早日志已截断 -----\n"
            head + all.substring(cut)
        }.getOrNull() ?: return

        val tmp = File(target.parentFile, "${target.name}.tmp")
        runCatching {
            tmp.writeText(kept)
            if (!tmp.renameTo(target)) {
                target.writeText(kept)
                tmp.delete()
            }
        }
    }
}
