package com.fuoins.opponote.core

import android.content.Context
import android.net.Uri

/**
 * 宿主进程侧的只读配置。
 *
 * 模块 App 把配置写入自己的 SharedPreferences，宿主进程无法直接读取，
 * 因此通过模块 App 暴露的 [ConfigProvider] 一次性取回全部键值并缓存。
 */
class Prefs private constructor(private val values: Map<String, Any?>) {

    fun getBoolean(key: String, default: Boolean): Boolean = when (val v = values[key]) {
        is Boolean -> v
        is String -> v == "true" || v == "1"
        is Number -> v.toInt() != 0
        else -> default
    }

    fun getInt(key: String, default: Int): Int = when (val v = values[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }

    fun getString(key: String, default: String): String =
        values[key]?.toString()?.takeIf { it.isNotEmpty() } ?: default

    companion object {
        /** 与模块 App 侧 SharedPreferences 文件名保持一致 */
        const val GROUP = "config"

        const val AUTHORITY = "com.fuoins.opponote.config"

        fun load(context: Context): Prefs {
            val map = runCatching {
                val bundle = context.contentResolver.call(
                    Uri.parse("content://$AUTHORITY"),
                    "all",
                    null,
                    null,
                )
                bundle?.let { b -> b.keySet().associateWith { b.get(it) } }
            }.getOrNull() ?: emptyMap()
            return Prefs(map)
        }
    }
}
