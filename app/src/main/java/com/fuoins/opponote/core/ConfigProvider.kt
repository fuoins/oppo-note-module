package com.fuoins.opponote.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * 向宿主进程（便签）暴露模块配置。
 *
 * 只提供只读的 "all" 调用，返回模块 SharedPreferences 中的全部键值；
 * 不包含任何用户数据，暴露无敏感性。
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        if (method != "all") return bundle

        val ctx = context ?: return bundle
        val sp = ctx.getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)
        sp.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> bundle.putBoolean(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putFloat(key, value)
                is String -> bundle.putString(key, value)
            }
        }
        return bundle
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
