package com.fuoins.opponote.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** 字段类型 */
enum class FieldType { DATE, TEXT }

/** 一个待生成字段 */
data class FieldConfig(
    val type: FieldType,
    val text: String = "",
)

/** 日期间隔方式 */
enum class IntervalMode { DAILY, EVERY_N_DAYS, WEEKLY }

/** 排版方式 */
enum class LayoutMode { SPACE, NEWLINE }

data class DateConfig(
    /** SimpleDateFormat 模式，如 MM.dd / M月d日 / yyyy年M月d日 EEE HH:mm */
    val pattern: String = "MM.dd",
    val intervalMode: IntervalMode = IntervalMode.DAILY,
    val intervalDays: Int = 2,
    /** Calendar.DAY_OF_WEEK，仅 WEEKLY 模式使用 */
    val weekday: Int = Calendar.SATURDAY,
    /** 生成条数 */
    val count: Int = 10,
    /** 首条日期（当天 0 点） */
    val startDate: Long = 0L,
)

data class LayoutConfig(
    val mode: LayoutMode = LayoutMode.SPACE,
    /** SPACE 模式下字段之间的空格数 */
    val spaceCount: Int = 2,
    /** NEWLINE 模式下字段之间的空行数，0 表示每个字段独占一行 */
    val blankLines: Int = 0,
    /** 是否按列对齐（用空格补齐，中文按 2 个半角宽度计算） */
    val alignColumns: Boolean = true,
)

/**
 * 批量文本生成器：按字段顺序与日期间隔生成多行文本。
 *
 * 生成结果完全在本地计算，不涉及便签数据模型，也不依赖云同步字段。
 */
object BatchEngine {

    /** 常用格式预设，供界面直接选择 */
    val PATTERN_PRESETS = listOf(
        "MM.dd" to "11.04",
        "M月d日" to "11月4日",
        "yyyy.MM.dd" to "2026.11.04",
        "yyyy年M月d日" to "2026年11月4日",
        "M月d日 EEE" to "11月4日 周三",
        "yyyy年M月d日 EEE" to "2026年11月4日 周三",
        "MM.dd HH:mm" to "11.04 14:30",
        "EEE" to "周三",
        "HH:mm" to "14:30",
    )

    fun generate(
        fields: List<FieldConfig>,
        dateConfig: DateConfig,
        layout: LayoutConfig,
    ): String {
        if (fields.isEmpty()) return ""

        val dates = buildDates(dateConfig)
        val formatter = SimpleDateFormat(dateConfig.pattern, Locale.CHINA)

        // 每行一条记录，每个字段一列
        val rows: List<List<String>> = dates.map { date ->
            fields.map { field ->
                when (field.type) {
                    FieldType.DATE -> formatter.format(date)
                    FieldType.TEXT -> field.text
                }
            }
        }

        val columnWidths = if (layout.alignColumns) columnWidths(rows) else emptyList()

        val separator = when (layout.mode) {
            LayoutMode.SPACE -> " ".repeat(layout.spaceCount.coerceAtLeast(1))
            LayoutMode.NEWLINE -> "\n" + "\n".repeat(layout.blankLines.coerceAtLeast(0))
        }

        return rows.joinToString("\n") { row ->
            row.mapIndexed { index, cell ->
                if (layout.alignColumns && index < row.lastIndex) {
                    padToWidth(cell, columnWidths[index])
                } else {
                    cell
                }
            }.joinToString(separator)
        }
    }

    /** 生成日期序列，全部取 0 点，避免时区夏令时误差 */
    fun buildDates(config: DateConfig): List<Date> {
        val count = config.count.coerceIn(1, 2000)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = if (config.startDate > 0) config.startDate else System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (config.intervalMode) {
            IntervalMode.DAILY -> List(count) { i ->
                (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.time
            }

            IntervalMode.EVERY_N_DAYS -> {
                val step = config.intervalDays.coerceAtLeast(1)
                List(count) { i ->
                    (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i * step) }.time
                }
            }

            IntervalMode.WEEKLY -> {
                // 对齐到最近的指定星期几（含当天）
                val target = config.weekday.coerceIn(Calendar.SUNDAY, Calendar.SATURDAY)
                while (calendar.get(Calendar.DAY_OF_WEEK) != target) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                List(count) { i ->
                    (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i * 7) }.time
                }
            }
        }
    }

    private fun columnWidths(rows: List<List<String>>): List<Int> {
        val columns = rows.maxOfOrNull { it.size } ?: 0
        return (0 until columns).map { index ->
            rows.maxOfOrNull { row -> index.takeIf { it < row.size }?.let { displayWidth(row[it]) } ?: 0 } ?: 0
        }
    }

    /** 中日韩字符按 2 个半角宽度计算，用于等宽字体下的列对齐 */
    fun displayWidth(text: String): Int {
        var width = 0
        for (ch in text) {
            width += when {
                ch.code in 0x1100..0x115F -> 2
                ch.code in 0x2E80..0xA4CF -> 2
                ch.code in 0xAC00..0xD7A3 -> 2
                ch.code in 0xF900..0xFAFF -> 2
                ch.code in 0xFE30..0xFE6F -> 2
                ch.code in 0xFF00..0xFF60 -> 2
                ch.code in 0xFFE0..0xFFE6 -> 2
                else -> 1
            }
        }
        return width
    }

    private fun padToWidth(text: String, width: Int): String {
        val pad = max(0, width - displayWidth(text))
        return text + " ".repeat(pad)
    }
}
