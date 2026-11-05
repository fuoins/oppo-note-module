package com.fuoins.opponote.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.fuoins.opponote.R
import com.fuoins.opponote.core.BatchEngine
import com.fuoins.opponote.core.DateConfig
import com.fuoins.opponote.core.ExpiredCleaner
import com.fuoins.opponote.core.FieldConfig
import com.fuoins.opponote.core.FieldType
import com.fuoins.opponote.core.IntervalMode
import com.fuoins.opponote.core.LayoutConfig
import com.fuoins.opponote.core.LayoutMode
import com.fuoins.opponote.core.LogStore
import com.fuoins.opponote.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 批量与排版：按字段序列批量生成日历清单，并支持列对齐排版。
 * 生成结果通过系统分享直接发送给便签，不修改便签数据模型。
 */
class BatchFormatActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private val fields = mutableListOf<FieldConfig>()

    private lateinit var fieldContainer: LinearLayout
    private lateinit var tvFieldEmpty: TextView
    private lateinit var etPattern: EditText
    private lateinit var etIntervalDays: EditText
    private lateinit var spWeekday: Spinner
    private lateinit var etCount: EditText
    private lateinit var etSpace: EditText
    private lateinit var etBlank: EditText
    private lateinit var swAlign: Switch
    private lateinit var etPreview: EditText
    private lateinit var rgInterval: RadioGroup
    private lateinit var rgLayout: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        step("onCreate start")

        // 分步初始化并打点：任何一步异常都记录到模块日志，并给出明确提示
        runCatching {
            setContentView(R.layout.activity_batch_format)
            step("setContentView ok")
            prefs = getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)
            bindViews()
            step("bindViews ok")
            setupWeekdaySpinner()
            step("spinner ok")
            setupPresets()
            step("presets ok")
            loadConfig()
            step("loadConfig ok")
            renderFields()
            step("renderFields ok")
            setupListeners()
            step("listeners ok")
            refreshPreview()
            step("preview ok, UI ready")
        }.onFailure { t ->
            step("CRASH ${t.javaClass.name}: ${t.message}")
            t.stackTrace.take(14).forEach { step("    at $it") }
            Toast.makeText(
                this,
                "批量排版界面启动失败：${t.message}\n详细信息已写入模块日志",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    /** 分步打点：写入模块日志（「运存日志」页可见），不依赖 prefs 等任何初始化状态 */
    private fun step(msg: String) {
        runCatching {
            val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date())
            LogStore.append(this, "[batch] $ts $msg\n")
        }
    }

    /** 模块进程崩溃捕获：主线程未捕获异常写文件 + 模块日志，再交给系统默认处理 */
    private fun installCrashGuard() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                    .format(Date())
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(filesDir, "opponote_crash.log")
                    .appendText("===== $ts thread=${t.name} =====\n$sw\n")
                LogStore.append(this, "[batch-crash] $ts thread=${t.name}\n$sw\n")
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun bindViews() {
        fieldContainer = findViewById(R.id.field_container)
        tvFieldEmpty = findViewById(R.id.tv_field_empty)
        etPattern = findViewById(R.id.et_pattern)
        etIntervalDays = findViewById(R.id.et_interval_days)
        spWeekday = findViewById(R.id.sp_weekday)
        etCount = findViewById(R.id.et_count)
        etSpace = findViewById(R.id.et_space)
        etBlank = findViewById(R.id.et_blank)
        swAlign = findViewById(R.id.sw_align)
        etPreview = findViewById(R.id.et_preview)
        rgInterval = findViewById(R.id.rg_interval)
        rgLayout = findViewById(R.id.rg_layout)
    }

    private fun setupWeekdaySpinner() {
        val labels = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        spWeekday.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun setupPresets() {
        val container = findViewById<LinearLayout>(R.id.preset_container)
        BatchEngine.PATTERN_PRESETS.forEach { (pattern, sample) ->
            val chip = TextView(this).apply {
                text = sample
                textSize = 12f
                setTextColor(getColor(R.color.brand_dark))
                setBackgroundResource(R.drawable.bg_mini_button)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.marginEnd = dp(6)
                layoutParams = lp
                setOnClickListener {
                    etPattern.setText(pattern)
                    etPattern.setSelection(pattern.length)
                }
            }
            container.addView(chip)
        }
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btn_add_date).setOnClickListener {
            fields.add(FieldConfig(FieldType.DATE))
            renderFields()
            persist()
            refreshPreview()
        }

        findViewById<Button>(R.id.btn_add_text).setOnClickListener {
            showTextFieldDialog { value ->
                fields.add(FieldConfig(FieldType.TEXT, value))
                renderFields()
                persist()
                refreshPreview()
            }
        }

        etPattern.addTextChangedListener(simpleWatcher { persist(); refreshPreview() })
        etIntervalDays.addTextChangedListener(simpleWatcher { persist(); refreshPreview() })
        etCount.addTextChangedListener(simpleWatcher { persist(); refreshPreview() })
        etSpace.addTextChangedListener(simpleWatcher { persist(); refreshPreview() })
        etBlank.addTextChangedListener(simpleWatcher { persist(); refreshPreview() })

        rgInterval.setOnCheckedChangeListener { _, checkedId ->
            etIntervalDays.visibility = if (checkedId == R.id.rb_every) View.VISIBLE else View.GONE
            spWeekday.visibility = if (checkedId == R.id.rb_weekly) View.VISIBLE else View.GONE
            persist()
            refreshPreview()
        }

        rgLayout.setOnCheckedChangeListener { _, checkedId ->
            etSpace.visibility = if (checkedId == R.id.rb_space) View.VISIBLE else View.GONE
            etBlank.visibility = if (checkedId == R.id.rb_newline) View.VISIBLE else View.GONE
            persist()
            refreshPreview()
        }

        spWeekday.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                persist()
                refreshPreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        swAlign.setOnCheckedChangeListener { _, _ -> persist(); refreshPreview() }

        findViewById<Button>(R.id.btn_generate).setOnClickListener { sendToNote() }
        findViewById<Button>(R.id.btn_copy).setOnClickListener { copyToClipboard(currentText()) }
        findViewById<Button>(R.id.btn_clean).setOnClickListener { cleanExpired() }
    }

    private fun simpleWatcher(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = action()
    }

    private fun renderFields() {
        fieldContainer.removeAllViews()
        tvFieldEmpty.visibility = if (fields.isEmpty()) View.VISIBLE else View.GONE

        fields.forEachIndexed { index, field ->
            val row = layoutInflater.inflate(R.layout.item_field, fieldContainer, false)
            val typeView = row.findViewById<TextView>(R.id.field_type)
            val contentView = row.findViewById<TextView>(R.id.field_content)

            if (field.type == FieldType.DATE) {
                typeView.text = getString(R.string.field_type_date)
                contentView.text = runCatching {
                    java.text.SimpleDateFormat(piPattern(), java.util.Locale.CHINA)
                        .format(System.currentTimeMillis())
                }.getOrDefault("—")
            } else {
                typeView.text = getString(R.string.field_type_text)
                contentView.text = field.text.ifBlank { "（空）" }
            }

            row.setOnClickListener {
                if (field.type == FieldType.TEXT) {
                    showTextFieldDialog(field.text) { value ->
                        fields[index] = field.copy(text = value)
                        renderFields()
                        persist()
                        refreshPreview()
                    }
                } else {
                    Toast.makeText(this, "日历字段请在下方「日历设置」中调整", Toast.LENGTH_SHORT).show()
                }
            }

            row.findViewById<TextView>(R.id.field_delete).setOnClickListener {
                fields.removeAt(index)
                renderFields()
                persist()
                refreshPreview()
            }

            fieldContainer.addView(row)
        }
    }

    private fun showTextFieldDialog(initial: String = "", onConfirm: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.text_field_hint)
            setText(initial)
            setSelection(text.length)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.text_field_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm(input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 配置 ----------

    private fun piPattern(): String = etPattern.text.toString().ifBlank { "MM.dd" }

    private fun dateConfig(): DateConfig = DateConfig(
        pattern = piPattern(),
        intervalMode = when (rgInterval.checkedRadioButtonId) {
            R.id.rb_every -> IntervalMode.EVERY_N_DAYS
            R.id.rb_weekly -> IntervalMode.WEEKLY
            else -> IntervalMode.DAILY
        },
        intervalDays = etIntervalDays.text.toString().toIntOrNull() ?: 2,
        weekday = spWeekday.selectedItemPosition + 1,
        count = etCount.text.toString().toIntOrNull() ?: 10,
    )

    private fun layoutConfig(): LayoutConfig = LayoutConfig(
        mode = if (rgLayout.checkedRadioButtonId == R.id.rb_newline) LayoutMode.NEWLINE else LayoutMode.SPACE,
        spaceCount = etSpace.text.toString().toIntOrNull() ?: 2,
        blankLines = etBlank.text.toString().toIntOrNull() ?: 0,
        alignColumns = swAlign.isChecked,
    )

    private fun loadConfig() {
        etPattern.setText(prefs.getString(KEY_PATTERN, "MM.dd"))
        etPattern.setSelection(etPattern.text.length)
        etIntervalDays.setText(prefs.getInt(KEY_INTERVAL_DAYS, 2).toString())
        etCount.setText(prefs.getInt(KEY_COUNT, 10).toString())
        etSpace.setText(prefs.getInt(KEY_SPACE, 2).toString())
        etBlank.setText(prefs.getInt(KEY_BLANK, 0).toString())
        swAlign.isChecked = prefs.getBoolean(KEY_ALIGN, true)

        when (prefs.getInt(KEY_INTERVAL_MODE, 0)) {
            1 -> rgInterval.check(R.id.rb_every)
            2 -> rgInterval.check(R.id.rb_weekly)
            else -> rgInterval.check(R.id.rb_daily)
        }
        spWeekday.setSelection((prefs.getInt(KEY_WEEKDAY, Calendar.SATURDAY) - 1).coerceIn(0, 6))
        etIntervalDays.visibility = if (rgInterval.checkedRadioButtonId == R.id.rb_every) View.VISIBLE else View.GONE
        spWeekday.visibility = if (rgInterval.checkedRadioButtonId == R.id.rb_weekly) View.VISIBLE else View.GONE

        if (prefs.getInt(KEY_LAYOUT_MODE, 0) == 1) rgLayout.check(R.id.rb_newline) else rgLayout.check(R.id.rb_space)
        etSpace.visibility = if (rgLayout.checkedRadioButtonId == R.id.rb_space) View.VISIBLE else View.GONE
        etBlank.visibility = if (rgLayout.checkedRadioButtonId == R.id.rb_newline) View.VISIBLE else View.GONE

        fields.clear()
        val raw = prefs.getString(KEY_FIELDS, null)
        if (raw.isNullOrBlank()) {
            fields.add(FieldConfig(FieldType.DATE))
            fields.add(FieldConfig(FieldType.TEXT, "完成"))
            fields.add(FieldConfig(FieldType.TEXT, "忘记"))
        } else {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val type = if (obj.optString("type") == "date") FieldType.DATE else FieldType.TEXT
                    fields.add(FieldConfig(type, obj.optString("text")))
                }
            }
        }
    }

    private fun persist() {
        val array = JSONArray()
        fields.forEach { field ->
            array.put(
                JSONObject().apply {
                    put("type", if (field.type == FieldType.DATE) "date" else "text")
                    put("text", field.text)
                }
            )
        }
        prefs.edit()
            .putString(KEY_PATTERN, piPattern())
            .putInt(
                KEY_INTERVAL_MODE,
                when (rgInterval.checkedRadioButtonId) {
                    R.id.rb_every -> 1
                    R.id.rb_weekly -> 2
                    else -> 0
                },
            )
            .putInt(KEY_INTERVAL_DAYS, etIntervalDays.text.toString().toIntOrNull() ?: 2)
            .putInt(KEY_WEEKDAY, spWeekday.selectedItemPosition + 1)
            .putInt(KEY_COUNT, etCount.text.toString().toIntOrNull() ?: 10)
            .putInt(KEY_LAYOUT_MODE, if (rgLayout.checkedRadioButtonId == R.id.rb_newline) 1 else 0)
            .putInt(KEY_SPACE, etSpace.text.toString().toIntOrNull() ?: 2)
            .putInt(KEY_BLANK, etBlank.text.toString().toIntOrNull() ?: 0)
            .putBoolean(KEY_ALIGN, swAlign.isChecked)
            .putString(KEY_FIELDS, array.toString())
            .apply()
    }

    // ---------- 预览与输出 ----------

    private fun refreshPreview() {
        val text = runCatching {
            BatchEngine.generate(fields, dateConfig(), layoutConfig())
        }.getOrElse { "" }
        etPreview.setText(text)
    }

    private fun currentText(): String = etPreview.text.toString()

    private fun sendToNote() {
        val text = currentText()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.toast_no_field, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.coloros.note")
        }
        val launched = runCatching { startActivity(intent); true }.getOrDefault(false)
        if (!launched) {
            copyToClipboard(text)
            Toast.makeText(this, R.string.toast_no_note, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("batch_note", text))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    private fun cleanExpired() {
        val text = currentText()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.toast_nothing_cleaned, Toast.LENGTH_SHORT).show()
            return
        }
        val result = ExpiredCleaner.clean(text, piPattern())
        if (result.removedLines == 0) {
            Toast.makeText(this, R.string.toast_nothing_cleaned, Toast.LENGTH_SHORT).show()
        } else {
            etPreview.setText(result.text)
            Toast.makeText(
                this,
                getString(R.string.toast_cleaned, result.removedLines),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_PATTERN = "batch_pattern"
        private const val KEY_INTERVAL_MODE = "batch_interval_mode"
        private const val KEY_INTERVAL_DAYS = "batch_interval_days"
        private const val KEY_WEEKDAY = "batch_weekday"
        private const val KEY_COUNT = "batch_count"
        private const val KEY_LAYOUT_MODE = "batch_layout_mode"
        private const val KEY_SPACE = "batch_space"
        private const val KEY_BLANK = "batch_blank"
        private const val KEY_ALIGN = "batch_align"
        private const val KEY_FIELDS = "batch_fields"
    }
}
