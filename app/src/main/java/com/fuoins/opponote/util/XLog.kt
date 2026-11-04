package com.fuoins.opponote.util

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 统一日志出口：[XLog] 同时写入 LSPosed 框架日志与 logcat（TAG = OPPONote），
 * 方便在 LSPosed 管理器里直接排查，也方便 adb 抓取。
 */
object XLog {

    const val TAG = "OPPONote"

    @Volatile
    private var sink: XposedInterface? = null

    fun attach(api: XposedInterface) {
        sink = api
    }

    fun d(msg: String) = log(Log.DEBUG, msg, null)

    fun i(msg: String) = log(Log.INFO, msg, null)

    fun w(msg: String, throwable: Throwable? = null) = log(Log.WARN, msg, throwable)

    fun e(msg: String, throwable: Throwable? = null) = log(Log.ERROR, msg, throwable)

    private fun log(priority: Int, msg: String, throwable: Throwable?) {
        runCatching { sink?.log(priority, TAG, msg, throwable) }
        runCatching {
            if (throwable == null) {
                Log.println(priority, TAG, msg)
            } else {
                Log.println(priority, TAG, "$msg\n${Log.getStackTraceString(throwable)}")
            }
        }
    }
}
