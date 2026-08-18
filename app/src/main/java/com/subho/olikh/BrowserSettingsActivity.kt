package com.subho.olikh

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class BrowserSettingsActivity : AppCompatActivity() {
    private val browserPrefs by lazy { getSharedPreferences("olikh_browser", MODE_PRIVATE) }
    private val advancedPrefs by lazy { getSharedPreferences("olikh_advanced", MODE_PRIVATE) }
    private val blocker by lazy { OlikhBlocker(applicationContext) }

    private val white = Color.rgb(245, 247, 250)
    private val muted = Color.rgb(155, 164, 178)
    private val surface = Color.rgb(18, 22, 30)
    private val rowSurface = Color.rgb(27, 32, 42)
    private val accent = Color.rgb(96, 165, 250)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 12, 17))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(surface)
        }

        header.addView(Button(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(white)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        header.addView(TextView(this).apply {
            text = "Settings"
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(white)
        }, LinearLayout.LayoutParams(0, -2, 1f))

        header.addView(TextView(this).apply {
            text = "OLIKH"
            textSize = 10f
            setTextColor(accent)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(header)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(28))
        }

        section(content, "SEARCH & HOME")
        actionRow(content, "Search engine", browserPrefs.getString("search_engine", "Google") ?: "Google") { chooseSearchEngine() }
        actionRow(content, "Homepage", browserPrefs.getString("home_page", "https://www.google.com") ?: "https://www.google.com") { editHomepage() }

        section(content, "PRIVACY & PROTECTION")
        switchRow(content, "OLIKH Shield", "Block known ads and trackers", blocker.isEnabled()) { blocker.setEnabled(it) }
        switchRow(content, "Do Not Track", "Ask websites not to track you", getBool("do_not_track_enabled", false)) { putBool("do_not_track_enabled", it) }
        switchRow(content, "Cookies", "Allow website cookies", getBool("cookies_enabled", true)) { putBool("cookies_enabled", it) }
        switchRow(content, "Third-party cookies", "Allow cookies from embedded sites", getBool("third_party_cookies_enabled", true)) { putBool("third_party_cookies_enabled", it) }

        section(content, "WEB CONTENT")
        switchRow(content, "JavaScript", "Required by many modern websites", getBool("javascript_enabled", true)) { putBool("javascript_enabled", it) }
        switchRow(content, "Images", "Load network images automatically", getBool("images_enabled", true)) { putBool("images_enabled", it) }
        switchRow(content, "DOM Storage", "Enable localStorage and sessionStorage", getBool("dom_storage_enabled", true)) { putBool("dom_storage_enabled", it) }
        switchRow(content, "Database storage", "Enable WebView database storage", getBool("database_storage_enabled", true)) { putBool("database_storage_enabled", it) }
        switchRow(content, "Autoplay", "Allow media to start without a gesture", getBool("autoplay_enabled", false)) { putBool("autoplay_enabled", it) }
        switchRow(content, "Zoom gestures", "Allow pinch and built-in zoom controls", getBool("zoom_gestures_enabled", true)) { putBool("zoom_gestures_enabled", it) }
        switchRow(content, "Wide viewport", "Use responsive viewport metadata", getBool("wide_viewport_enabled", true)) { putBool("wide_viewport_enabled", it) }
        switchRow(content, "Overview mode", "Fit pages to the available screen", getBool("overview_mode_enabled", true)) { putBool("overview_mode_enabled", it) }
        switchRow(content, "Desktop viewport", "Prefer desktop-style viewport sizing", getBool("desktop_viewport_enabled", false)) { putBool("desktop_viewport_enabled", it) }

        section(content, "ADVANCED")
        switchRow(content, "Cache", "Keep normal WebView cache behavior", getBool("cache_enabled", true)) { putBool("cache_enabled", it) }
        switchRow(content, "Content access", "Allow WebView content:// access", getBool("content_access_enabled", true)) { putBool("content_access_enabled", it) }
        switchRow(content, "File access", "Allow WebView file:// access", getBool("file_access_enabled", false)) { putBool("file_access_enabled", it) }
        switchRow(content, "JavaScript popups", "Allow JavaScript to open windows", getBool("js_popups_enabled", false)) { putBool("js_popups_enabled", it) }
        switchRow(content, "Multiple windows", "Allow pages to request extra WebViews", getBool("multiple_windows_enabled", false)) { putBool("multiple_windows_enabled", it) }
        switchRow(content, "Restore session", "Restore tabs after app restart", advancedPrefs.getBoolean("restore_session", true)) { advancedPrefs.edit().putBoolean("restore_session", it).apply() }
        switchRow(content, "Clear on exit", "Clear cookies, storage and cache when leaving", advancedPrefs.getBoolean("clear_on_exit", false)) {
            advancedPrefs.edit().putBoolean("clear_on_exit", it).apply()
            getSharedPreferences("olikh_pref", MODE_PRIVATE).edit().putBoolean("auto_clear_exit", it).apply()
        }

        section(content, "READING & DISPLAY")
        switchRow(content, "Reader mode", "Prefer distraction-free reading", getBool("reader_mode_enabled", false)) { putBool("reader_mode_enabled", it) }
        actionRow(content, "Default font size", "${browserPrefs.getInt("default_font_size", 16)}sp") { chooseFontSize() }
        actionRow(content, "Text encoding", browserPrefs.getString("text_encoding", "UTF-8") ?: "UTF-8") { chooseEncoding() }

        section(content, "MAINTENANCE")
        actionRow(content, "Reset browser settings", "Restore OLIKH defaults") { confirmReset() }
        TextView(this).apply {
            text = "Changes are saved immediately. WebView engine changes are applied when you return to the browser."
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(6), dp(14), dp(6), 0)
        }.also { content.addView(it) }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 11f
            setTextColor(muted)
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.12f
            setPadding(dp(4), dp(16), dp(4), dp(8))
        })
    }

    private fun switchRow(parent: LinearLayout, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            setBackgroundColor(rowSurface)
        }
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(white) })
        textBox.addView(TextView(this).apply { text = subtitle; textSize = 10.5f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })
        row.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
    }

    private fun actionRow(parent: LinearLayout, title: String, value: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setBackgroundColor(rowSurface)
            isClickable = true
            setOnClickListener { action() }
        }
        row.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(white) })
        row.addView(TextView(this).apply { text = value; textSize = 10.5f; setTextColor(accent); setPadding(0, dp(3), 0, 0) })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
    }

    private fun chooseSearchEngine() {
        val engines = arrayOf("Google", "DuckDuckGo", "Bing", "Brave")
        val current = browserPrefs.getString("search_engine", "Google") ?: "Google"
        AlertDialog.Builder(this).setTitle("Search engine").setSingleChoiceItems(engines, engines.indexOf(current).coerceAtLeast(0)) { dialog, which ->
            browserPrefs.edit().putString("search_engine", engines[which]).apply()
            dialog.dismiss()
            recreate()
        }.show()
    }

    private fun editHomepage() {
        val input = EditText(this).apply {
            hint = "https://www.google.com"
            setSingleLine(true)
            setText(browserPrefs.getString("home_page", "https://www.google.com"))
            selectAll()
        }
        AlertDialog.Builder(this).setTitle("Homepage").setView(input).setPositiveButton("Save") { _, _ ->
            var value = input.text.toString().trim()
            if (value.isNotBlank() && !value.startsWith("http://", true) && !value.startsWith("https://", true)) value = "https://$value"
            if (value.startsWith("http://", true) || value.startsWith("https://", true)) browserPrefs.edit().putString("home_page", value).apply()
            recreate()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseFontSize() {
        val values = (12..24).map { "$it sp" }.toTypedArray()
        val current = browserPrefs.getInt("default_font_size", 16).coerceIn(12, 24)
        AlertDialog.Builder(this).setTitle("Default font size").setSingleChoiceItems(values, current - 12) { dialog, which ->
            browserPrefs.edit().putInt("default_font_size", 12 + which).apply()
            dialog.dismiss()
            recreate()
        }.show()
    }

    private fun chooseEncoding() {
        val values = arrayOf("UTF-8", "ISO-8859-1", "UTF-16")
        val current = browserPrefs.getString("text_encoding", "UTF-8") ?: "UTF-8"
        AlertDialog.Builder(this).setTitle("Text encoding").setSingleChoiceItems(values, values.indexOf(current).coerceAtLeast(0)) { dialog, which ->
            browserPrefs.edit().putString("text_encoding", values[which]).apply()
            dialog.dismiss()
            recreate()
        }.show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this).setTitle("Reset browser settings?").setMessage("This restores browser defaults but does not delete bookmarks, history or downloaded files.")
            .setNegativeButton("Cancel", null).setPositiveButton("Reset") { _, _ ->
                browserPrefs.edit().clear().apply()
                advancedPrefs.edit().putBoolean("restore_session", true).putBoolean("clear_on_exit", false).apply()
                getSharedPreferences("olikh_pref", MODE_PRIVATE).edit().putBoolean("auto_clear_exit", false).apply()
                blocker.setEnabled(true)
                Toast.makeText(this, "Browser settings restored", Toast.LENGTH_SHORT).show()
                recreate()
            }.show()
    }

    private fun getBool(key: String, default: Boolean) = browserPrefs.getBoolean(key, default)
    private fun putBool(key: String, value: Boolean) { browserPrefs.edit().putBoolean(key, value).apply() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
