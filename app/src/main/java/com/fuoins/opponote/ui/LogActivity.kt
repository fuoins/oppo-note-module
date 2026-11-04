package com.fuoins.opponote.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.fuoins.opponote.R
import com.fuoins.opponote.core.LogStore
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 运行日志查看页。
 *
 * 日志由便签进程通过 [com.fuoins.opponote.core.ConfigProvider] 送进来落盘，
 * 这里只负责读取、复制、分享与清空。数据不出本机。
 */
class LogActivity : Activity() {

    private lateinit var tvLog: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var scrollLog: ScrollView

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var lastText: String = ""
    private var firstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tv_log)
        tvMeta = findViewById(R.id.tv_log_meta)
        tvEmpty = findViewById(R.id.tv_empty)
        scrollLog = findViewById(R.id.scroll_log)

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { load(autoScroll = true) }
        findViewById<Button>(R.id.btn_copy).setOnClickListener { copyLog() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { confirmClear() }

        load(autoScroll = true)
    }

    override fun onResume() {
        super.onResume()
        // 从便签切回来时自动刷新，省去手动点
        if (!firstLoad) load(autoScroll = true)
        firstLoad = false
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun load(autoScroll: Boolean) {
        tvMeta.text = getString(R.string.log_loading)
        io.execute {
            val text = LogStore.read(this)
            val size = LogStore.size(this)
            main.post { render(text, size, autoScroll) }
        }
    }

    private fun render(text: String, size: Long, autoScroll: Boolean) {
        lastText = text
        val lines = if (text.isBlank()) 0 else text.count { it == '\n' } + 1

        tvMeta.text = if (lines == 0) {
            getString(R.string.log_meta_empty)
        } else {
            getString(R.string.log_meta, lines, formatSize(size))
        }

        tvLog.text = text
        tvEmpty.visibility = if (text.isBlank()) View.VISIBLE else View.GONE
        scrollLog.visibility = if (text.isBlank()) View.GONE else View.VISIBLE

        if (autoScroll && text.isNotBlank()) {
            scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun copyLog() {
        if (lastText.isBlank()) {
            toast(R.string.log_empty)
            return
        }
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("OPPONote log", lastText))
        toast(R.string.log_copied)
    }

    private fun shareLog() {
        if (lastText.isBlank()) {
            toast(R.string.log_empty)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_title))
            putExtra(Intent.EXTRA_TEXT, lastText)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.log_share))) }
            .onFailure { toast(R.string.log_no_app) }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.log_clear)
            .setMessage(R.string.log_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                io.execute {
                    LogStore.clear(this)
                    main.post { load(autoScroll = false) }
                }
            }
            .show()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}
