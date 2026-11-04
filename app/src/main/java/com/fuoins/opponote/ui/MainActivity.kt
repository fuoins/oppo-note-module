package com.fuoins.opponote.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.fuoins.opponote.BuildConfig
import com.fuoins.opponote.ModuleEntry
import com.fuoins.opponote.R
import com.fuoins.opponote.core.FeatureRegistry
import com.fuoins.opponote.core.Prefs

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindStatus()
        bindFeatures()
        findViewById<View>(R.id.btn_open_note).setOnClickListener { openNote() }
    }

    private fun bindStatus() {
        @Suppress("DEPRECATION")
        val noteVersion = runCatching {
            packageManager.getPackageInfo(ModuleEntry.TARGET_PACKAGE, 0).versionName
        }.getOrNull()

        bindRow(R.id.row_target, getString(R.string.status_target), ModuleEntry.TARGET_PACKAGE)
        bindRow(
            R.id.row_note_version,
            getString(R.string.status_version),
            noteVersion ?: getString(R.string.status_not_installed),
        )
        bindRow(
            R.id.row_module_version,
            getString(R.string.status_module),
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    private fun bindRow(rowId: Int, key: String, value: String) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.info_key).text = key
        row.findViewById<TextView>(R.id.info_value).text = value
    }

    private fun bindFeatures() {
        val container = findViewById<LinearLayout>(R.id.feature_container)
        val prefs = Prefs(getSharedPreferences(Prefs.GROUP, MODE_PRIVATE))

        FeatureRegistry.all.forEachIndexed { index, feature ->
            val row = layoutInflater.inflate(R.layout.item_feature, container, false)
            row.findViewById<TextView>(R.id.feature_title).text = feature.title
            row.findViewById<TextView>(R.id.feature_summary).text = feature.summary

            val toggle = row.findViewById<Switch>(R.id.feature_switch)
            toggle.isChecked = prefs.isEnabled(feature.id, feature.enabledByDefault)
            toggle.setOnCheckedChangeListener { _, checked ->
                prefs.setEnabled(feature.id, checked)
            }

            container.addView(row)
            if (index != FeatureRegistry.all.lastIndex) {
                container.addView(divider())
            }
        }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.displayMetrics.density.toInt().coerceAtLeast(1),
        ).apply {
            marginStart = dp(18)
            marginEnd = dp(18)
        }
    }

    private fun openNote() {
        val intent: Intent? = runCatching {
            packageManager.getLaunchIntentForPackage(ModuleEntry.TARGET_PACKAGE)
        }.getOrNull()

        if (intent == null) {
            Toast.makeText(this, R.string.toast_note_missing, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(intent)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
