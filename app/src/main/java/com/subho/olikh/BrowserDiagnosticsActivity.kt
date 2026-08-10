package com.subho.olikh

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BrowserDiagnosticsActivity : AppCompatActivity() {
    private val browserPrefs by lazy { getSharedPreferences("olikh_browser", MODE_PRIVATE) }
    private val advancedPrefs by lazy { getSharedPreferences("olikh_advanced", MODE_PRIVATE) }
    private val blocker by lazy { OlikhBlocker(applicationContext) }
    private val permissions by lazy { SitePermissionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(243, 244, 246))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(17, 19, 24))
        }

        header.addView(Button(this).apply {
            text = "‹"
            textSize = 25f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        header.addView(TextView(this).apply {
            text = "Browser Diagnostics"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))

        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(28))
        }

        content.addView(section("RUNTIME"))
        addInfo(content, "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        addInfo(content, "Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        addInfo(content, "WebView provider", runCatching {
            WebView.getCurrentWebViewPackage()?.packageName ?: "Unknown"
        }.getOrDefault("Unknown"))
        addInfo(content, "WebView version", runCatching {
            WebView.getCurrentWebViewPackage()?.versionName ?: "Unknown"
        }.getOrDefault("Unknown"))

        content.addView(section("PROTECTION"))
        addInfo(content, "Protection", if (blocker.isEnabled()) "ON" else "OFF")
        addInfo(content, "Blocked requests", blocker.blockedRequests().toString())
        addInfo(content, "Blocked domains", blocker.blockedHostCount().toString())

        content.addView(section("WEBVIEW SETTINGS"))
        listOf(
            "javascript_enabled" to "JavaScript",
            "cookies_enabled" to "Cookies",
            "third_party_cookies_enabled" to "Third-party cookies",
            "images_enabled" to "Images",
            "dom_storage_enabled" to "DOM storage",
            "database_storage_enabled" to "Database storage",
            "autoplay_enabled" to "Media autoplay",
            "zoom_gestures_enabled" to "Zoom gestures",
            "wide_viewport_enabled" to "Wide viewport",
            "overview_mode_enabled" to "Overview mode",
            "content_access_enabled" to "Content access"
        ).forEach { (key, label) ->
            addInfo(content, label, if (browserPrefs.getBoolean(key, true)) "ON" else "OFF")
        }

        content.addView(section("STORAGE"))
        addInfo(content, "Cookies", runCatching {
            if (CookieManager.getInstance().hasCookies()) "Present" else "Empty"
        }.getOrDefault("Unknown"))

        content.addView(action("Clear WebView site data", "Remove cookies and WebView local storage.", "CLEAR") {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                toast("WebView site data cleared")
                recreate()
            }
        })

        content.addView(action("Clear saved site permissions", "Remove saved camera, microphone and location decisions.", "CLEAR") {
            permissions.clearAll()
            toast("Site permissions cleared")
        })

        content.addView(action("Reset blocker counter", "Reset the current privacy-shield statistics.", "RESET") {
            blocker.resetCounter()
            toast("Blocker counter reset")
            recreate()
        })

        content.addView(section("CONFIGURATION"))
        addInfo(content, "Search engine", browserPrefs.getString("search_engine", "Google") ?: "Google")
        addInfo(content, "Advanced preferences", advancedPrefs.all.size.toString())

        content.addView(action("Copy diagnostics", "Copy a plain-text diagnostic report without page contents or passwords.", "COPY") {
            copyReport()
        })

        content.addView(action("Share diagnostics", "Create a plain-text diagnostic report for troubleshooting.", "SHARE") {
            shareReport()
        })

        content.addView(TextView(this).apply {
            text = "OLIKH • Diagnostics • no passwords or page contents are included"
            textSize = 10f
            setTextColor(Color.rgb(105, 110, 120))
            setPadding(dp(5), dp(18), dp(5), dp(12))
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 10f
        setTextColor(Color.rgb(105, 110, 120))
        setPadding(dp(5), dp(18), dp(5), dp(8))
    }

    private fun addInfo(parent: LinearLayout, title: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.rgb(20, 22, 27))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(Color.rgb(105, 110, 120))
            gravity = android.view.Gravity.END
        })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(2), 0, dp(2))
        })
    }

    private fun action(title: String, summary: String, label: String, click: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(13), dp(9), dp(13))
            setBackgroundColor(Color.WHITE)
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.rgb(20, 22, 27))
        })
        copy.addView(TextView(this).apply {
            text = summary
            textSize = 10f
            setTextColor(Color.rgb(105, 110, 120))
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = label
            textSize = 10f
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-2, dp(46)))
        return row
    }

    private fun buildReport(): String = buildString {
        appendLine("OLIKH Browser Diagnostics")
        appendLine()
        appendLine("Android = ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device = ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("WebView provider = ${runCatching { WebView.getCurrentWebViewPackage()?.packageName ?: "Unknown" }.getOrDefault("Unknown")}")
        appendLine("WebView version = ${runCatching { WebView.getCurrentWebViewPackage()?.versionName ?: "Unknown" }.getOrDefault("Unknown")}")
        appendLine("Protection = ${blocker.isEnabled()}")
        appendLine("Blocked requests = ${blocker.blockedRequests()}")
        appendLine("Blocked domains = ${blocker.blockedHostCount()}")
        appendLine("Search engine = ${browserPrefs.getString("search_engine", "Google")}")
        appendLine()
        appendLine("WebView preferences:")
        browserPrefs.all.filterKeys { it.endsWith("_enabled") }.toSortedMap()
            .forEach { (k, v) -> appendLine("$k = $v") }
    }

    private fun copyReport() {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "OLIKH diagnostics",
                buildReport()
            )
        )
        toast("Diagnostics copied")
    }

    private fun shareReport() {
        startActivity(android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, buildReport())
            },
            "Share OLIKH diagnostics"
        ))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
