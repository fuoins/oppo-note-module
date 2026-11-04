package com.fuoins.opponote.util

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 宿主进程（便签）侧的日志出口。
 *
 * 便签进程无法直接写模块 App 的私有目录，因此通过模块暴露的 [ConfigProvider]
 * 的 "log" 调用把日志文本递交给模块进程，由模块进程落盘，设置里的「运行日志」再读出来。
 *
 * 所有写入均为异步 + 批量：默认攒 250ms 合并一次，避免频繁 IPC 拖慢便签。
 */
object LogSink {

    private const val AUTHORITY = "com.fuoins.opponote.config"
    private const val MAX_PENDING = 400
    private const val MAX_LINE = 3600
    private const val FLUSH_DELAY_MS = 250L
    private const val MAX_BATCH_CHARS = 48 * 1024
    private const val MAX_FAILURES = 6

    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private val format = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var worker: Handler? = null

    private var flushScheduled = false
    private var failures = 0

    /** 由 ModuleEntry 在 Application.onCreate 后调用；未 attach 前产生的日志也会留在缓冲里。 */
    fun attach(context: Context) {
        if (appContext == null) {
            appContext = runCatching { context.applicationContext }.getOrNull()
        }
        ensureWorker()
        scheduleFlush(FLUSH_DELAY_MS)
    }

    /** 记录一行日志，永不抛出异常。 */
    fun record(level: String, message: String) {
        runCatching {
            val line = buildString {
                append(format.get()?.format(Date()) ?: "--:--:--")
                append(' ')
                append(level)
                append(' ')
                append(if (message.length > MAX_LINE) message.take(MAX_LINE) + " …(截断)" else message)
            }
            synchronized(lock) {
                if (pending.size >= MAX_PENDING) pending.pollFirst()
                pending.addLast(line)
            }
            ensureWorker()
            scheduleFlush(FLUSH_DELAY_MS)
        }
    }

    /** 立即刷出（进程退出、关键节点使用）。 */
    fun flushNow() {
        runCatching {
            ensureWorker()
            worker?.removeCallbacksAndMessages(null)
            synchronized(lock) { flushScheduled = false }
            worker?.post { drain() }
        }
    }

    private fun ensureWorker() {
        if (worker != null) return
        synchronized(lock) {
            if (worker != null) return
            val thread = runCatching { HandlerThread("OPPONote-Log").also { it.start() } }.getOrNull() ?: return
            worker = Handler(thread.looper)
        }
    }

    private fun scheduleFlush(delay: Long) {
        val handler = worker ?: return
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        handler.postDelayed({
            synchronized(lock) { flushScheduled = false }
            drain()
        }, delay)
    }

    /** 在 worker 线程执行：把缓冲切成不超过 IPC 上限的批次依次发送。 */
    private fun drain() {
        val ctx = appContext
        while (true) {
            val batch = buildString {
                synchronized(lock) {
                    while (pending.isNotEmpty()) {
                        val line = pending.pollFirst() ?: break
                        if (length + line.length + 1 > MAX_BATCH_CHARS) {
                            pending.addFirst(line)
                            break
                        }
                        append(line)
                        append('\n')
                    }
                }
            }
            if (batch.isEmpty()) return

            if (ctx == null) {
                requeue(batch)
                return
            }
            val ok = runCatching {
                val extras = Bundle().apply { putString("text", batch) }
                ctx.contentResolver.call(Uri.parse("content://$AUTHORITY"), "log", null, extras)
                true
            }.getOrDefault(false)

            if (!ok) {
                failures++
                requeue(batch)
                if (failures >= MAX_FAILURES) {
                    // 渠道不可用时放弃，避免无限重试持续唤醒 CPU
                    synchronized(lock) { pending.clear() }
                }
                return
            }
            failures = 0
        }
    }

    private fun requeue(batch: String) {
        val lines = batch.split('\n').filter { it.isNotEmpty() }
        synchronized(lock) {
            for (i in lines.indices.reversed()) {
                if (pending.size >= MAX_PENDING) break
                pending.addFirst(lines[i])
            }
        }
    }
}
