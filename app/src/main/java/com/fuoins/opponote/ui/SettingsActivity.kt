package com.fuoins.opponote.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.fuoins.opponote.R
import com.fuoins.opponote.core.FeatureRegistry
import com.fuoins.opponote.core.LogStore
import com.fuoins.opponote.core.Prefs
import java.util.concurrent.Executors

/**
 * 模块设置主页。
 * 正常路径从便签「设置 → 模块设置」进入；排障期间保留桌面图标，
 * 直接打开可查看宿主连接状态与运行日志。
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)

        refreshHostStatus()

        // 三个功能开关：批量与排版 / 待办增强 / 首页样式，默认全部开启
        val switchContainer = findViewById<LinearLayout>(R.id.switch_container)
        addSwitch(
            switchContainer,
            getString(R.string.batch_entry_title),
            getString(R.string.batch_entry_summary),
            FeatureRegistry.key("add_menu"),
            true,
        )
        addDivider(switchContainer)
        addSwitch(
            switchContainer,
            getString(R.string.todo_entry_title),
            getString(R.string.todo_entry_summary),
            FeatureRegistry.key("todo_enhance"),
            true,
        )
        addDivider(switchContainer)
        addSwitch(
            switchContainer,
            getString(R.string.home_entry_title),
            getString(R.string.home_entry_summary),
            FeatureRegistry.key("home_style"),
            true,
        )

        // 运行日志入口
        val entryContainer = findViewById<LinearLayout>(R.id.entry_container)
        addEntry(entryContainer, getString(R.string.log_entry_title), getString(R.string.log_entry_summary)) {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHostStatus()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    /** 依据宿主写回的日志判断注入是否发生过，显示在副标题位置 */
    private fun refreshHostStatus() {
        val statusView = findViewById<TextView>(R.id.tv_status) ?: return
        io.execute {
            val text = LogStore.read(this)
            val connected = text.contains("loadPackage")
            val installed = text.contains("installed [")
            val entryAdded = text.contains("settings entry added")
            main.post {
                statusView.text = when {
                    connected && entryAdded ->
                        getString(R.string.status_ok_entry)
                    connected && installed ->
                        getString(R.string.status_ok_no_entry)
                    connected ->
                        getString(R.string.status_connected_err)
                    else ->
                        getString(R.string.status_not_connected)
                }
            }
        }
    }

    private fun addEntry(container: LinearLayout, title: String, summary: String, onClick: () -> Unit) {
        val row = layoutInflater.inflate(R.layout.item_entry, container, false)
        row.findViewById<TextView>(R.id.entry_title).text = title
        row.findViewById<TextView>(R.id.entry_summary).text = summary
        row.setOnClickListener { onClick() }
        container.addView(row)
    }

    private fun addSwitch(
        container: LinearLayout,
        title: String,
        summary: String,
        key: String,
        default: Boolean,
    ) {
        val row = layoutInflater.inflate(R.layout.item_switch, container, false)
        row.findViewById<TextView>(R.id.switch_title).text = title
        row.findViewById<TextView>(R.id.switch_summary).text = summary

        val toggle = row.findViewById<Switch>(R.id.switch_toggle)
        toggle.isChecked = prefs.getBoolean(key, default)
        toggle.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
        container.addView(row)
    }

    private fun addDivider(container: LinearLayout) {
        val divider = View(this).apply {
            setBackgroundColor(getColor(R.color.divider))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.density.toInt().coerceAtLeast(1),
            ).apply {
                marginStart = dp(18)
                marginEnd = dp(18)
            }
        }
        container.addView(divider)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
