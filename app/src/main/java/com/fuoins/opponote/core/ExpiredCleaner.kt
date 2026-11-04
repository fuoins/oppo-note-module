package com.fuoins.opponote.core

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 过期日历项清理：删除“行首可解析为日期且早于今天”的整行。
 *
 * 用于「日历 + 完成 + 忘记」这类每天一行、需要自动淘汰历史记录的清单。
 */
object ExpiredCleaner {

    data class Result(
        val text: String,
        val removedLines: Int,
        val keptLines: Int,
    )

    fun clean(text: String, pattern: String, now: Long = System.currentTimeMillis()): Result {
        if (text.isBlank()) return Result(text, 0, 0)

        val formatter = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var removed = 0
        var kept = 0

        val output = text.lineSequence().filterNot { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@filterNot false

            val position = ParsePosition(0)
            val parsed = runCatching { formatter.parse(trimmed, position) }.getOrNull()
            // 必须从行首成功解析，且解析出的日期严格早于今天
            val expired = parsed != null && position.index > 0 && parsed.time < todayStart
            if (expired) {
                removed++
            } else {
                kept++
            }
            expired
        }.joinToString("\n")

        return Result(output, removed, kept)
    }
}
