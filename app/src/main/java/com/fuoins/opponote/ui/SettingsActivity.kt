package com.fuoins.opponote.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.fuoins.opponote.R
import com.fuoins.opponote.core.Prefs

/**
 * 模块设置主页，从便签「设置 → 模块设置」进入。
 * 模块自身没有桌面图标。
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)

        val entryContainer = findViewById<LinearLayout>(R.id.entry_container)
        addEntry(entryContainer, getString(R.string.batch_entry_title), getString(R.string.batch_entry_summary)) {
            startActivity(Intent(this, BatchFormatActivity::class.java))
        }
        addDivider(entryContainer)
        addEntry(entryContainer, getString(R.string.todo_entry_title), getString(R.string.todo_entry_summary)) {
            Toast.makeText(this, "待办增强正在适配，开关见下方", Toast.LENGTH_SHORT).show()
        }
        addDivider(entryContainer)
        addEntry(entryContainer, getString(R.string.home_entry_title), getString(R.string.home_entry_summary)) {
            Toast.makeText(this, "首页样式正在适配，开关见下方", Toast.LENGTH_SHORT).show()
        }
        addDivider(entryContainer)
        addEntry(entryContainer, getString(R.string.log_entry_title), getString(R.string.log_entry_summary)) {
            startActivity(Intent(this, LogActivity::class.java))
        }

        val switchContainer = findViewById<LinearLayout>(R.id.switch_container)
        addSwitch(
            switchContainer,
            "启用待办增强",
            "每日刷新指定待办、一行多列显示",
            "feature_todo_enhance",
            false,
        )
        addDivider(switchContainer)
        addSwitch(
            switchContainer,
            "启用首页样式增强",
            "加大标题、突出卡片",
            "feature_home_style",
            false,
        )
        addDivider(switchContainer)
        addSwitch(
            switchContainer,
            getString(R.string.diag_entry_title),
            getString(R.string.diag_entry_summary),
            "feature_diagnostics",
            false,
        )
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
