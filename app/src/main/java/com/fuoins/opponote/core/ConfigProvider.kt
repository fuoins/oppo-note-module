package com.fuoins.opponote.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * 与宿主进程（便签）之间的唯一通道。
 *
 * - `all`：只读地返回模块 SharedPreferences 中的全部键值
 * - `log`：宿主把运行日志交过来落盘，供「运行日志」页面查看
 * - `log_read` / `log_clear` / `log_size`：日志的读取与维护
 *
 * 日志里只有模块自身的运行信息，不含笔记正文等用户数据。
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        val ctx = context ?: return bundle

        when (method) {
            "all" -> readConfig(ctx, bundle)
            "log" -> LogStore.append(ctx, extras?.getString("text").orEmpty())
            "log_read" -> bundle.putString("text", LogStore.read(ctx))
            "log_size" -> bundle.putLong("size", LogStore.size(ctx))
            "log_clear" -> LogStore.clear(ctx)
        }
        return bundle
    }

    private fun readConfig(ctx: Context, bundle: Bundle) {
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
