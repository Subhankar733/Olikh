package com.subho.olikh

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdvancedBrowserHubActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("olikh_advanced", MODE_PRIVATE)
    }

    private val browserPrefs by lazy {
        getSharedPreferences("olikh_browser", MODE_PRIVATE)
    }

    private val blocker by lazy {
        OlikhBlocker(applicationContext)
    }

    private val permissions by lazy {
        SitePermissionManager(this)
    }

    private val white = Color.WHITE
    private val ink = Color.rgb(20, 22, 27)
    private val muted = Color.rgb(105, 110, 120)
    private val surface = Color.WHITE
    private val dark = Color.rgb(17, 19, 24)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(243, 244, 246))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            setBackgroundColor(dark)
        }

        val back = Button(this).apply {
            text = "‹"
            textSize = 25f
            setTextColor(white)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "OLIKH Power Center"
            textSize = 21f
            setTextColor(white)
        })
        titleBox.addView(TextView(this).apply {
            text = "Advanced browser controls"
            textSize = 11f
            setTextColor(Color.rgb(174, 179, 190))
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        header.addView(TextView(this).apply {
            text = "LIVE"
            textSize = 10f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Color.rgb(37, 40, 48))
        })

        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(28))
        }

        content.addView(section("BROWSER ENGINE"))

        content.addView(switchCard(
            "Desktop site mode",
            "Prefer desktop layouts when pages support them.",
            "desktop_viewport_enabled",
            false
        ))

        content.addView(switchCard(
            "Open external links in new tabs",
            "Keep the current page while opening a new destination.",
            "open_links_new_tab",
            false
        ))

        content.addView(switchCard(
            "Block pop-up windows",
            "Keep unsolicited WebView pop-ups disabled.",
            "block_popups",
            true
        ))

        content.addView(switchCard(
            "Do Not Track",
            "Request reduced tracking where websites respect the signal.",
            "do_not_track",
            true
        ))

        content.addView(switchCard(
            "Strict HTTPS preference",
            "Upgrade HTTP navigations to HTTPS when this switch is enabled.",
            "strict_https",
            false
        ))

        content.addView(section("WEBVIEW ENGINE"))

        content.addView(browserSwitchCard(
            "JavaScript",
            "Allow websites to run JavaScript. Turn off for maximum restriction.",
            "javascript_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Cookies",
            "Allow websites to store login and session cookies.",
            "cookies_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Third-party cookies",
            "Allow cross-site cookies used by embedded services.",
            "third_party_cookies_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Images",
            "Load page images. Disable to reduce bandwidth and page weight.",
            "images_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "DOM storage",
            "Enable localStorage/sessionStorage used by modern web apps.",
            "dom_storage_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Database storage",
            "Allow web databases used by some offline-capable sites.",
            "database_storage_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Media autoplay",
            "Allow pages to start media without an explicit tap.",
            "autoplay_enabled",
            false
        ))

        content.addView(browserSwitchCard(
            "Zoom gestures",
            "Allow pinch-to-zoom and browser zoom gestures.",
            "zoom_gestures_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Wide viewport",
            "Let responsive pages use a desktop-like viewport width when supported.",
            "wide_viewport_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Overview mode",
            "Allow WebView overview/layout behavior for pages that request it.",
            "overview_mode_enabled",
            true
        ))

        content.addView(browserSwitchCard(
            "Content access",
            "Allow WebView access to content:// resources where supported.",
            "content_access_enabled",
            true
        ))

        content.addView(section("PRIVACY & SECURITY"))

        content.addView(switchCard(
            "Strict private mode",
            "Do not persist private-session data through OLIKH settings.",
            "strict_private",
            true
        ))

        content.addView(switchCard(
            "Auto-clear site data on exit",
            "Clear cookies and WebView storage when the browser exits.",
            "clear_on_exit",
            false
        ))

        content.addView(switchCard(
            "Ad & content protection",
            "Use OLIKH's existing host blocker.",
            "blocker_ui",
            blocker.isEnabled()
        ) { enabled ->
            blocker.setEnabled(enabled)
            toast(if (enabled) "Protection enabled" else "Protection paused")
        })

        content.addView(actionCard(
            "Clear cookies & site storage",
            "Remove WebView cookies and stored web data.",
            "CLEAR DATA"
        ) {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                toast("Site data cleared")
            }
        })

        content.addView(actionCard(
            "Reset blocked-request counter",
            "Reset the existing privacy shield statistics.",
            "RESET"
        ) {
            blocker.resetCounter()
            toast("Blocked counter reset")
        })

        content.addView(actionCard(
            "Clear saved site permissions",
            "Remove saved camera, microphone and location decisions.",
            "CLEAR"
        ) {
            permissions.clearAll()
            toast("Site permissions cleared")
        })

        content.addView(section("SEARCH & ADDRESS"))

        content.addView(actionCard(
            "Search engine",
            "Current: ${browserPrefs.getString("search_engine", "Google")}",
            "CHANGE"
        ) {
            chooseSearchEngine()
        })

        content.addView(switchCard(
            "Voice-ready address bar",
            "Keep address-bar voice search enabled for supported builds.",
            "voice_search",
            true
        ))

        content.addView(switchCard(
            "Smart suggestions",
            "Allow history, bookmarks and open-tab suggestions.",
            "smart_suggestions",
            true
        ))

        content.addView(section("TABS & SESSION"))

        content.addView(switchCard(
            "Tab groups",
            "Store the tab-group preference; grouping UI will use it in the tab-system pass.",
            "tab_groups",
            true
        ))

        content.addView(switchCard(
            "Keep recently closed tabs",
            "Keep up to 20 normal tabs available for reopening.",
            "recently_closed",
            true
        ))

        content.addView(switchCard(
            "Restore previous session",
            "Prefer the last normal browsing session at startup.",
            "restore_session",
            true
        ))

        content.addView(section("MEDIA & WEB APPS"))

        content.addView(switchCard(
            "Picture-in-picture ready",
            "Keep media/PiP capability enabled for supported WebView content.",
            "pip_enabled",
            true
        ))

        content.addView(switchCard(
            "Fullscreen media",
            "Allow websites to request immersive media playback.",
            "fullscreen_media",
            true
        ))

        content.addView(switchCard(
            "Web-app / PWA support",
            "Store the web-app preference for the supported-site/PWA pass.",
            "pwa_enabled",
            true
        ))

        content.addView(section("POWER USER"))

        content.addView(switchCard(
            "WebView developer tools",
            "Enable Chrome remote debugging for this app's WebViews.",
            "developer_tools",
            false
        ) { enabled ->
            WebView.setWebContentsDebuggingEnabled(enabled)
            toast(if (enabled) "WebView debugging enabled" else "WebView debugging disabled")
        })

        content.addView(actionCard(
            "Downloads",
            "Open Android's system download manager.",
            "OPEN"
        ) {
            runCatching {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            }.onFailure {
                toast("Downloads screen is unavailable")
            }
        })

        content.addView(section("PAGE TOOLS"))

        content.addView(actionCard(
            "Page tools",
            "Read aloud, copy page text, inspect page info, zoom, cache and capture.",
            "OPEN"
        ) {
            val intent = Intent(this, PageToolsActivity::class.java)
            val currentUrl = browserPrefs.getString("current_url", "") ?: ""
            if (currentUrl.isNotBlank()) intent.putExtra("url", currentUrl)
            startActivity(intent)
        })

        content.addView(section("BROWSER DIAGNOSTICS"))

        content.addView(actionCard(
            "Browser diagnostics",
            "Inspect WebView runtime, privacy protection and engine state.",
            "OPEN"
        ) {
            startActivity(Intent(this, BrowserDiagnosticsActivity::class.java))
        })

        content.addView(actionCard(
            "Share advanced configuration",
            "Share the current OLIKH power-center settings.",
            "SHARE"
        ) {
            shareConfiguration()
        })

        content.addView(actionCard(
            "Reset advanced controls",
            "Restore this power center's preferences to safe defaults.",
            "RESET"
        ) {
            prefs.edit().clear().apply()
            toast("Advanced controls reset")
            recreate()
        })

        content.addView(Space(this), LinearLayout.LayoutParams(1, dp(18)))

        content.addView(TextView(this).apply {
            text = "OLIKH • Advanced Browser Batch 3 • " +
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun section(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(muted)
            setPadding(dp(5), dp(17), dp(5), dp(8))
        }

    private fun switchCard(
        title: String,
        summary: String,
        key: String,
        default: Boolean,
        onChanged: ((Boolean) -> Unit)? = null
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(10), dp(14))
            setBackgroundColor(surface)
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        copy.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(ink)
        })

        copy.addView(TextView(this).apply {
            text = summary
            textSize = 10f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })

        box.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))

        val sw = Switch(this).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, value ->
                prefs.edit().putBoolean(key, value).apply()
                onChanged?.invoke(value)
            }
        }

        box.addView(sw, LinearLayout.LayoutParams(-2, -2))

        return wrap(box)
    }

    private fun browserSwitchCard(
        title: String,
        summary: String,
        key: String,
        default: Boolean
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(10), dp(14))
            setBackgroundColor(surface)
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        copy.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(ink)
        })

        copy.addView(TextView(this).apply {
            text = summary
            textSize = 10f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })

        box.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))

        val sw = Switch(this).apply {
            isChecked = browserPrefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, value ->
                browserPrefs.edit().putBoolean(key, value).apply()
                toast("$title ${if (value) "enabled" else "disabled"}")
            }
        }

        box.addView(sw, LinearLayout.LayoutParams(-2, -2))

        return wrap(box)
    }

    private fun actionCard(
        title: String,
        summary: String,
        action: String,
        click: () -> Unit
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(10), dp(14))
            setBackgroundColor(surface)
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        copy.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(ink)
        })

        copy.addView(TextView(this).apply {
            text = summary
            textSize = 10f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })

        box.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))

        box.addView(Button(this).apply {
            text = action
            textSize = 10f
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-2, dp(48)))

        return wrap(box)
    }

    private fun wrap(view: View): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        outer.addView(view, LinearLayout.LayoutParams(-1, -2))
        return outer
    }

    private fun chooseSearchEngine() {
        val names = listOf("Google", "Bing", "DuckDuckGo", "Brave", "Startpage")
        val current = browserPrefs.getString("search_engine", "Google")
        val checked = names.indexOf(current).coerceAtLeast(0)

        android.app.AlertDialog.Builder(this)
            .setTitle("Search engine")
            .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                browserPrefs.edit().putString("search_engine", names[which]).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareConfiguration() {
        val advanced = prefs.all.toSortedMap()
            .entries
            .joinToString("\n") { "${it.key} = ${it.value}" }

        val text = buildString {
            append("OLIKH Advanced Browser Configuration\n\n")
            append("Search engine = ")
            append(browserPrefs.getString("search_engine", "Google"))
            append("\nBlocker = ")
            append(blocker.isEnabled())
            append("\nBlocked requests = ")
            append(blocker.blockedRequests())
            append("\nBlocked hosts = ")
            append(blocker.blockedHostCount())
            append("\n\nAdvanced preferences:\n")
            append(advanced)
        }

        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share OLIKH configuration"
        ))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
