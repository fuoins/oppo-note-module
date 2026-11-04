package com.fuoins.opponote.util

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * 宿主进程内日志：同时输出到 LSPosed 日志与 logcat（TAG = OPPONote）。
 * 注意：模块自身的 Activity 进程不要调用本类，直接使用 android.util.Log。
 */
object XLog {

    const val TAG = "OPPONote"

    fun d(msg: String) = log(Log.DEBUG, msg, null)

    fun i(msg: String) = log(Log.INFO, msg, null)

    fun w(msg: String, throwable: Throwable? = null) = log(Log.WARN, msg, throwable)

    fun e(msg: String, throwable: Throwable? = null) = log(Log.ERROR, msg, throwable)

    private fun log(priority: Int, msg: String, throwable: Throwable?) {
        runCatching {
            val text = if (throwable == null) msg else "$msg\n${Log.getStackTraceString(throwable)}"
            XposedBridge.log("[$TAG] $text")
        }
        runCatching {
            if (throwable == null) {
                Log.println(priority, TAG, msg)
            } else {
                Log.println(priority, TAG, "$msg\n${Log.getStackTraceString(throwable)}")
            }
        }
    }
}
