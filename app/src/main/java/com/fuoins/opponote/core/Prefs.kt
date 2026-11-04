package com.fuoins.opponote.core

import android.content.SharedPreferences

/**
 * 模块配置。宿主进程通过 libxposed 的 remote preferences 读取，
 * 模块 App 通过同名 SharedPreferences 写入，两者共用 [GROUP]。
 */
class Prefs(private val sp: SharedPreferences) {

    fun isEnabled(featureId: String, default: Boolean): Boolean =
        runCatching { sp.getBoolean(key(featureId), default) }.getOrDefault(default)

    fun setEnabled(featureId: String, enabled: Boolean) {
        runCatching { sp.edit().putBoolean(key(featureId), enabled).apply() }
    }

    companion object {
        /** SharedPreferences 文件名，必须与模块 App 侧保持一致 */
        const val GROUP = "config"

        fun key(featureId: String): String = "feature_$featureId"
    }
}
