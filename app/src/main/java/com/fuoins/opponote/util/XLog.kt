package com.fuoins.opponote.util

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * 宿主进程内日志。
 *
 * 三路输出：
 * 1. LSPosed 日志（XposedBridge.log）
 * 2. logcat，TAG = OPPONote
 * 3. [LogSink] → 模块设置的「运行日志」页面（跨进程落盘）
 *
 * 注意：模块自身的 Activity 进程不要调用本类，直接使用 android.util.Log。
 */
object XLog {

    const val TAG = "OPPONote"

    fun d(msg: String) = log(Log.DEBUG, "D", msg, null)

    fun i(msg: String) = log(Log.INFO, "I", msg, null)

    fun w(msg: String, throwable: Throwable? = null) = log(Log.WARN, "W", msg, throwable)

    fun e(msg: String, throwable: Throwable? = null) = log(Log.ERROR, "E", msg, throwable)

    private fun log(priority: Int, level: String, msg: String, throwable: Throwable?) {
        val detail = if (throwable == null) msg else "$msg\n${Log.getStackTraceString(throwable)}"

        runCatching { XposedBridge.log("[$TAG] $detail") }

        runCatching { Log.println(priority, TAG, detail) }

        // 日志通道自身出问题绝不能影响宿主
        runCatching { LogSink.record(level, detail) }
    }
}
