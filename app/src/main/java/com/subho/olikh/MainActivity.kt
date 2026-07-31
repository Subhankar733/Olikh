package com.subho.olikh

import android.app.DownloadManager

import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.Manifest
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.view.ViewGroup
import android.view.Gravity
import android.os.Message
import android.os.Build
import android.webkit.SafeBrowsingResponse
import android.webkit.RenderProcessGoneDetail
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.ValueCallback
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ConsoleMessage
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private val olikhBlocker by lazy {
        OlikhBlocker(applicationContext)
    }

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 7002




    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility = 0

    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingLocationOrigin: String? = null
    private var pendingLocationCallback: GeolocationPermissions.Callback? = null
    private val locationPermissionRequestCode = 7003
    private var pendingClientCertRequest: ClientCertRequest? = null

    private val webPermissionRequestCode = 7001


    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var browserContainer: FrameLayout
    private lateinit var btnTabs: Button
    private lateinit var btnNewTab: ImageButton

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0

    private val activeTab: BrowserTab?
        get() = tabs.getOrNull(activeTabIndex)

    private val browserPrefs by lazy {
        getSharedPreferences("olikh_browser", MODE_PRIVATE)
    }

    private val sitePermissionManager by lazy {
        SitePermissionManager(this)
    }

    private val homePage: String
        get() = browserPrefs.getString(
            "home_page",
            "https://www.google.com"
        ) ?: "https://www.google.com"


    private fun isOlikhStartPageUrl(url: String?): Boolean {
        return url == "https://olikh.local/start"
    }

    private data class QuickAccessItem(
        val name: String,
        val url: String
    )

    private fun defaultQuickAccessItems(): List<QuickAccessItem> {
        return listOf(
            QuickAccessItem("Google", "https://www.google.com"),
            QuickAccessItem("YouTube", "https://www.youtube.com"),
            QuickAccessItem("Wikipedia", "https://en.wikipedia.org"),
            QuickAccessItem("GitHub", "https://github.com")
        )
    }

    private fun getQuickAccessItems(): MutableList<QuickAccessItem> {
        val raw = browserPrefs.getString(
            "quick_access_items",
            null
        )

        if (raw.isNullOrBlank()) {
            return defaultQuickAccessItems().toMutableList()
        }

        val items = raw
            .split("\n")
            .mapNotNull { line ->
                val parts = line.split("\t", limit = 2)

                if (parts.size != 2) {
                    null
                } else {
                    val name = parts[0].trim()
                    val url = parts[1].trim()

                    if (name.isBlank() || url.isBlank()) {
                        null
                    } else {
                        QuickAccessItem(name, url)
                    }
                }
            }
            .toMutableList()

        return if (items.isEmpty()) {
            defaultQuickAccessItems().toMutableList()
        } else {
            items
        }
    }

    private fun saveQuickAccessItems(
        items: List<QuickAccessItem>
    ) {
        val raw = items.joinToString("\n") {
            val safeName =
                it.name.replace("\n", " ").replace("\t", " ")

            val safeUrl =
                it.url.replace("\n", "").replace("\t", "")

            "$safeName\t$safeUrl"
        }

        browserPrefs.edit()
            .putString("quick_access_items", raw)
            .apply()
    }

    private fun normalizeQuickAccessUrl(
        raw: String
    ): String {
        val value = raw.trim()

        if (value.startsWith("http://", true) ||
            value.startsWith("https://", true)
        ) {
            return value
        }

        return "https://$value"
    }

    private fun quickAccessIcon(
        name: String
    ): String {
        return name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "•"
    }

    private fun buildQuickAccessHtml(): String {
        return getQuickAccessItems()
            .joinToString("\n") { item ->

                val safeName =
                    escapeHtml(item.name)

                val safeUrl =
                    escapeHtml(item.url)

                val icon =
                    escapeHtml(
                        quickAccessIcon(item.name)
                    )

                """
            <a class="site"
               href="$safeUrl">

                <div class="site-icon">$icon</div>
                <div class="site-name">$safeName</div>

            </a>
                """.trimIndent()
            }
    }

    private fun showOlikhStartPage() {
        showingErrorPage = false
        failedUrl = null

        val blockerEnabled = olikhBlocker.isEnabled()
        val blockedRequests = olikhBlocker.blockedRequests()
        val blockedDomains = olikhBlocker.blockedHostCount()

        val blockerStatus =
            if (blockerEnabled) "Protection active"
            else "Protection paused"

        val blockerIcon =
            if (blockerEnabled) "&#10003;"
            else "&#8212;"

        val searchEngine =
            escapeHtml(currentSearchEngine())

        val quickAccessHtml =
            getQuickAccessItems()
                .take(8)
                .joinToString("\n") { item ->
                    val safeName = escapeHtml(item.name)
                    val safeUrl = escapeHtml(item.url)

                    val faviconUrl = runCatching {
                        val uri = Uri.parse(item.url)
                        val scheme = uri.scheme ?: "https"
                        val host = uri.host.orEmpty()

                        if (host.isBlank()) {
                            ""
                        } else {
                            "$scheme://$host/favicon.ico"
                        }
                    }.getOrDefault("")

                    val safeFavicon =
                        escapeHtml(faviconUrl)

                    val fallback =
                        escapeHtml(quickAccessIcon(item.name))

                    """
                    <a class="site" href="$safeUrl">
                        <div class="site-icon">
                            <span class="fallback">$fallback</span>
                            <img
                                src="$safeFavicon"
                                alt=""
                                onload="this.style.display='block';this.previousElementSibling.style.display='none';"
                                onerror="this.style.display='none';this.previousElementSibling.style.display='flex';">
                        </div>
                        <div class="site-name">$safeName</div>
                    </a>
                    """.trimIndent()
                }

        val recentHtml =
            historyManager.getAll()
                .asSequence()
                .filter {
                    it.url.startsWith("http://") ||
                    it.url.startsWith("https://")
                }
                .distinctBy { it.url }
                .take(2)
                .map { entry ->
                    val safeTitle =
                        escapeHtml(
                            entry.title
                                .trim()
                                .ifBlank { entry.url }
                                .take(34)
                        )

                    val safeUrl =
                        escapeHtml(entry.url)

                    val host = runCatching {
                        Uri.parse(entry.url).host.orEmpty()
                    }.getOrDefault("")

                    val safeHost =
                        escapeHtml(
                            host.removePrefix("www.")
                                .ifBlank { entry.url }
                                .take(36)
                        )

                    """
                    <a class="row" href="$safeUrl">
                        <div class="row-icon">&#8634;</div>
                        <div class="row-text">
                            <div class="row-title">$safeTitle</div>
                            <div class="row-sub">$safeHost</div>
                        </div>
                        <div class="row-arrow">&#8250;</div>
                    </a>
                    """.trimIndent()
                }
                .toList()
                .joinToString("\n")
                .ifBlank {
                    """
                    <div class="empty">
                        Sites you visit will appear here.
                    </div>
                    """.trimIndent()
                }

        val bookmarkHtml =
            bookmarkManager.getAll()
                .take(2)
                .joinToString("\n") { entry ->
                    val safeTitle =
                        escapeHtml(
                            entry.title
                                .trim()
                                .ifBlank { entry.url }
                                .take(34)
                        )

                    val safeUrl =
                        escapeHtml(entry.url)

                    val host = runCatching {
                        Uri.parse(entry.url).host.orEmpty()
                    }.getOrDefault("")

                    val safeHost =
                        escapeHtml(
                            host.removePrefix("www.")
                                .ifBlank { entry.url }
                                .take(36)
                        )

                    """
                    <a class="row" href="$safeUrl">
                        <div class="row-icon">&#9734;</div>
                        <div class="row-text">
                            <div class="row-title">$safeTitle</div>
                            <div class="row-sub">$safeHost</div>
                        </div>
                        <div class="row-arrow">&#8250;</div>
                    </a>
                    """.trimIndent()
                }
                .ifBlank {
                    """
                    <div class="empty">
                        Saved bookmarks will appear here.
                    </div>
                    """.trimIndent()
                }

        val html = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<meta name="color-scheme" content="dark">
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent} :root{--bg:#090d14;--card:#141b26;--line:#263246;--text:#f7f9fc;--muted:#99a5b7;--a:#7c5cff;--b:#25c7d9}
html,body{margin:0;min-height:100%;background:linear-gradient(180deg,#121a29,#090d14 42%,#070a0f);color:var(--text);font-family:system-ui,-apple-system,sans-serif}
body{padding:22px 18px 42px}.page{max-width:720px;margin:auto}.hero{display:flex;align-items:center;justify-content:space-between;padding:6px 3px 22px}
.brand{display:flex;align-items:center;gap:12px}.logo{width:46px;height:46px;border-radius:16px;background:linear-gradient(135deg,var(--a),var(--b));display:grid;place-items:center;font-size:22px;font-weight:900;box-shadow:0 12px 30px #0008}
.kicker{font-size:11px;color:var(--muted)}.name{font-size:23px;font-weight:850}.private{padding:9px 13px;border:1px solid var(--line);background:#121925;border-radius:999px;color:#d6dce6;font-size:12px;text-decoration:none}
.search{height:60px;border-radius:22px;background:#f5f7fa;display:flex;align-items:center;padding:0 17px;box-shadow:0 16px 38px #0008;margin-bottom:24px}.search .glass{color:#596270;font-size:21px}
.search input{flex:1;border:0;outline:0;background:transparent;padding:0 13px;font-size:16px;color:#151a22}.engine{font-size:10px;background:#e3e7ed;color:#4e5866;padding:7px 9px;border-radius:999px;font-weight:800}
.section{margin-top:24px}.head{display:flex;justify-content:space-between;align-items:center;margin:0 3px 12px}.title{font-size:14px;font-weight:850}.hint{font-size:10px;color:var(--muted)}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.grid .site{min-width:0;text-decoration:none;color:var(--text);text-align:center}.grid .site-icon,.grid .siteicon{height:64px;border-radius:20px;background:linear-gradient(145deg,#1d2635,#111721);border:1px solid var(--line);display:grid;place-items:center;font-size:23px;box-shadow:0 8px 20px #0005}
.grid .site-name,.grid .sitename{font-size:10px;color:#bac3d1;margin-top:7px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.actions{display:grid;grid-template-columns:repeat(4,1fr);gap:9px}.action{text-decoration:none;color:var(--text);background:var(--card);border:1px solid var(--line);border-radius:18px;padding:14px 4px;text-align:center}.ico{font-size:20px;margin-bottom:6px}.label{font-size:10px;color:#b9c3d1}
.shield{display:flex;align-items:center;justify-content:space-between;text-decoration:none;color:var(--text);background:linear-gradient(135deg,#182130,#111721);border:1px solid var(--line);border-radius:22px;padding:17px}
.shieldleft{display:flex;align-items:center;gap:12px}.badge{width:44px;height:44px;border-radius:15px;background:linear-gradient(135deg,var(--a),var(--b));display:grid;place-items:center;font-weight:900}.pt{font-size:14px;font-weight:850}.ps{font-size:10px;color:var(--muted);margin-top:3px}.stat{text-align:right}.stat strong{font-size:18px}.stat small{display:block;font-size:9px;color:var(--muted)}
.panel{background:var(--card);border:1px solid var(--line);border-radius:20px;overflow:hidden}.footer{text-align:center;color:#505b6b;font-size:9px;letter-spacing:2px;margin-top:34px}
@media(max-width:380px){body{padding-left:14px;padding-right:14px}.grid,.actions{gap:7px}.engine{display:none}}
</style></head><body><div class="page">
<div class="hero"><div class="brand"><div class="logo">O</div><div><div class="kicker">Your browser</div><div class="name">OLIKH</div></div></div><a class="private" href="olikh://incognito">Private</a></div>
<form class="search" onsubmit="event.preventDefault();var q=document.getElementById('q').value.trim();if(q)location.href='olikh://search?q='+encodeURIComponent(q)"><span class="glass">&#8981;</span><input id="q" autocomplete="off" placeholder="Search or enter address"><span class="engine">$searchEngine</span></form>
<div class="section"><div class="head"><div class="title">Speed Dial</div><div class="hint">Quick access</div></div><div class="grid">$quickAccessHtml</div></div>
<div class="section"><div class="head"><div class="title">Browse</div><div class="hint">Firefox · Opera · Chrome inspired</div></div><div class="actions">
<a class="action" href="olikh://new-tab"><div class="ico">＋</div><div class="label">New tab</div></a>
<a class="action" href="olikh://incognito"><div class="ico">◉</div><div class="label">Private</div></a>
<a class="action" href="olikh://history"><div class="ico">↶</div><div class="label">History</div></a>
<a class="action" href="olikh://bookmarks"><div class="ico">☆</div><div class="label">Bookmarks</div></a>
<a class="action" href="olikh://downloads"><div class="ico">⇩</div><div class="label">Downloads</div></a>
<a class="action" href="olikh://settings"><div class="ico">⚙</div><div class="label">Settings</div></a>
<a class="action" href="olikh://new-tab"><div class="ico">▣</div><div class="label">New page</div></a>
<a class="action" href="olikh://toggle-blocker"><div class="ico">✓</div><div class="label">Protection</div></a>
</div></div>
<div class="section"><a class="shield" href="olikh://toggle-blocker"><div class="shieldleft"><div class="badge">$blockerIcon</div><div><div class="pt">Privacy Shield</div><div class="ps">$blockerStatus · tap to change</div></div></div><div class="stat"><strong>$blockedRequests</strong><small>blocked · $blockedDomains domains</small></div></a></div>
<div class="section"><div class="head"><div class="title">Recent</div><div class="hint">Continue browsing</div></div><div class="panel">$recentHtml</div></div>
<div class="section"><div class="head"><div class="title">Bookmarks</div><div class="hint">Saved pages</div></div><div class="panel">$bookmarkHtml</div></div>
<div class="footer">OLIKH · PRIVATE · FAST</div></div></body></html>
        """.trimIndent()

        addressBar.setText("OLIKH Start")

        webView.loadDataWithBaseURL(
            "https://olikh.local/start",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun isJavaScriptEnabled(): Boolean {
        return browserPrefs.getBoolean("javascript_enabled", true)
    }

    private fun areCookiesEnabled(): Boolean {
        return browserPrefs.getBoolean("cookies_enabled", true)
    }

    private fun areThirdPartyCookiesEnabled(): Boolean {
        return browserPrefs.getBoolean("third_party_cookies_enabled", true)
    }

    private fun isDoNotTrackEnabled(): Boolean {
        return browserPrefs.getBoolean("do_not_track_enabled", false)
    }

    private fun areImagesEnabled(): Boolean {
        return browserPrefs.getBoolean("images_enabled", true)
    }

    private fun isDomStorageEnabled(): Boolean {
        return browserPrefs.getBoolean("dom_storage_enabled", true)
    }

    private fun isDatabaseStorageEnabled(): Boolean {
        return browserPrefs.getBoolean("database_storage_enabled", true)
    }

    private fun isAutoplayEnabled(): Boolean {
        return browserPrefs.getBoolean("autoplay_enabled", false)
    }

    private fun areZoomGesturesEnabled(): Boolean {
        return browserPrefs.getBoolean("zoom_gestures_enabled", true)
    }

    private fun isWideViewportEnabled(): Boolean {
        return browserPrefs.getBoolean("wide_viewport_enabled", true)
    }

    private fun isOverviewModeEnabled(): Boolean {
        return browserPrefs.getBoolean("overview_mode_enabled", true)
    }

    private fun isContentAccessEnabled(): Boolean {
        return browserPrefs.getBoolean("content_access_enabled", true)
    }

    private fun isFileAccessEnabled(): Boolean {
        return browserPrefs.getBoolean("file_access_enabled", false)
    }

    private fun areJsPopupsEnabled(): Boolean {
        return browserPrefs.getBoolean("js_popups_enabled", false)
    }

    private fun areMultipleWindowsEnabled(): Boolean {
        return browserPrefs.getBoolean("multiple_windows_enabled", false)
    }

    private fun isCacheEnabled(): Boolean {
        return browserPrefs.getBoolean("cache_enabled", true)
    }

    private fun isDesktopViewportEnabled(): Boolean {
        return browserPrefs.getBoolean("desktop_viewport_enabled", false)
    }

    private fun currentSearchEngine(): String {
        return browserPrefs.getString("search_engine", "Google") ?: "Google"
    }

    private fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")

        return when (currentSearchEngine()) {
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
            "Bing" -> "https://www.bing.com/search?q=$encoded"
            "Brave" -> "https://search.brave.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    private val tabPrefs by lazy {
        getSharedPreferences("olikh_tabs", MODE_PRIVATE)
    }

    private val historyManager by lazy {
        HistoryManager(this)
    }

    private val bookmarkManager by lazy {
        BookmarkManager(this)
    }

    private lateinit var btnBookmark: ImageButton

    private var failedUrl: String? = null
    private var showingErrorPage = false

    @SuppressLint("SetJavaScriptEnabled")

    private fun isReaderModeEnabled(): Boolean =
        browserPrefs.getBoolean("reader_mode_enabled", false)

    private fun currentDefaultFontSize(): Int =
        browserPrefs.getInt("default_font_size", 16)

    private fun currentFixedFontSize(): Int =
        browserPrefs.getInt("fixed_font_size", 13)

    private fun currentTextEncoding(): String =
        browserPrefs.getString(
            "text_encoding",
            "UTF-8"
        ) ?: "UTF-8"

    private fun currentSansFont(): String =
        browserPrefs.getString(
            "sans_font",
            "sans-serif"
        ) ?: "sans-serif"

    private fun currentSerifFont(): String =
        browserPrefs.getString(
            "serif_font",
            "serif"
        ) ?: "serif"

    private fun currentMonospaceFont(): String =
        browserPrefs.getString(
            "monospace_font",
            "monospace"
        ) ?: "monospace"

    private fun isOffscreenPreRasterEnabled(): Boolean =
        browserPrefs.getBoolean(
            "offscreen_preraster_enabled",
            false
        )

    private fun isInitialFocusEnabled(): Boolean =
        browserPrefs.getBoolean(
            "initial_focus_enabled",
            true
        )

    private fun isAutoFitScaleEnabled(): Boolean =
        browserPrefs.getBoolean(
            "auto_fit_scale_enabled",
            false
        )


    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = callback

        val intent = runCatching {
            params?.createIntent()
        }.getOrNull() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        return try {
            startActivityForResult(intent, fileChooserRequestCode)
            true
        } catch (_: Exception) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null

            Toast.makeText(
                this,
                "No file picker available",
                Toast.LENGTH_SHORT
            ).show()

            false
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != fileChooserRequestCode) return

        val callback = fileUploadCallback ?: return
        fileUploadCallback = null

        val result =
            if (resultCode == RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(
                    resultCode,
                    data
                )
            } else {
                null
            }

        callback.onReceiveValue(result)
    }

    private fun createBrowserChromeClient(): WebChromeClient =
        object : WebChromeClient() {

            override fun onProgressChanged(
                view: WebView?,
                newProgress: Int
            ) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                return openFileChooser(
                    filePathCallback,
                    fileChooserParams
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar)
        progressBar = findViewById(R.id.progressBar)
        browserContainer = findViewById(R.id.browserContainer)
        btnTabs = findViewById(R.id.btnTabs)
        btnNewTab = findViewById(R.id.btnNewTab)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnForward = findViewById<ImageButton>(R.id.btnForward)
        val btnHome = findViewById<ImageButton>(R.id.btnHome)
        val btnReload = findViewById<ImageButton>(R.id.btnReload)
        val btnHistory = findViewById<ImageButton>(R.id.btnHistory)
        btnBookmark = findViewById(R.id.btnBookmark)

        tabs.add(
            BrowserTab(
                webView = webView,
                title = "OLIKH",
                url = homePage
            )
        )
        activeTabIndex = 0
        btnTabs.text = tabs.size.toString()

        installUiAnimations()

        CookieManager.getInstance().apply {
            setAcceptCookie(areCookiesEnabled())
            setAcceptThirdPartyCookies(webView, areCookiesEnabled() && areThirdPartyCookiesEnabled())
        }

        installDownloadListener(webView)
        installLongPressActions(webView)

        webView.settings.apply {
            javaScriptEnabled = isJavaScriptEnabled()
            domStorageEnabled = isDomStorageEnabled()
            databaseEnabled = isDatabaseStorageEnabled()

            loadsImagesAutomatically = areImagesEnabled()
            blockNetworkImage = !areImagesEnabled()

            useWideViewPort = isDesktopViewportEnabled() || isWideViewportEnabled()
            loadWithOverviewMode = isDesktopViewportEnabled() || isOverviewModeEnabled()

            setSupportZoom(areZoomGesturesEnabled())
            builtInZoomControls = areZoomGesturesEnabled()
            displayZoomControls = false
            setGeolocationEnabled(locationPermissionEnabled())

            cacheMode =
                if (isCacheEnabled()) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_NO_CACHE
                }
            mediaPlaybackRequiresUserGesture = !isAutoplayEnabled()

            allowContentAccess = isContentAccessEnabled()
            allowFileAccess = isFileAccessEnabled()

            javaScriptCanOpenWindowsAutomatically = areJsPopupsEnabled()
            setSupportMultipleWindows(areMultipleWindowsEnabled())
        }

        applyReadingDisplaySettings(webView)

        applyReadingDisplaySettings(webView)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                    ?: return super.shouldInterceptRequest(view, request)

                if (olikhBlocker.shouldBlock(url)) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "No Content",
                        mapOf(
                            "Cache-Control" to "no-store",
                            "X-OLIKH-Blocked" to "1"
                        ),
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }



            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                if (!showingErrorPage) {
                    progressBar.visibility = View.VISIBLE

                    activeTab?.let { tab ->
                        url?.let { tab.url = it }
                    }
                }

                url?.takeIf { !it.startsWith("data:text/html") }?.let {
                    if (!addressBar.hasFocus()) {
                        addressBar.setText(it)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                progressBar.visibility = View.GONE

                if (!showingErrorPage) {
                    url?.let {
                        if (!addressBar.hasFocus()) {
                            addressBar.setText(it)
                        }
                    }
                }

                activeTab?.let { tab ->
                    url?.let { tab.url = it }
                    recordHistory(tab, url)
                }

                updateNavigationButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                if (request?.isForMainFrame != true) return

                val url = request.url?.toString() ?: addressBar.text.toString()

                showNetworkError(
                    url = url,
                    description = error?.description?.toString()
                        ?: "The page could not be loaded."
                )
            }
        }

        installSitePermissionChromeClient(webView)

        addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) addressBar.post { addressBar.selectAll() }
        }

        addressBar.setOnLongClickListener {
            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val clipboardText =
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val currentText = addressBar.text.toString().trim()
            val actions = mutableListOf<String>()

            if (clipboardText.isNotBlank()) {
                actions += "Paste"
                actions += "Paste & Go"
            }

            if (currentText.isNotBlank()) {
                actions += "Copy"
                actions += "Clear"
            }

            if (actions.isEmpty()) {
                Toast.makeText(this, "Nothing to paste or copy", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Address bar")
                .setItems(actions.toTypedArray()) { _, which ->
                    when (actions[which]) {
                        "Paste" -> {
                            addressBar.setText(clipboardText)
                            addressBar.setSelection(addressBar.text.length)
                        }
                        "Paste & Go" -> {
                            addressBar.setText(clipboardText)
                            addressBar.setSelection(addressBar.text.length)
                            openInput(clipboardText)
                            addressBar.clearFocus()
                        }
                        "Copy" -> {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("OLIKH address", currentText)
                            )
                            Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show()
                        }
                        "Clear" -> {
                            addressBar.text?.clear()
                            addressBar.requestFocus()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()

            true
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->

            val enterPressed =
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN

            if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                openInput(addressBar.text.toString())
                addressBar.clearFocus()
                true
            } else {
                false
            }
        }

        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                showingErrorPage = false
                webView.goBack()
            }
        }

        btnForward.setOnClickListener {
            if (webView.canGoForward()) {
                showingErrorPage = false
                webView.goForward()
            }
        }

        btnHome.setOnClickListener {
            showingErrorPage = false
            failedUrl = null
            webView.loadUrl(homePage)
        }

        btnReload.setOnClickListener {
            val retryUrl = failedUrl

            if (showingErrorPage && !retryUrl.isNullOrBlank()) {
                showingErrorPage = false
                failedUrl = null
                webView.loadUrl(retryUrl)
            } else {
                webView.reload()
            }
        }

        btnHistory.setOnClickListener {
            showLibrary()
        }

        btnBookmark.setOnClickListener {
            toggleCurrentBookmark()
        }

        btnBookmark.setOnLongClickListener {
            showLibrary()
            true
        }

        updateBookmarkButton()

        val restoredPersistentTabs = restoreTabs()

        if (!restoredPersistentTabs) {
            if (savedInstanceState == null) {
                showOlikhStartPage()
            } else {
                webView.restoreState(savedInstanceState)
            }
        }

        btnNewTab.setOnClickListener {
            createNewTab(
                initialUrl = "about:blank"
            )
        }

        btnMenu.setOnClickListener {
            showBrowserMenu(btnMenu)
        }

        btnTabs.setOnClickListener {
            showTabManager()
        }

        btnTabs.setOnLongClickListener {
            closeCurrentTab()
            true
        }

        updateNavigationButtons()
    }

    private fun toggleCurrentBookmark() {
        val currentUrl = webView.url
            ?.takeIf {
                it.startsWith("https://") ||
                    it.startsWith("http://")
            }
            ?: return

        if (bookmarkManager.contains(currentUrl)) {
            bookmarkManager.remove(currentUrl)
        } else {
            val pageTitle = webView.title
                ?.replace("\n", " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: activeTab?.title?.ifBlank { currentUrl }
                ?: currentUrl

            bookmarkManager.add(
                title = pageTitle,
                url = currentUrl
            )
        }

        updateBookmarkButton()
    }

    private fun updateBookmarkButton() {
        if (!::btnBookmark.isInitialized) return

        val currentUrl = webView.url
            ?.takeIf {
                it.startsWith("https://") ||
                    it.startsWith("http://")
            }

        val saved =
            currentUrl != null &&
                bookmarkManager.contains(currentUrl)

        btnBookmark.setImageResource(
            if (saved) {
                R.drawable.ic_bookmark_filled
            } else {
                R.drawable.ic_bookmark
            }
        )

        btnBookmark.contentDescription =
            if (saved) {
                "Remove bookmark"
            } else {
                "Add bookmark"
            }
    }

    private fun showBookmarks() {
        val bookmarks = bookmarkManager.getAll()

        if (bookmarks.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setMessage("No bookmarks saved yet.")
                .setPositiveButton("Close", null)
                .show()

            return
        }

        val items = bookmarks.map { entry ->
            val cleanTitle = entry.title
                .replace("\n", " ")
                .trim()
                .ifBlank { entry.url }
                .take(60)

            "$cleanTitle\n${entry.url}"
        }.toTypedArray()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Bookmarks · ${bookmarks.size}")
            .setItems(items) { _, index ->
                val entry =
                    bookmarks.getOrNull(index)
                        ?: return@setItems

                showingErrorPage = false
                failedUrl = null

                activeTab?.apply {
                    showingError = false
                    failedUrl = null
                }

                webView.loadUrl(entry.url)
            }
            .setNegativeButton("Clear") { _, _ ->
                confirmClearBookmarks()
            }
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun confirmClearBookmarks() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear bookmarks?")
            .setMessage("All saved bookmarks will be removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                bookmarkManager.clear()
                updateBookmarkButton()
            }
            .show()
    }

    private fun showLibrary() {
        LibraryDialog(
            context = this,
            history = historyManager.getAll(),
            bookmarks = bookmarkManager.getAll(),

            onOpenHistory = { entry ->
                showingErrorPage = false
                failedUrl = null

                activeTab?.apply {
                    showingError = false
                    failedUrl = null
                }

                webView.loadUrl(entry.url)
            },

            onOpenBookmark = { entry ->
                showingErrorPage = false
                failedUrl = null

                activeTab?.apply {
                    showingError = false
                    failedUrl = null
                }

                webView.loadUrl(entry.url)
            },

            onDeleteHistory = { entry ->
                historyManager.remove(entry.url)

                Toast.makeText(
                    this,
                    "History entry deleted",
                    Toast.LENGTH_SHORT
                ).show()
            },

            onDeleteBookmark = { entry ->
                bookmarkManager.remove(entry.url)
                updateBookmarkButton()

                Toast.makeText(
                    this,
                    "Bookmark deleted",
                    Toast.LENGTH_SHORT
                ).show()
            },

            onClearHistory = {
                confirmClearHistory()
            },

            onClearBookmarks = {
                confirmClearBookmarks()
            }
        ).show()
    }

    private fun showHistory() {
        val history = historyManager.getAll()

        if (history.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("History")
                .setMessage("No browsing history yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = history.map { entry ->
            val cleanTitle = entry.title
                .replace("\n", " ")
                .trim()
                .ifBlank { entry.url }
                .take(60)

            "$cleanTitle\n${entry.url}"
        }.toTypedArray()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("History · ${history.size}")
            .setItems(items) { _, index ->
                val entry = history.getOrNull(index)
                    ?: return@setItems

                showingErrorPage = false
                failedUrl = null

                activeTab?.apply {
                    showingError = false
                    failedUrl = null
                }

                webView.loadUrl(entry.url)
            }
            .setNegativeButton("Clear") { _, _ ->
                confirmClearHistory()
            }
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun confirmClearHistory() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear history?")
            .setMessage("All saved browsing history will be removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                historyManager.clear()
            }
            .show()
    }

    private fun showTabManager() {
        if (tabs.isEmpty()) return

        TabManagerDialog(
            browserTabs = tabs.toList(),
            activeIndex = activeTabIndex,

            onSelectTab = { index ->
                switchToTab(index)
            },

            onCloseTab = { index ->
                closeTab(index)
            },

            onDuplicateTab = { index ->
                duplicateTab(index)
            },

            onNewTab = {
                createNewTab(initialUrl = "about:blank")
            }
        ).show()
    }

    private fun duplicateTab(index: Int) {
        val source = tabs.getOrNull(index) ?: return
        val sourceUrl = source.webView.url?.trim()?.takeIf { it.isNotBlank() } ?: source.url.trim()

        if (sourceUrl.isBlank() || sourceUrl == "about:blank" || isOlikhStartPageUrl(sourceUrl)) {
            createNewTab(incognito = source.incognito, initialUrl = "about:blank")
        } else {
            createNewTab(incognito = source.incognito, initialUrl = sourceUrl)
        }
    }

    private data class ClosedTabEntry(
        val title: String,
        val url: String
    )

    private val recentlyClosedTabs =
        ArrayDeque<ClosedTabEntry>()

    private fun rememberClosedTab(tab: BrowserTab) {
        if (tab.incognito) return

        val url = tab.url
            .ifBlank { tab.webView.url.orEmpty() }
            .trim()

        if (
            url.isBlank() ||
            url == "about:blank"
        ) {
            return
        }

        val title = tab.title
            .replace("\n", " ")
            .trim()
            .ifBlank { url }

        recentlyClosedTabs.addFirst(
            ClosedTabEntry(
                title = title,
                url = url
            )
        )

        while (recentlyClosedTabs.size > 20) {
            recentlyClosedTabs.removeLast()
        }
    }

    private fun reopenLastClosedTab() {
        val entry = recentlyClosedTabs
            .removeFirstOrNull()

        if (entry == null) {
            Toast.makeText(
                this,
                "No recently closed tabs",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        createNewTab(
            incognito = false,
            initialUrl = entry.url
        )
    }

    private fun showRecentlyClosedTabs() {
        if (recentlyClosedTabs.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Recently closed")
                .setMessage("No recently closed tabs.")
                .setPositiveButton("Close", null)
                .show()

            return
        }

        val entries = recentlyClosedTabs.toList()

        val labels = entries.map { entry ->
            val title = entry.title
                .replace("\n", " ")
                .trim()
                .ifBlank { entry.url }
                .take(60)

            "$title\n${entry.url}"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Recently closed · ${entries.size}")
            .setItems(labels) { _, index ->
                val entry =
                    entries.getOrNull(index)
                        ?: return@setItems

                recentlyClosedTabs.remove(entry)

                createNewTab(
                    incognito = false,
                    initialUrl = entry.url
                )
            }
            .setNeutralButton("Clear") { _, _ ->
                recentlyClosedTabs.clear()

                Toast.makeText(
                    this,
                    "Recently closed tabs cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun closeTab(index: Int) {
        val closingTab = tabs.getOrNull(index) ?: return
        val wasActive = index == activeTabIndex

        rememberClosedTab(closingTab)
        tabs.removeAt(index)

        (closingTab.webView.parent as? android.view.ViewGroup)
            ?.removeView(closingTab.webView)

        closingTab.webView.stopLoading()
        closingTab.webView.webChromeClient = null
        closingTab.webView.webViewClient = WebViewClient()
        closingTab.webView.removeAllViews()
        closingTab.webView.destroy()

        if (tabs.isEmpty()) {
            activeTabIndex = 0
            createNewTab()
            return
        }

        if (wasActive) {
            val nextIndex = index.coerceAtMost(tabs.lastIndex)
            switchToTab(nextIndex)
        } else {
            if (index < activeTabIndex) {
                activeTabIndex--
            }

            btnTabs.text = tabs.size.toString()
        }
    }

    private fun closeCurrentTab() {
        if (tabs.isEmpty()) return

        val closingIndex = activeTabIndex
        val closingTab = tabs[closingIndex]

        rememberClosedTab(closingTab)
        tabs.removeAt(closingIndex)

        if (closingTab.webView.parent != null) {
            (closingTab.webView.parent as? android.view.ViewGroup)
                ?.removeView(closingTab.webView)
        }

        closingTab.webView.stopLoading()
        closingTab.webView.webChromeClient = null
        closingTab.webView.webViewClient = WebViewClient()
        closingTab.webView.destroy()

        if (tabs.isEmpty()) {
            activeTabIndex = 0
            createNewTab()
            return
        }

        val nextIndex =
            if (closingIndex >= tabs.size) {
                tabs.lastIndex
            } else {
                closingIndex
            }

        switchToTab(nextIndex)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewTab(
        incognito: Boolean = false,
        initialUrl: String = homePage
    ) {
        val newWebView = WebView(this)

        newWebView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        newWebView.settings.apply {
            javaScriptEnabled = isJavaScriptEnabled()
            domStorageEnabled = isDomStorageEnabled()
            databaseEnabled = isDatabaseStorageEnabled()

            loadsImagesAutomatically = areImagesEnabled()
            blockNetworkImage = !areImagesEnabled()

            useWideViewPort = isDesktopViewportEnabled() || isWideViewportEnabled()
            loadWithOverviewMode = isDesktopViewportEnabled() || isOverviewModeEnabled()

            setSupportZoom(areZoomGesturesEnabled())
            builtInZoomControls = areZoomGesturesEnabled()
            displayZoomControls = false
            setGeolocationEnabled(locationPermissionEnabled())

            cacheMode =
                if (isCacheEnabled()) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_NO_CACHE
                }
            mediaPlaybackRequiresUserGesture = !isAutoplayEnabled()

            allowContentAccess = isContentAccessEnabled()
            allowFileAccess = isFileAccessEnabled()

            javaScriptCanOpenWindowsAutomatically = areJsPopupsEnabled()
            setSupportMultipleWindows(areMultipleWindowsEnabled())
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(areCookiesEnabled())
            setAcceptThirdPartyCookies(newWebView, areCookiesEnabled() && areThirdPartyCookiesEnabled())
        }

        installDownloadListener(newWebView)
        installLongPressActions(newWebView)

        val tab = BrowserTab(
            webView = newWebView,
            title = if (incognito) "Incognito" else "New Tab",
            url = initialUrl,
            incognito = incognito
        )

        tabs.add(tab)
        activeTabIndex = tabs.lastIndex

        newWebView.webViewClient = createTabWebViewClient(tab)

        installSitePermissionChromeClient(newWebView, tab)

        switchToTab(activeTabIndex)

        if (!incognito && initialUrl == "about:blank") {
            showOlikhStartPage()
        } else {
            newWebView.loadUrl(initialUrl)
        }
    }

    private fun switchToTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return

        activeTabIndex = index
        webView = tab.webView

        browserContainer.removeAllViews()

        if (webView.parent != null) {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        }

        browserContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        addressBar.setText(
            webView.url ?: tab.url.ifBlank { homePage }
        )

        btnTabs.text = tabs.size.toString()

        showingErrorPage = tab.showingError
        failedUrl = tab.failedUrl

        title = tab.title.ifBlank { "OLIKH" }

        updateNavigationButtons()
        updateBookmarkButton()
    }


    private fun handleMainFrameHttpError(
        tab: BrowserTab?,
        request: WebResourceRequest?,
        response: WebResourceResponse?
    ) {
        if (request?.isForMainFrame != true) return

        val code = response?.statusCode ?: return
        if (code < 400) return

        val url = request.url.toString()

        if (tab != null) {
            tab.failedUrl = url
            tab.showingError = true
        }

        if (tab == null || activeTab === tab) {
            failedUrl = url
            showingErrorPage = true

            showNetworkError(
                url,
                "HTTP $code ${response?.reasonPhrase.orEmpty()}".trim()
            )
        }
    }

    private fun handleSslFailure(
        tab: BrowserTab?,
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        handler?.cancel()

        val url = error?.url ?: view?.url ?: return

        if (tab != null) {
            tab.failedUrl = url
            tab.showingError = true
        }

        if (tab == null || activeTab === tab) {
            failedUrl = url
            showingErrorPage = true

            showNetworkError(
                url,
                "Secure connection failed. OLIKH blocked this page."
            )
        }
    }

    private fun createTabWebViewClient(tab: BrowserTab): WebViewClient {
        return object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                    ?: return super.shouldInterceptRequest(view, request)

                if (olikhBlocker.shouldBlock(url)) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "No Content",
                        mapOf(
                            "Cache-Control" to "no-store",
                            "X-OLIKH-Blocked" to "1"
                        ),
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }



            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {

                val uri = request?.url ?: return false

                if (handleOlikhUri(uri)) {
                    return true
                }

                return handleExternalUri(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {

                if (url.isNullOrBlank()) return false

                val uri = Uri.parse(url)

                if (handleOlikhUri(uri)) {
                    return true
                }

                return handleExternalUri(uri)
            }

            override fun onSafeBrowsingHit(
                view: WebView?,
                request: WebResourceRequest?,
                threatType: Int,
                callback: SafeBrowsingResponse?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    callback?.backToSafety(true)
                    Toast.makeText(
                        this@MainActivity,
                        "Unsafe page blocked",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val crashedView = view ?: return true
                val urlToRestore =
                    crashedView.url
                        ?: tab.url.takeIf { it.isNotBlank() }
                        ?: homePage

                val wasActive = activeTab === tab

                (crashedView.parent as? ViewGroup)
                    ?.removeView(crashedView)

                crashedView.webChromeClient = null
                crashedView.webViewClient = WebViewClient()
                crashedView.destroy()

                val replacement = WebView(this@MainActivity)

                replacement.layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )

                replacement.settings.apply {
                    javaScriptEnabled = isJavaScriptEnabled()
                    domStorageEnabled = isDomStorageEnabled()
                    databaseEnabled = isDatabaseStorageEnabled()

                    loadsImagesAutomatically = areImagesEnabled()
                    blockNetworkImage = !areImagesEnabled()

                    useWideViewPort =
                        isDesktopViewportEnabled() ||
                        isWideViewportEnabled()

                    loadWithOverviewMode =
                        isDesktopViewportEnabled() ||
                        isOverviewModeEnabled()

                    setSupportZoom(areZoomGesturesEnabled())
                    builtInZoomControls = areZoomGesturesEnabled()
                    displayZoomControls = false

                    setGeolocationEnabled(
                        locationPermissionEnabled()
                    )

                    cacheMode =
                        if (isCacheEnabled()) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_NO_CACHE
                        }

                    mediaPlaybackRequiresUserGesture =
                        !isAutoplayEnabled()

                    allowContentAccess =
                        isContentAccessEnabled()

                    allowFileAccess =
                        isFileAccessEnabled()

                    javaScriptCanOpenWindowsAutomatically =
                        areJsPopupsEnabled()

                    setSupportMultipleWindows(
                        areMultipleWindowsEnabled()
                    )
                }

                applyReadingDisplaySettings(replacement)
                applyAdvancedSettings(replacement)

                CookieManager.getInstance().apply {
                    setAcceptCookie(areCookiesEnabled())

                    setAcceptThirdPartyCookies(
                        replacement,
                        areCookiesEnabled() &&
                        areThirdPartyCookiesEnabled()
                    )
                }

                installDownloadListener(replacement)
                installLongPressActions(replacement)

                tab.webView = replacement
                tab.url = urlToRestore
                tab.failedUrl = null
                tab.showingError = false

                replacement.webViewClient =
                    createTabWebViewClient(tab)

                installSitePermissionChromeClient(
                    replacement,
                    tab
                )

                if (wasActive) {
                    browserContainer.removeAllViews()
                    browserContainer.addView(replacement)
                    webView = replacement

                    showingErrorPage = false
                    failedUrl = null

                    Toast.makeText(
                        this@MainActivity,
                        "Web page crashed — recovered",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                replacement.loadUrl(urlToRestore)

                return true
            }

            override fun doUpdateVisitedHistory(
                view: WebView?,
                url: String?,
                isReload: Boolean
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let { tab.url = it }
            }

            override fun onScaleChanged(
                view: WebView?,
                oldScale: Float,
                newScale: Float
            ) {
                super.onScaleChanged(view, oldScale, newScale)
            }

            override fun onReceivedClientCertRequest(
                view: WebView?,
                request: ClientCertRequest?
            ) {
                if (request == null) return

                pendingClientCertRequest?.cancel()
                pendingClientCertRequest = request

                KeyChain.choosePrivateKeyAlias(
                    this@MainActivity,
                    KeyChainAliasCallback { alias ->
                        runOnUiThread {
                            val pending = pendingClientCertRequest
                            pendingClientCertRequest = null

                            if (pending !== request) {
                                request.cancel()
                                return@runOnUiThread
                            }

                            if (alias.isNullOrBlank()) {
                                request.cancel()
                                return@runOnUiThread
                            }

                            Thread {
                                try {
                                    val privateKey =
                                        KeyChain.getPrivateKey(
                                            this@MainActivity,
                                            alias
                                        )

                                    val certificateChain =
                                        KeyChain.getCertificateChain(
                                            this@MainActivity,
                                            alias
                                        )

                                    runOnUiThread {
                                        if (
                                            privateKey != null &&
                                            !certificateChain.isNullOrEmpty()
                                        ) {
                                            request.proceed(
                                                privateKey,
                                                certificateChain
                                            )
                                        } else {
                                            request.cancel()
                                        }
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        request.cancel()

                                        Toast.makeText(
                                            this@MainActivity,
                                            "Certificate could not be loaded",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }.start()
                        }
                    },
                    request.keyTypes,
                    request.principals,
                    request.host,
                    request.port,
                    null
                )
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                if (handler == null) return

                val username = EditText(this@MainActivity).apply {
                    hint = "Username"
                    setSingleLine(true)
                }

                val password = EditText(this@MainActivity).apply {
                    hint = "Password"
                    setSingleLine(true)
                    inputType =
                        android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }

                val container = android.widget.LinearLayout(
                    this@MainActivity
                ).apply {
                    orientation = android.widget.LinearLayout.VERTICAL

                    val padding =
                        (20 * resources.displayMetrics.density).toInt()

                    setPadding(padding, padding, padding, 0)
                    addView(username)
                    addView(password)
                }

                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Sign in")
                    .setMessage(
                        buildString {
                            append(host ?: "Website")
                            if (!realm.isNullOrBlank()) {
                                append("\n")
                                append(realm)
                            }
                        }
                    )
                    .setView(container)
                    .setPositiveButton("Sign in") { _, _ ->
                        handler.proceed(
                            username.text.toString(),
                            password.text.toString()
                        )
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        handler.cancel()
                    }
                    .setOnCancelListener {
                        handler.cancel()
                    }
                    .show()
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                url?.let {
                    tab.url = it
                }



                if (activeTab === tab) {
                    progressBar.visibility = View.VISIBLE

                    url?.let {
                        if (!addressBar.hasFocus()) {
                            addressBar.setText(it)
                        }
                    }
                }
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                url?.let {
                    tab.url = it
                }

                recordHistory(tab, url)

                if (activeTab === tab) {
                    progressBar.visibility = View.GONE

                    url?.let {
                        if (!addressBar.hasFocus()) {
                            addressBar.setText(it)
                        }
                    }

                    updateNavigationButtons()
                    updateBookmarkButton()
                }
            }


            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(
                    view,
                    request,
                    errorResponse
                )

                handleMainFrameHttpError(
                    tab,
                    request,
                    errorResponse
                )
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handleSslFailure(
                    tab,
                    view,
                    handler,
                    error
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                if (request?.isForMainFrame != true) return

                tab.failedUrl = request.url?.toString()
                tab.showingError = true

                if (activeTab === tab) {
                    failedUrl = tab.failedUrl
                    showingErrorPage = true
                }
            }
        }
    }

    private fun recordHistory(tab: BrowserTab, url: String?) {
        if (tab.incognito) return

        val pageUrl = url ?: return

        if (
            !pageUrl.startsWith("https://") &&
            !pageUrl.startsWith("http://")
        ) {
            return
        }

        val pageTitle = tab.webView.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: tab.title.ifBlank { pageUrl }

        historyManager.add(
            title = pageTitle,
            url = pageUrl
        )
    }

    private fun installLongPressActions(targetWebView: WebView) {
        targetWebView.setOnLongClickListener {
            val result = targetWebView.hitTestResult
            val linkUrl = when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                    result.extra

                else -> null
            }

            if (linkUrl.isNullOrBlank()) {
                return@setOnLongClickListener false
            }

            val items = arrayOf(
                "Open link",
                "Open in new tab",
                "Open in incognito tab",
                "Copy link",
                "Share link"
            )

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Link")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> targetWebView.loadUrl(linkUrl)

                        1 -> createNewTab(
                            incognito = false,
                            initialUrl = linkUrl
                        )

                        2 -> createNewTab(
                            incognito = true,
                            initialUrl = linkUrl
                        )

                        3 -> {
                            val clipboard =
                                getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager

                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "OLIKH link",
                                    linkUrl
                                )
                            )

                            Toast.makeText(
                                this,
                                "Link copied",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        4 -> {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, linkUrl)
                            }

                            startActivity(
                                Intent.createChooser(intent, "Share link")
                            )
                        }
                    }
                }
                .show()

            true
        }
    }

    private fun animateBrowserButton(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()

                    val tilt =
                        if (v.id == R.id.btnNewTab) 8f else 5f

                    v.animate()
                        .scaleX(0.78f)
                        .scaleY(0.78f)
                        .rotation(tilt)
                        .alpha(0.68f)
                        .translationY(3f)
                        .setDuration(85L)
                        .start()
                }

                MotionEvent.ACTION_UP -> {
                    v.animate().cancel()

                    v.animate()
                        .scaleX(1.16f)
                        .scaleY(1.16f)
                        .rotation(-3f)
                        .alpha(1f)
                        .translationY(-2f)
                        .setDuration(110L)
                        .withEndAction {
                            v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .rotation(0f)
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(150L)
                                .start()
                        }
                        .start()
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()

                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(140L)
                        .start()
                }
            }

            false
        }
    }

    private fun installUiAnimations() {
        val ids = intArrayOf(
            R.id.btnTabs,
            R.id.btnNewTab,
            R.id.btnMenu,
            R.id.btnBack,
            R.id.btnForward,
            R.id.btnHome,
            R.id.btnHistory,
            R.id.btnBookmark,
            R.id.btnReload
        )

        ids.forEach { id ->
            findViewById<View>(id)?.let {
                animateBrowserButton(it)
            }
        }

        addressBar.setOnFocusChangeListener { view, focused ->
            view.animate().cancel()

            if (focused) {
                view.animate()
                    .scaleX(1.035f)
                    .scaleY(1.08f)
                    .translationY(-4f)
                    .alpha(1f)
                    .setDuration(180L)
                    .start()
            } else {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .alpha(0.96f)
                    .setDuration(220L)
                    .start()
            }
        }
    }

    private fun installDownloadListener(targetWebView: WebView) {
        targetWebView.setDownloadListener {
            url,
            userAgent,
            contentDisposition,
            mimeType,
            _ ->

            if (url.isNullOrBlank()) {
                return@setDownloadListener
            }

            DownloadHelper.download(
                context = this,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType
            )
        }
    }

    private fun showDownloads() {
        val prefs = getSharedPreferences(
            "olikh_downloads",
            MODE_PRIVATE
        )

        val downloads = prefs.all
            .mapNotNull { (key, value) ->
                if (!key.startsWith("download_")) {
                    return@mapNotNull null
                }

                val id = key
                    .removePrefix("download_")
                    .toLongOrNull()
                    ?: return@mapNotNull null

                val fileName = value as? String
                    ?: return@mapNotNull null

                id to fileName
            }
            .sortedByDescending { it.first }

        if (downloads.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Downloads")
                .setMessage("No downloads yet.")
                .setPositiveButton("OK", null)
                .show()

            return
        }

        val manager =
            getSystemService(Context.DOWNLOAD_SERVICE)
                as DownloadManager

        val labels = downloads.map { (id, fileName) ->
            var statusText = "Unknown"

            runCatching {
                manager.query(
                    DownloadManager.Query().setFilterById(id)
                )?.use { cursor ->

                    if (cursor.moveToFirst()) {
                        val statusIndex =
                            cursor.getColumnIndex(
                                DownloadManager.COLUMN_STATUS
                            )

                        val downloadedIndex =
                            cursor.getColumnIndex(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                            )

                        val totalIndex =
                            cursor.getColumnIndex(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                            )

                        val status =
                            if (statusIndex >= 0) {
                                cursor.getInt(statusIndex)
                            } else {
                                -1
                            }

                        val downloaded =
                            if (downloadedIndex >= 0) {
                                cursor.getLong(downloadedIndex)
                            } else {
                                0L
                            }

                        val total =
                            if (totalIndex >= 0) {
                                cursor.getLong(totalIndex)
                            } else {
                                -1L
                            }

                        statusText = when (status) {
                            DownloadManager.STATUS_PENDING ->
                                "Queued"

                            DownloadManager.STATUS_RUNNING -> {
                                if (total > 0L) {
                                    val progress =
                                        ((downloaded * 100L) / total)
                                            .coerceIn(0L, 100L)

                                    "Downloading • $progress%"
                                } else {
                                    "Downloading"
                                }
                            }

                            DownloadManager.STATUS_PAUSED ->
                                "Paused"

                            DownloadManager.STATUS_SUCCESSFUL ->
                                "Complete"

                            DownloadManager.STATUS_FAILED ->
                                "Failed"

                            else ->
                                "Unknown"
                        }
                    } else {
                        statusText = "Not found"
                    }
                }
            }

            "$fileName\n$statusText"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Downloads")
            .setItems(labels) { _, which ->
                val (id, fileName) = downloads[which]

                runCatching {
                    val uri =
                        manager.getUriForDownloadedFile(id)

                    if (uri == null) {
                        Toast.makeText(
                            this,
                            "$fileName is not ready yet",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@runCatching
                    }

                    val mimeType =
                        manager.getMimeTypeForDownloadedFile(id)
                            ?: "*/*"

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)

                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                    startActivity(intent)
                }.onFailure {
                    Toast.makeText(
                        this,
                        "Unable to open $fileName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNeutralButton("Clear list") { _, _ ->
                prefs.edit().clear().apply()

                Toast.makeText(
                    this,
                    "Download list cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showBrowserMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("New incognito tab")
        popup.menu.add("Reopen last closed tab")
        popup.menu.add("Recently closed")
        popup.menu.add("Find in page")
        popup.menu.add("Share page")
        popup.menu.add("Copy URL")
        popup.menu.add("Downloads")
        popup.menu.add("Page tools")
        popup.menu.add("Quick access")
        popup.menu.add("Open start page")
        popup.menu.add("Paste and go")
        popup.menu.add("Duplicate tab")
        popup.menu.add("Back to top")
        popup.menu.add("Scroll to bottom")
        popup.menu.add("Close current tab")
        popup.menu.add("Productivity tools")
        popup.menu.add("Research tools")
        popup.menu.add("Browser systems")
        popup.menu.add("Settings")

        popup.menu.add(
            if (
                webView.settings.userAgentString
                    ?.contains("OLIKH_DESKTOP") == true
            ) {
                "Mobile site"
            } else {
                "Desktop site"
            }
        )

        popup.menu.add("Clear browsing data")

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "New incognito tab" -> {
                    createNewTab(incognito = true)

                    Toast.makeText(
                        this,
                        "Incognito tab opened",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }

                "Reopen last closed tab" -> {
                reopenLastClosedTab()
                true
            }

            "Recently closed" -> {
                showRecentlyClosedTabs()
                true
            }

            "Find in page" -> {
                    showFindInPage()
                    true
                }

                "Share page" -> {
                    shareCurrentPage()
                    true
                }

                "Copy URL" -> {
                    copyCurrentUrl()
                    true
                }

                "Downloads" -> {
                showDownloads()
                true
            }

            "Page tools" -> {
                    showPageToolsMenu()
                    true
                }

                "Quick access" -> {
                    showQuickAccessManager()
                    true
                }

                "Open start page" -> {
                    showOlikhStartPage()
                    true
                }

                "Paste and go" -> {
                    pasteAndGo()
                    true
                }

                "Duplicate tab" -> {
                    duplicateCurrentTab()
                    true
                }

                "Back to top" -> {
                    webView.evaluateJavascript("window.scrollTo(0,0);", null)
                    true
                }

                "Scroll to bottom" -> {
                    webView.evaluateJavascript("window.scrollTo(0,document.documentElement.scrollHeight);", null)
                    true
                }

                "Close current tab" -> {
                    closeCurrentTab()
                    true
                }

                "Productivity tools" -> {
                    showProductivityToolsV12()
                    true
                }

                "Research tools" -> {
                    showResearchToolsV13()
                    true
                }

                "Browser systems" -> {
                    showBrowserSystemsV11()
                    true
                }

                "Settings" -> {
                    showSettings()
                    true
                }

                "Desktop site",
                "Mobile site" -> {
                    toggleDesktopSite()
                    true
                }

                "Clear browsing data" -> {
                    confirmClearBrowsingData()
                    true
                }

                else -> false
            }
        }

        popup.setOnDismissListener {
            anchor.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(140L)
                .start()
        }

        anchor.animate().cancel()
        anchor.scaleX = 1f
        anchor.scaleY = 1f
        anchor.alpha = 1f

        popup.show()
    }


    private fun showResearchToolsV13() {
        val options=arrayOf(
            "Page statistics","Copy page outline","Copy numbered headings","Copy unique links",
            "Copy external links","Copy internal links","Copy mail links","Copy image alt text",
            "Copy table text","Copy code blocks","Copy blockquotes","Copy list items",
            "Copy JSON-LD","Copy Open Graph data","Copy Twitter card data","Copy meta keywords",
            "Copy author","Copy publish date","Copy modified date","Copy robots meta",
            "Copy hreflang links","Copy stylesheet URLs","Copy script URLs","Copy iframe URLs",
            "Copy media sources","Copy form actions","Copy ARIA labels","Highlight external links",
            "Highlight nofollow links","Highlight forms","Highlight tables","Highlight code blocks",
            "Highlight quotes","Highlight missing image alt","Show link destinations","Remove highlights",
            "Copy selection + source","Search page title","Search selected exact quote"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Research tools").setItems(options){_,w->
            when(w){
                0->researchStatsV13()
                1->copyResearchV13("Outline","Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6')).map(e=>e.tagName+' '+e.innerText.trim()).join('\\n')")
                2->copyResearchV13("Numbered headings","Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6')).map((e,i)=>(i+1)+'. '+e.innerText.trim()).join('\\n')")
                3->copyResearchV13("Unique links","Array.from(new Set(Array.from(document.links).map(a=>a.href))).join('\\n')")
                4->copyResearchV13("External links","Array.from(new Set(Array.from(document.links).map(a=>a.href).filter(h=>{try{return new URL(h).host!==location.host}catch(e){return false}}))).join('\\n')")
                5->copyResearchV13("Internal links","Array.from(new Set(Array.from(document.links).map(a=>a.href).filter(h=>{try{return new URL(h).host===location.host}catch(e){return false}}))).join('\\n')")
                6->copyResearchV13("Mail links","Array.from(new Set(Array.from(document.querySelectorAll('a[href^=mailto]')).map(a=>a.href))).join('\\n')")
                7->copyResearchV13("Image alt text","Array.from(document.images).map((i,n)=>(n+1)+'. '+(i.alt||'[missing]')).join('\\n')")
                8->copyResearchV13("Tables","Array.from(document.querySelectorAll('table')).map((t,i)=>'TABLE '+(i+1)+'\\n'+t.innerText).join('\\n\\n')")
                9->copyResearchV13("Code blocks","Array.from(document.querySelectorAll('pre,code')).map(e=>e.innerText).filter(Boolean).join('\\n\\n')")
                10->copyResearchV13("Blockquotes","Array.from(document.querySelectorAll('blockquote')).map(e=>e.innerText).filter(Boolean).join('\\n\\n')")
                11->copyResearchV13("List items","Array.from(document.querySelectorAll('li')).map(e=>e.innerText.trim()).filter(Boolean).join('\\n')")
                12->copyResearchV13("JSON-LD","Array.from(document.querySelectorAll('script[type=application/ld+json]')).map(e=>e.textContent).join('\\n\\n')")
                13->copyResearchV13("Open Graph","Array.from(document.querySelectorAll('meta[property^=og:]')).map(e=>e.getAttribute('property')+': '+e.content).join('\\n')")
                14->copyResearchV13("Twitter card","Array.from(document.querySelectorAll('meta[name^=twitter:]')).map(e=>e.name+': '+e.content).join('\\n')")
                15->copyResearchV13("Meta keywords","(document.querySelector('meta[name=keywords]')||{}).content||''")
                16->copyResearchV13("Author","(document.querySelector('meta[name=author]')||{}).content||''")
                17->copyResearchV13("Publish date","(document.querySelector('meta[property=article:published_time],meta[name=date],time[datetime]')||{}).content||(document.querySelector('time[datetime]')||{}).dateTime||''")
                18->copyResearchV13("Modified date","(document.querySelector('meta[property=article:modified_time]')||{}).content||''")
                19->copyResearchV13("Robots","(document.querySelector('meta[name=robots]')||{}).content||''")
                20->copyResearchV13("Hreflang","Array.from(document.querySelectorAll('link[rel=alternate][hreflang]')).map(e=>e.hreflang+' '+e.href).join('\\n')")
                21->copyResearchV13("Stylesheets","Array.from(document.styleSheets).map(x=>x.href).filter(Boolean).join('\\n')")
                22->copyResearchV13("Scripts","Array.from(document.scripts).map(x=>x.src).filter(Boolean).join('\\n')")
                23->copyResearchV13("Iframes","Array.from(document.querySelectorAll('iframe')).map(x=>x.src).filter(Boolean).join('\\n')")
                24->copyResearchV13("Media sources","Array.from(new Set(Array.from(document.querySelectorAll('video,audio,source')).map(x=>x.currentSrc||x.src).filter(Boolean))).join('\\n')")
                25->copyResearchV13("Form actions","Array.from(document.forms).map((f,i)=>(i+1)+'. '+(f.action||location.href)+' ['+(f.method||'get')+']').join('\\n')")
                26->copyResearchV13("ARIA labels","Array.from(document.querySelectorAll('[aria-label]')).map(e=>e.tagName+' — '+e.getAttribute('aria-label')).join('\\n')")
                27->researchJsV13("document.querySelectorAll('a').forEach(a=>{try{if(new URL(a.href).host!==location.host)a.style.outline='2px solid #ff7a59'}catch(e){}})")
                28->researchJsV13("document.querySelectorAll('a[rel~=nofollow]').forEach(a=>a.style.outline='2px dashed #ffd166')")
                29->researchJsV13("document.querySelectorAll('form').forEach(e=>e.style.outline='3px solid #00d4ff')")
                30->researchJsV13("document.querySelectorAll('table').forEach(e=>e.style.outline='3px solid #9b5cff')")
                31->researchJsV13("document.querySelectorAll('pre,code').forEach(e=>e.style.outline='2px solid #00c853')")
                32->researchJsV13("document.querySelectorAll('blockquote').forEach(e=>e.style.outline='2px solid #ff4081')")
                33->researchJsV13("document.querySelectorAll('img').forEach(e=>{if(!e.hasAttribute('alt')||!e.alt)e.style.outline='4px solid red'})")
                34->researchJsV13("document.querySelectorAll('a[href]').forEach(a=>{if(!a.dataset.olikhHref){a.dataset.olikhHref='1';var x=document.createElement('small');x.className='olikh-v13-href';x.textContent=' ['+a.href+']';x.style.opacity='.65';a.after(x)}})")
                35->researchJsV13("document.querySelectorAll('.olikh-v13-href').forEach(e=>e.remove());document.querySelectorAll('*').forEach(e=>e.style.outline='')")
                36->selectedV12{q->megaCopy("OLIKH selection source",if(q.isBlank())"" else q+"\\n— "+webView.url.orEmpty(),"Selection + source copied")}
                37->{val t=webView.title.orEmpty();if(t.isNotBlank())createNewTab(initialUrl=buildSearchUrl(t))}
                38->selectedV12{q->if(q.isNotBlank())createNewTab(initialUrl=buildSearchUrl(q))}
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun copyResearchV13(label:String,expression:String){
        megaJs("(function(){try{return "+expression+"}catch(e){return ''}})();"){v->megaCopy("OLIKH "+label,v,label+" copied")}
    }

    private fun researchJsV13(code:String){
        webView.evaluateJavascript("(function(){try{"+code+";return true}catch(e){return false}})();",null)
        Toast.makeText(this,"Done",Toast.LENGTH_SHORT).show()
    }

    private fun researchStatsV13(){
        megaJs("(function(){var t=(document.body?document.body.innerText:'').trim();return 'Title: '+document.title+'\\nWords: '+((t.match(/\\\\S+/g)||[]).length)+'\\nLinks: '+document.links.length+'\\nImages: '+document.images.length+'\\nHeadings: '+document.querySelectorAll('h1,h2,h3,h4,h5,h6').length+'\\nForms: '+document.forms.length+'\\nTables: '+document.querySelectorAll('table').length+'\\nScripts: '+document.scripts.length+'\\nIframes: '+document.querySelectorAll('iframe').length;})();"){r->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Page statistics").setMessage(r).setPositiveButton("Copy"){_,_->megaCopy("OLIKH statistics",r,"Statistics copied")}.setNegativeButton("Close",null).show()
        }
    }

    private fun showProductivityToolsV12() {
        val options = arrayOf(
            "Reader mode","Copy page summary","Copy selected quote","Search selected text",
            "Translate current page","Wayback lookup","Copy Markdown link","Copy HTML link",
            "Copy citation","Extract emails","Extract social links","Extract video links",
            "Extract download links","List forms","List buttons","List inputs",
            "Highlight links","Highlight headings","Highlight images","Remove overlays",
            "Remove sticky elements","Pause animations","Resume animations","Focus reading column",
            "Print / Save PDF","Share selected quote + URL","Copy timestamp + URL","Open page twice"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Productivity tools")
            .setItems(options) { _, w ->
                when (w) {
                    0 -> readerModeV12()
                    1 -> copySummaryV12()
                    2 -> selectedV12 { megaCopy("OLIKH quote", it, "Quote copied") }
                    3 -> selectedV12 { if (it.isNotBlank()) createNewTab(initialUrl=buildSearchUrl(it)) }
                    4 -> translatePageV12()
                    5 -> externalLookupV12("https://web.archive.org/web/*/")
                    6 -> megaCopy("Markdown link","["+(webView.title?:"Page")+"]("+webView.url.orEmpty()+")","Markdown link copied")
                    7 -> megaCopy("HTML link","<a href=\""+webView.url.orEmpty()+"\">"+(webView.title?:"Page")+"</a>","HTML link copied")
                    8 -> megaCopy("Citation",(webView.title?:"Untitled")+" — "+webView.url.orEmpty(),"Citation copied")
                    9 -> extractV12("Emails","Array.from(new Set((document.body.innerText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/gi)||[]))).join('\\n')")
                    10 -> extractV12("Social links","Array.from(new Set(Array.from(document.links).map(a=>a.href).filter(h=>/instagram|facebook|twitter|x\\.com|linkedin|youtube|tiktok/i.test(h)))).join('\\n')")
                    11 -> extractV12("Video links","Array.from(new Set(Array.from(document.querySelectorAll('video,video source')).map(e=>e.currentSrc||e.src).filter(Boolean))).join('\\n')")
                    12 -> extractV12("Download links","Array.from(new Set(Array.from(document.links).filter(a=>a.download||/\\.(pdf|zip|apk|docx?|xlsx?|pptx?|mp3|mp4)(\\?|$)/i.test(a.href)).map(a=>a.href))).join('\\n')")
                    13 -> extractV12("Forms","Array.from(document.forms).map((f,i)=>(i+1)+'. '+(f.action||location.href)).join('\\n')")
                    14 -> extractV12("Buttons","Array.from(document.querySelectorAll('button,input[type=button],input[type=submit]')).map((e,i)=>(i+1)+'. '+(e.innerText||e.value||'Button')).join('\\n')")
                    15 -> extractV12("Inputs","Array.from(document.querySelectorAll('input,textarea,select')).map((e,i)=>(i+1)+'. '+e.tagName.toLowerCase()+' '+(e.name||e.id||e.type||'')).join('\\n')")
                    16 -> jsV12("document.querySelectorAll('a').forEach(e=>e.style.outline='2px solid #5B8CFF')")
                    17 -> jsV12("document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(e=>e.style.outline='2px solid #8B5CF6')")
                    18 -> jsV12("document.querySelectorAll('img').forEach(e=>e.style.outline='2px solid #22C55E')")
                    19 -> jsV12("document.querySelectorAll('[role=dialog],[aria-modal=true],.modal,.overlay,.popup').forEach(e=>e.remove());document.body.style.overflow='auto'")
                    20 -> jsV12("document.querySelectorAll('*').forEach(e=>{var p=getComputedStyle(e).position;if(p==='fixed'||p==='sticky')e.style.position='static'})")
                    21 -> jsV12("document.querySelectorAll('*').forEach(e=>{e.style.animationPlayState='paused';e.style.transition='none'})")
                    22 -> jsV12("document.querySelectorAll('*').forEach(e=>e.style.animationPlayState='running')")
                    23 -> jsV12("document.body.style.maxWidth='760px';document.body.style.margin='auto';document.body.style.padding='24px';document.body.style.lineHeight='1.7'")
                    24 -> savePageAsPdf()
                    25 -> selectedV12 { if(it.isNotBlank()) shareSimpleV10("\""+it+"\"\\n"+webView.url.orEmpty(),"Share quote") }
                    26 -> megaCopy("Timestamp URL",java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date())+" — "+webView.url.orEmpty(),"Timestamp + URL copied")
                    27 -> { val u=webView.url.orEmpty(); if(u.isNotBlank()){createNewTab(initialUrl=u);createNewTab(initialUrl=u)} }
                }
            }.setNegativeButton("Close",null).show()
    }

    private fun jsV12(code:String) {
        webView.evaluateJavascript("(function(){try{"+code+";return 'Done'}catch(e){return String(e)}})();",null)
        Toast.makeText(this,"Done",Toast.LENGTH_SHORT).show()
    }

    private fun selectedV12(done:(String)->Unit) {
        megaJs("(function(){return window.getSelection().toString();})();",done)
    }

    private fun extractV12(title:String, expression:String) {
        megaJs("(function(){return "+expression+";})();") { value ->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title)
                .setMessage(value.ifBlank{"Nothing found"})
                .setPositiveButton("Copy"){_,_->megaCopy("OLIKH "+title,value,title+" copied")}
                .setNegativeButton("Close",null).show()
        }
    }

    private fun readerModeV12() {
        jsV12("var a=document.querySelector('article,main,[role=main]')||document.body;document.body.innerHTML='<main id=\"olikhReader\">'+a.innerHTML+'</main>';var r=document.getElementById('olikhReader');r.style.maxWidth='760px';r.style.margin='auto';r.style.padding='28px 22px';r.style.fontSize='18px';r.style.lineHeight='1.75';document.body.style.background='#10131a';document.body.style.color='#f4f6fb';document.querySelectorAll('script,nav,aside,footer,iframe').forEach(e=>e.remove())")
    }

    private fun copySummaryV12() {
        megaJs("(function(){var t=(document.body?document.body.innerText:'').replace(/\\s+/g,' ').trim();return t.substring(0,1200);})();") {
            megaCopy("OLIKH summary",it,"Page summary copied")
        }
    }

    private fun translatePageV12() {
        val u=webView.url.orEmpty()
        if(u.isNotBlank()) createNewTab(initialUrl="https://translate.google.com/translate?sl=auto&tl=en&u="+Uri.encode(u))
    }

    private fun externalLookupV12(prefix:String) {
        val u=webView.url.orEmpty()
        if(u.isNotBlank()) createNewTab(initialUrl=prefix+u)
    }

    private val v11Prefs by lazy {
        getSharedPreferences("olikh_v11_systems", MODE_PRIVATE)
    }

    private fun showBrowserSystemsV11() {
        val options = arrayOf(
            "Site controls",
            "Cookie controls",
            "Privacy controls",
            "Session controls",
            "Media controls",
            "Accessibility",
            "Security report",
            "Tab tools",
            "Storage tools"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Browser systems")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSiteControlsV11()
                    1 -> showCookieControlsV11()
                    2 -> showPrivacyControlsV11()
                    3 -> showSessionControlsV11()
                    4 -> showMediaControlsV11()
                    5 -> showAccessibilityV11()
                    6 -> showSecurityReportV11()
                    7 -> showTabToolsV11()
                    8 -> showStorageToolsV11()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSiteControlsV11() {
        val labels = arrayOf("JavaScript","Images","DOM storage","Database storage","Geolocation","Multiple windows")
        val values = booleanArrayOf(
            webView.settings.javaScriptEnabled,
            webView.settings.loadsImagesAutomatically && !webView.settings.blockNetworkImage,
            webView.settings.domStorageEnabled,
            true,
            true,
            webView.settings.supportMultipleWindows()
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Site controls")
            .setMultiChoiceItems(labels, values) { _, which, checked ->
                when (which) {
                    0 -> webView.settings.javaScriptEnabled = checked
                    1 -> { webView.settings.loadsImagesAutomatically = checked; webView.settings.blockNetworkImage = !checked }
                    2 -> webView.settings.domStorageEnabled = checked
                    3 -> webView.settings.databaseEnabled = checked
                    4 -> webView.settings.setGeolocationEnabled(checked)
                    5 -> webView.settings.setSupportMultipleWindows(checked)
                }
            }
            .setPositiveButton("Reload") { _, _ -> webView.reload() }
            .setNegativeButton("Close", null).show()
    }

    private fun showCookieControlsV11() {
        val cm = android.webkit.CookieManager.getInstance()
        val options = arrayOf("Toggle cookies","Toggle third-party cookies","Clear session cookies","Clear all cookies","Show current-site cookies")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cookie controls")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { val n=!cm.acceptCookie(); cm.setAcceptCookie(n); Toast.makeText(this,"Cookies: $n",Toast.LENGTH_SHORT).show() }
                    1 -> { val n=!v11Prefs.getBoolean("third_party",true); cm.setAcceptThirdPartyCookies(webView,n); v11Prefs.edit().putBoolean("third_party",n).apply(); Toast.makeText(this,"Third-party cookies: $n",Toast.LENGTH_SHORT).show() }
                    2 -> cm.removeSessionCookies { Toast.makeText(this,"Session cookies cleared",Toast.LENGTH_SHORT).show() }
                    3 -> cm.removeAllCookies { cm.flush(); Toast.makeText(this,"All cookies cleared",Toast.LENGTH_SHORT).show() }
                    4 -> androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Current-site cookies").setMessage(cm.getCookie(webView.url.orEmpty()).orEmpty().ifBlank{"No cookies found"}).setPositiveButton("Close",null).show()
                }
            }.setNegativeButton("Close",null).show()
    }

    private fun showPrivacyControlsV11() {
        val options=arrayOf("Clear cache","Clear history","Clear SSL preferences","Clear cookies + cache","Clear form data")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Privacy controls").setItems(options){_,w->
            when(w){
                0->webView.clearCache(true)
                1->webView.clearHistory()
                2->webView.clearSslPreferences()
                3->{webView.clearCache(true);android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.CookieManager.getInstance().flush()}
                4->webView.clearFormData()
            }
            Toast.makeText(this,"Done",Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Close",null).show()
    }

    private fun showSessionControlsV11() {
        val options=arrayOf("Save current tab session","Restore saved session","Clear saved session","Duplicate all open tabs")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Session controls").setItems(options){_,w->
            when(w){
                0->{val u=listOfNotNull(webView.url).filter{it.isNotBlank()};v11Prefs.edit().putString("session",u.joinToString("\n")).apply();Toast.makeText(this,"${u.size} tab saved",Toast.LENGTH_SHORT).show()}
                1->{val u=v11Prefs.getString("session","").orEmpty().lines().filter{it.isNotBlank()};u.forEach{createNewTab(initialUrl=it)};Toast.makeText(this,"${u.size} tabs restored",Toast.LENGTH_SHORT).show()}
                2->v11Prefs.edit().remove("session").apply()
                3->{val u=webView.url.orEmpty();if(u.isNotBlank())createNewTab(initialUrl=u)}
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun showMediaControlsV11() {
        val options=arrayOf("Play media","Pause media","Mute media","Unmute media","Speed 0.75x","Speed 1x","Speed 1.25x","Speed 1.5x","Speed 2x")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Media controls").setItems(options){_,w->
            val js=when(w){
                0->"document.querySelectorAll('video,audio').forEach(e=>e.play())"
                1->"document.querySelectorAll('video,audio').forEach(e=>e.pause())"
                2->"document.querySelectorAll('video,audio').forEach(e=>e.muted=true)"
                3->"document.querySelectorAll('video,audio').forEach(e=>e.muted=false)"
                4->"document.querySelectorAll('video,audio').forEach(e=>e.playbackRate=.75)"
                5->"document.querySelectorAll('video,audio').forEach(e=>e.playbackRate=1)"
                6->"document.querySelectorAll('video,audio').forEach(e=>e.playbackRate=1.25)"
                7->"document.querySelectorAll('video,audio').forEach(e=>e.playbackRate=1.5)"
                else->"document.querySelectorAll('video,audio').forEach(e=>e.playbackRate=2)"
            }
            webView.evaluateJavascript(js,null)
        }.setNegativeButton("Close",null).show()
    }

    private fun showAccessibilityV11() {
        val options=arrayOf("Text 80%","Text 100%","Text 120%","Text 150%","High contrast","Grayscale","Reset filters")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Accessibility").setItems(options){_,w->
            when(w){
                0->webView.settings.textZoom=80
                1->webView.settings.textZoom=100
                2->webView.settings.textZoom=120
                3->webView.settings.textZoom=150
                4->pageFilterV10("contrast(1.35)")
                5->pageFilterV10("grayscale(1)")
                6->pageFilterV10("none")
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun showSecurityReportV11() {
        val u=runCatching{Uri.parse(webView.url.orEmpty())}.getOrNull()
        val report="Host: ${u?.host.orEmpty()}\nScheme: ${u?.scheme.orEmpty()}\nHTTPS: ${u?.scheme.equals("https",true)}\nJavaScript: ${webView.settings.javaScriptEnabled}\nDOM storage: ${webView.settings.domStorageEnabled}\nIncognito: ${activeTab?.incognito==true}\nUser agent: ${webView.settings.userAgentString.orEmpty()}"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Security report").setMessage(report).setPositiveButton("Copy"){_,_->megaCopy("OLIKH security",report,"Security report copied")}.setNegativeButton("Close",null).show()
    }

    private fun showTabToolsV11() {
        val options=arrayOf("New tab","New incognito tab","Duplicate current tab","Close current tab","Reopen closed tab","Tab manager","Save session","Restore session")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Tab tools").setItems(options){_,w->
            when(w){
                0->createNewTab()
                1->createNewTab(incognito=true)
                2->duplicateCurrentTab()
                3->closeCurrentTab()
                4->reopenLastClosedTab()
                5->showTabManager()
                6->{val u=listOfNotNull(webView.url).filter{it.isNotBlank()};v11Prefs.edit().putString("session",u.joinToString("\n")).apply()}
                7->v11Prefs.getString("session","").orEmpty().lines().filter{it.isNotBlank()}.forEach{createNewTab(initialUrl=it)}
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun showStorageToolsV11() {
        val options=arrayOf("Clear cache","Clear history","Clear cookies","Clear form data","Clear SSL preferences","Clear localStorage","Clear sessionStorage")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Storage tools").setItems(options){_,w->
            when(w){
                0->webView.clearCache(true)
                1->webView.clearHistory()
                2->android.webkit.CookieManager.getInstance().removeAllCookies(null)
                3->webView.clearFormData()
                4->webView.clearSslPreferences()
                5->webView.evaluateJavascript("localStorage.clear();",null)
                6->webView.evaluateJavascript("sessionStorage.clear();",null)
            }
            Toast.makeText(this,"Done",Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Close",null).show()
    }

    private fun animateDialogEntrance(
        dialog: androidx.appcompat.app.AlertDialog
    ) {
        val decor = dialog.window?.decorView ?: return

        decor.alpha = 0f
        decor.scaleX = 0.94f
        decor.scaleY = 0.94f
        decor.translationY = 28f

        decor.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun showPageToolsMenu() {
        val options = arrayOf(
            "Page info",
            "Open in external app",
            "Save as PDF",
            "Zoom",
            "View page source",
            "Save page offline",
            "Refresh page",
            "Copy page title",
            "Copy link as text",
            "Open current page in new tab",
            "Select all text",
            "Stop loading",
            "Open homepage in new tab",
            "Open start page in new tab",
            "Share title + URL",
            "Copy host name",
            "Copy page HTML",
            "Copy selected text",
            "Scroll up",
            "Scroll down",
            "Go to middle",
            "Reload without cache",
            "Open HTTP version",
            "Open HTTPS version",
            "Search page title",
            "Copy domain + path",
            "Copy origin",
            "Copy page text",
            "Copy meta description",
            "Copy canonical URL",
            "Copy user agent",
            "Scroll one screen up",
            "Scroll one screen down",
            "Jump to 25%",
            "Jump to 75%",
            "Zoom 100%",
            "Zoom 125%",
            "Zoom 150%",
            "Disable page images",
            "Enable page images",
            "Disable JavaScript",
            "Enable JavaScript",
            "Clear page cache",
            "Clear session cookies",
            "Open domain root",
            "Search selected text",
            "Search host name",
            "Share selected text",
            "Copy clean URL",
            "Copy query string",
            "Copy fragment",
            "Copy page language",
            "Copy charset",
            "Copy viewport size",
            "Copy scroll position",
            "Copy link count",
            "Copy image count",
            "Copy heading count",
            "Copy word count",
            "Copy first H1",
            "Copy all headings",
            "Copy all links",
            "Copy all image URLs",
            "Copy favicon URL",
            "Copy referrer",
            "Copy page dimensions",
            "Search current URL",
            "Open in incognito tab",
            "Open domain root in new tab",
            "Jump to 10%",
            "Jump to 50%",
            "Jump to 90%",
            "Zoom 75%",
            "Zoom 90%",
            "Zoom 110%",
            "Zoom 175%",
            "Zoom 200%",
            "Invert page colors",
            "Grayscale page",
            "Reset page filters",
            "Hide page images",
            "Show page images",
            "Increase page text",
            "Decrease page text",
            "Reset page text",
            "Share URL only",
            "Share title only"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Page tools")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPageInfo()
                    1 -> openInExternalApp()
                    2 -> savePageAsPdf()
                    3 -> showZoomMenu()
                    4 -> viewPageSource()
                    5 -> saveWebArchive()
                    6 -> refreshCurrentPage()
                    7 -> copyCurrentPageTitle()
                    8 -> copyCurrentLinkAsText()
                    9 -> openCurrentPageInNewTab()
                    10 -> selectAllPageText()
                    11 -> stopCurrentPageLoading()
                    12 -> createNewTab(initialUrl = homePage)
                    13 -> createNewTab(initialUrl = "about:blank")
                    14 -> sharePageTitleAndUrl()
                    15 -> copyCurrentHostName()
                    16 -> copyCurrentPageHtml()
                    17 -> copySelectedPageText()
                    18 -> scrollPageBy(-600)
                    19 -> scrollPageBy(600)
                    20 -> goToPageMiddle()
                    21 -> reloadWithoutCache()
                    22 -> openCurrentScheme("http")
                    23 -> openCurrentScheme("https")
                    24 -> searchCurrentPageTitle()
                    25 -> copyDomainAndPath()
                    26 -> copyCurrentOrigin()
                    27 -> copyPagePlainText()
                    28 -> copyMetaDescription()
                    29 -> copyCanonicalUrl()
                    30 -> copyCurrentUserAgent()
                    31 -> scrollViewport(-1)
                    32 -> scrollViewport(1)
                    33 -> jumpPagePercent(25)
                    34 -> jumpPagePercent(75)
                    35 -> setQuickZoom(100)
                    36 -> setQuickZoom(125)
                    37 -> setQuickZoom(150)
                    38 -> togglePageImages(false)
                    39 -> togglePageImages(true)
                    40 -> togglePageJavaScript(false)
                    41 -> togglePageJavaScript(true)
                    42 -> clearPageCacheQuick()
                    43 -> clearSessionCookiesQuick()
                    44 -> openDomainRoot()
                    45 -> searchSelectedTextQuick()
                    46 -> searchHostQuick()
                    47 -> shareSelectedTextQuick()
                    48 -> copyCleanUrlV10()
                    49 -> copyQueryV10()
                    50 -> copyFragmentV10()
                    51 -> copyJsV10("document.documentElement.lang||''", "Page language")
                    52 -> copyJsV10("document.characterSet||''", "Charset")
                    53 -> copyJsV10("'width='+window.innerWidth+', height='+window.innerHeight", "Viewport")
                    54 -> copyJsV10("'x='+window.scrollX+', y='+window.scrollY", "Scroll position")
                    55 -> copyJsV10("String(document.links.length)", "Link count")
                    56 -> copyJsV10("String(document.images.length)", "Image count")
                    57 -> copyJsV10("String(document.querySelectorAll('h1,h2,h3,h4,h5,h6').length)", "Heading count")
                    58 -> copyJsV10("String((document.body.innerText.match(/\\S+/g)||[]).length)", "Word count")
                    59 -> copyJsV10("(document.querySelector('h1')||{}).innerText||''", "First H1")
                    60 -> copyJsV10("Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6')).map(e=>e.innerText).join('\\n')", "Headings")
                    61 -> copyJsV10("Array.from(document.links).map(e=>e.href).join('\\n')", "Links")
                    62 -> copyJsV10("Array.from(document.images).map(e=>e.src).join('\\n')", "Image URLs")
                    63 -> copyJsV10("(document.querySelector('link[rel*=icon]')||{}).href||''", "Favicon")
                    64 -> copyJsV10("document.referrer||''", "Referrer")
                    65 -> copyJsV10("'width='+document.documentElement.scrollWidth+', height='+document.documentElement.scrollHeight", "Page dimensions")
                    66 -> searchUrlV10()
                    67 -> openIncognitoV10()
                    68 -> openRootNewTabV10()
                    69 -> jumpPagePercent(10)
                    70 -> jumpPagePercent(50)
                    71 -> jumpPagePercent(90)
                    72 -> setQuickZoom(75)
                    73 -> setQuickZoom(90)
                    74 -> setQuickZoom(110)
                    75 -> setQuickZoom(175)
                    76 -> setQuickZoom(200)
                    77 -> pageFilterV10("invert(1) hue-rotate(180deg)")
                    78 -> pageFilterV10("grayscale(1)")
                    79 -> pageFilterV10("none")
                    80 -> imageDisplayV10(false)
                    81 -> imageDisplayV10(true)
                    82 -> textScaleV10(1.15)
                    83 -> textScaleV10(0.90)
                    84 -> textResetV10()
                    85 -> shareSimpleV10(webView.url.orEmpty(), "Share URL")
                    86 -> shareSimpleV10(webView.title.orEmpty(), "Share title")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun selectAllPageText() {
        webView.evaluateJavascript(
            "(function(){var s=window.getSelection();var r=document.createRange();r.selectNodeContents(document.body);s.removeAllRanges();s.addRange(r);})();",
            null
        )
    }

    private fun stopCurrentPageLoading() {
        webView.stopLoading()
        progressBar.visibility = View.GONE
        Toast.makeText(this, "Loading stopped", Toast.LENGTH_SHORT).show()
    }

    private fun sharePageTitleAndUrl() {
        val url = webView.url.orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val pageTitle = webView.title?.trim().orEmpty().ifBlank { "OLIKH" }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, pageTitle)
            putExtra(Intent.EXTRA_TEXT, pageTitle + "\n" + url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share page"))
    }

    private fun copyCurrentHostName() {
        val host = runCatching { Uri.parse(webView.url.orEmpty()).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OLIKH host", host))
        Toast.makeText(this, "Host copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyCurrentPageHtml() {
        webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { result ->
            val text = runCatching { org.json.JSONArray("[" + result + "]").getString(0) }.getOrDefault("")
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OLIKH HTML", text))
                Toast.makeText(this, "Page HTML copied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copySelectedPageText() {
        webView.evaluateJavascript("(function(){return window.getSelection().toString();})();") { result ->
            val text = runCatching { org.json.JSONArray("[" + result + "]").getString(0) }.getOrDefault("")
            if (text.isBlank()) {
                Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OLIKH selection", text))
                Toast.makeText(this, "Selected text copied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrollPageBy(amount: Int) {
        webView.evaluateJavascript("window.scrollBy({top:" + amount + ",left:0,behavior:'smooth'});", null)
    }

    private fun goToPageMiddle() {
        webView.evaluateJavascript("window.scrollTo({top:document.documentElement.scrollHeight/2,left:0,behavior:'smooth'});", null)
    }

    private fun reloadWithoutCache() {
        webView.clearCache(false)
        webView.reload()
    }

    private fun openCurrentScheme(scheme: String) {
        val uri = runCatching { Uri.parse(webView.url.orEmpty()) }.getOrNull()
        if (uri?.host.isNullOrBlank()) return
        webView.loadUrl(uri!!.buildUpon().scheme(scheme).build().toString())
    }

    private fun searchCurrentPageTitle() {
        val pageTitle = webView.title?.trim().orEmpty()
        if (pageTitle.isNotBlank()) webView.loadUrl(buildSearchUrl(pageTitle))
    }

    private fun megaCopy(label: String, text: String, message: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "Nothing available", Toast.LENGTH_SHORT).show()
            return
        }
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun megaJs(script: String, done: (String) -> Unit) {
        webView.evaluateJavascript(script) { result ->
            val text = runCatching {
                if (result == "null") "" else org.json.JSONArray("[" + result + "]").getString(0)
            }.getOrDefault("")
            done(text)
        }
    }

    private fun copyDomainAndPath() {
        val u = runCatching { Uri.parse(webView.url.orEmpty()) }.getOrNull()
        megaCopy("OLIKH domain path", if (u?.host == null) "" else u.host.orEmpty() + u.encodedPath.orEmpty(), "Domain + path copied")
    }

    private fun copyCurrentOrigin() {
        val u = runCatching { Uri.parse(webView.url.orEmpty()) }.getOrNull()
        megaCopy("OLIKH origin", if (u?.host == null) "" else u.scheme.orEmpty() + "://" + u.host.orEmpty(), "Origin copied")
    }

    private fun copyPagePlainText() {
        megaJs("(function(){return document.body?document.body.innerText:'';})();") {
            megaCopy("OLIKH page text", it, "Page text copied")
        }
    }

    private fun copyMetaDescription() {
        megaJs("(function(){var e=document.querySelector('meta[name=description]');return e?e.content:'';})();") {
            megaCopy("OLIKH description", it, "Description copied")
        }
    }

    private fun copyCanonicalUrl() {
        megaJs("(function(){var e=document.querySelector('link[rel=canonical]');return e?e.href:'';})();") {
            megaCopy("OLIKH canonical", it, "Canonical URL copied")
        }
    }

    private fun copyCurrentUserAgent() {
        megaCopy("OLIKH user agent", webView.settings.userAgentString.orEmpty(), "User agent copied")
    }

    private fun scrollViewport(direction: Int) {
        webView.evaluateJavascript("window.scrollBy({top:window.innerHeight*" + direction + ",behavior:'smooth'});", null)
    }

    private fun jumpPagePercent(percent: Int) {
        webView.evaluateJavascript("window.scrollTo({top:document.documentElement.scrollHeight*" + percent + "/100,behavior:'smooth'});", null)
    }

    private fun setQuickZoom(percent: Int) {
        webView.setInitialScale(percent)
        Toast.makeText(this, "Zoom " + percent + "%", Toast.LENGTH_SHORT).show()
    }

    private fun togglePageImages(enabled: Boolean) {
        webView.settings.loadsImagesAutomatically = enabled
        webView.settings.blockNetworkImage = !enabled
        if (enabled) webView.reload()
        Toast.makeText(this, if (enabled) "Images enabled" else "Images disabled", Toast.LENGTH_SHORT).show()
    }

    private fun togglePageJavaScript(enabled: Boolean) {
        webView.settings.javaScriptEnabled = enabled
        webView.reload()
        Toast.makeText(this, if (enabled) "JavaScript enabled" else "JavaScript disabled", Toast.LENGTH_SHORT).show()
    }

    private fun clearPageCacheQuick() {
        webView.clearCache(false)
        Toast.makeText(this, "Page cache cleared", Toast.LENGTH_SHORT).show()
    }

    private fun clearSessionCookiesQuick() {
        android.webkit.CookieManager.getInstance().removeSessionCookies {
            Toast.makeText(this, "Session cookies cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDomainRoot() {
        val u = runCatching { Uri.parse(webView.url.orEmpty()) }.getOrNull() ?: return
        if (u.host.isNullOrBlank()) return
        webView.loadUrl(u.scheme.orEmpty() + "://" + u.host.orEmpty() + "/")
    }

    private fun searchSelectedTextQuick() {
        megaJs("(function(){return window.getSelection().toString();})();") {
            if (it.isNotBlank()) createNewTab(initialUrl = buildSearchUrl(it))
            else Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchHostQuick() {
        val host = runCatching { Uri.parse(webView.url.orEmpty()).host.orEmpty() }.getOrDefault("")
        if (host.isNotBlank()) createNewTab(initialUrl = buildSearchUrl(host))
    }

    private fun shareSelectedTextQuick() {
        megaJs("(function(){return window.getSelection().toString();})();") { text ->
            if (text.isBlank()) {
                Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(intent, "Share selected text"))
            }
        }
    }

    private fun copyJsV10(expression: String, label: String) {
        megaJs("(function(){return " + expression + ";})();") {
            megaCopy("OLIKH " + label, it, label + " copied")
        }
    }

    private fun copyCleanUrlV10() {
        val u = runCatching { Uri.parse(webView.url.orEmpty()) }.getOrNull() ?: return
        megaCopy("OLIKH clean URL", u.buildUpon().clearQuery().fragment(null).build().toString(), "Clean URL copied")
    }

    private fun copyQueryV10() {
        megaCopy("OLIKH query", runCatching { Uri.parse(webView.url.orEmpty()).encodedQuery.orEmpty() }.getOrDefault(""), "Query copied")
    }

    private fun copyFragmentV10() {
        megaCopy("OLIKH fragment", runCatching { Uri.parse(webView.url.orEmpty()).fragment.orEmpty() }.getOrDefault(""), "Fragment copied")
    }

    private fun searchUrlV10() {
        val value = webView.url.orEmpty()
        if (value.isNotBlank()) createNewTab(initialUrl = buildSearchUrl(value))
    }

    private fun openIncognitoV10() {
        createNewTab(incognito = true, initialUrl = webView.url.orEmpty().ifBlank { "about:blank" })
    }

    private fun openRootNewTabV10() {
        val u = runCatching { Uri.parse(webView.url.orEmpty()) }.getOrNull() ?: return
        if (u.host.isNullOrBlank()) return
        createNewTab(initialUrl = u.scheme.orEmpty() + "://" + u.host.orEmpty() + "/")
    }

    private fun pageFilterV10(filter: String) {
        webView.evaluateJavascript("document.documentElement.style.filter='" + filter + "';", null)
    }

    private fun imageDisplayV10(show: Boolean) {
        val display = if (show) "" else "none"
        webView.evaluateJavascript("Array.from(document.images).forEach(function(i){i.style.display='" + display + "';});", null)
    }

    private fun textScaleV10(factor: Double) {
        webView.evaluateJavascript("(function(){var b=document.body;var n=parseFloat(b.dataset.olikhScale||'1');n=n*" + factor + ";b.dataset.olikhScale=String(n);b.style.fontSize=(n*100)+'%';})();", null)
    }

    private fun textResetV10() {
        webView.evaluateJavascript("(function(){document.body.dataset.olikhScale='1';document.body.style.fontSize='';})();", null)
    }

    private fun shareSimpleV10(text: String, title: String) {
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun showPageInfo() {
        val url = webView.url ?: "No URL"
        val title = webView.title ?: "Untitled"
        val uri = runCatching { Uri.parse(url) }.getOrNull()

        val host = uri?.host ?: "Unknown"
        val scheme = uri?.scheme ?: "Unknown"

        val security = when (scheme.lowercase()) {
            "https" -> "Secure connection (HTTPS)"
            "http" -> "Not encrypted (HTTP)"
            else -> scheme
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Page info")
            .setMessage(
                "Title: $title\n\n" +
                    "Site: $host\n\n" +
                    "Connection: $security\n\n" +
                    "URL:\n$url"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun openInExternalApp() {
        val url = webView.url
            ?.takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }
            ?: run {
                Toast.makeText(
                    this,
                    "No external page to open",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        runCatching {
            startActivity(
                Intent.createChooser(
                    intent,
                    "Open with"
                )
            )
        }.onFailure {
            Toast.makeText(
                this,
                "No app can open this page",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun savePageAsPdf() {
        val manager =
            getSystemService(Context.PRINT_SERVICE) as PrintManager

        val title = webView.title
            ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "OLIKH page"

        val adapter = webView.createPrintDocumentAdapter(title)

        manager.print(
            title,
            adapter,
            PrintAttributes.Builder().build()
        )
    }

    private fun showSettings() {
        val options = arrayOf(
            "Search engine",
            "Homepage",
            "Quick access",
            "JavaScript",
            "Privacy & security",
            "Web page settings",
            "Advanced browsing",
            "Reading & display"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSearchEngineSelector()
                    1 -> showHomepageSettings()
                    2 -> showQuickAccessManager()
                    3 -> showJavaScriptSetting()
                    4 -> showPrivacySecuritySettings()
                    5 -> showWebPageSettings()
                    6 -> showAdvancedBrowsingSettings()
                    7 -> showReadingDisplaySettings()
                }
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showQuickAccessManager() {
        val items = getQuickAccessItems()

        val labels =
            items.mapIndexed { index, item ->
                "${index + 1}. ${item.name}\n${item.url}"
            }.toMutableList()

        labels.add("＋ Add shortcut")
        labels.add("↺ Reset defaults")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Quick access")
            .setItems(labels.toTypedArray()) { _, which ->

                when {
                    which < items.size -> {
                        showQuickAccessItemActions(which)
                    }

                    which == items.size -> {
                        showQuickAccessEditor(null)
                    }

                    else -> {
                        confirmResetQuickAccess()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showQuickAccessItemActions(
        index: Int
    ) {
        val items = getQuickAccessItems()
        val item = items.getOrNull(index) ?: return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(
                arrayOf(
                    "Edit",
                    "Delete",
                    "Open"
                )
            ) { _, which ->

                when (which) {
                    0 -> showQuickAccessEditor(index)

                    1 -> confirmDeleteQuickAccess(index)

                    2 -> webView.loadUrl(item.url)
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showQuickAccessManager()
            }
            .show()
    }

    private fun showQuickAccessEditor(
        editIndex: Int?
    ) {
        val items = getQuickAccessItems()
        val existing =
            editIndex?.let { items.getOrNull(it) }

        val container =
            android.widget.LinearLayout(this).apply {
                orientation =
                    android.widget.LinearLayout.VERTICAL

                val padding =
                    (20 * resources.displayMetrics.density)
                        .toInt()

                setPadding(
                    padding,
                    padding / 2,
                    padding,
                    0
                )
            }

        val nameInput = EditText(this).apply {
            hint = "Name"
            setSingleLine(true)
            setText(existing?.name.orEmpty())
        }

        val urlInput = EditText(this).apply {
            hint = "Website URL"
            setSingleLine(true)
            setText(existing?.url.orEmpty())
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        container.addView(nameInput)
        container.addView(urlInput)

        val dialog =
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(
                    if (existing == null) {
                        "Add shortcut"
                    } else {
                        "Edit shortcut"
                    }
                )
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val name =
                    nameInput.text.toString().trim()

                val rawUrl =
                    urlInput.text.toString().trim()

                if (name.isBlank()) {
                    nameInput.error = "Enter a name"
                    return@setOnClickListener
                }

                if (rawUrl.isBlank()) {
                    urlInput.error = "Enter a website"
                    return@setOnClickListener
                }

                val url =
                    normalizeQuickAccessUrl(rawUrl)

                if (existing == null) {
                    items.add(
                        QuickAccessItem(
                            name = name,
                            url = url
                        )
                    )
                } else {
                    val index =
                        editIndex ?: return@setOnClickListener

                    if (index !in items.indices) {
                        return@setOnClickListener
                    }

                    items[index] =
                        QuickAccessItem(
                            name = name,
                            url = url
                        )
                }

                saveQuickAccessItems(items)

                if (isOlikhStartPageUrl(webView.url)) {
                    showOlikhStartPage()
                }

                dialog.dismiss()
                showQuickAccessManager()
            }
        }

        dialog.show()
    }

    private fun confirmDeleteQuickAccess(
        index: Int
    ) {
        val items = getQuickAccessItems()
        val item = items.getOrNull(index) ?: return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete shortcut?")
            .setMessage(
                "${item.name}\n${item.url}"
            )
            .setNegativeButton("Cancel") { _, _ ->
                showQuickAccessManager()
            }
            .setPositiveButton("Delete") { _, _ ->

                items.removeAt(index)
                saveQuickAccessItems(items)

                if (isOlikhStartPageUrl(webView.url)) {
                    showOlikhStartPage()
                }

                showQuickAccessManager()
            }
            .show()
    }

    private fun confirmResetQuickAccess() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reset Quick access?")
            .setMessage(
                "Google, YouTube, Wikipedia and GitHub will be restored."
            )
            .setNegativeButton("Cancel") { _, _ ->
                showQuickAccessManager()
            }
            .setPositiveButton("Reset") { _, _ ->

                saveQuickAccessItems(
                    defaultQuickAccessItems()
                )

                if (isOlikhStartPageUrl(webView.url)) {
                    showOlikhStartPage()
                }

                showQuickAccessManager()
            }
            .show()
    }

    private fun cameraPermissionEnabled(): Boolean {
        return browserPrefs.getBoolean(
            "site_camera_enabled",
            true
        )
    }

    private fun microphonePermissionEnabled(): Boolean {
        return browserPrefs.getBoolean(
            "site_microphone_enabled",
            true
        )
    }

    private fun locationPermissionEnabled(): Boolean {
        return browserPrefs.getBoolean(
            "site_location_enabled",
            true
        )
    }

    private fun setCameraPermissionEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("site_camera_enabled", enabled)
            .apply()

        settingToast("Camera access", enabled)
    }

    private fun setMicrophonePermissionEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("site_microphone_enabled", enabled)
            .apply()

        settingToast("Microphone access", enabled)
    }

    private fun setLocationPermissionEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("site_location_enabled", enabled)
            .apply()

        settingToast("Location access", enabled)
    }

    private fun handleWebPermissionRequest(
        request: PermissionRequest
    ) {
        val requested = request.resources ?: emptyArray()

        val supported = requested.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }

        if (supported.isEmpty()) {
            request.deny()
            return
        }

        val origin = request.origin?.toString()

        if (origin.isNullOrBlank()) {
            request.deny()
            return
        }

        val wantsCamera =
            supported.contains(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE
            )

        val wantsMic =
            supported.contains(
                PermissionRequest.RESOURCE_AUDIO_CAPTURE
            )

        if (wantsCamera && !cameraPermissionEnabled()) {
            request.deny()
            return
        }

        if (wantsMic && !microphonePermissionEnabled()) {
            request.deny()
            return
        }

        val decisions = supported.map { resource ->
            sitePermissionManager.getDecision(
                origin,
                resource
            )
        }

        if (
            decisions.any {
                it == SitePermissionManager.Decision.BLOCK
            }
        ) {
            request.deny()
            return
        }

        fun continueWithAndroidPermission() {
            val androidPermissions = mutableListOf<String>()

            if (
                wantsCamera &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                androidPermissions += Manifest.permission.CAMERA
            }

            if (
                wantsMic &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                androidPermissions += Manifest.permission.RECORD_AUDIO
            }

            if (androidPermissions.isEmpty()) {
                request.grant(supported.toTypedArray())
                return
            }

            pendingWebPermissionRequest?.deny()
            pendingWebPermissionRequest = request

            ActivityCompat.requestPermissions(
                this,
                androidPermissions.toTypedArray(),
                webPermissionRequestCode
            )
        }

        if (
            decisions.all {
                it == SitePermissionManager.Decision.ALLOW
            }
        ) {
            continueWithAndroidPermission()
            return
        }

        val host = runCatching {
            Uri.parse(origin).host
        }.getOrNull() ?: origin

        val requestedNames = buildList {
            if (wantsCamera) add("Camera")
            if (wantsMic) add("Microphone")
        }.joinToString(" & ")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$requestedNames permission")
            .setMessage(
                "$host wants to use $requestedNames. " +
                    "Allow and remember this choice for this site?"
            )
            .setPositiveButton("Allow") { _, _ ->
                supported.forEach { resource ->
                    sitePermissionManager.setDecision(
                        origin,
                        resource,
                        SitePermissionManager.Decision.ALLOW
                    )
                }

                continueWithAndroidPermission()
            }
            .setNegativeButton("Block") { _, _ ->
                supported.forEach { resource ->
                    sitePermissionManager.setDecision(
                        origin,
                        resource,
                        SitePermissionManager.Decision.BLOCK
                    )
                }

                request.deny()
            }
            .setNeutralButton("Cancel") { _, _ ->
                request.deny()
            }
            .setOnCancelListener {
                request.deny()
            }
            .show()
    }

    private fun handleGeolocationRequest(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (origin.isNullOrBlank() || callback == null) return

        if (!locationPermissionEnabled()) {
            callback.invoke(origin, false, false)
            return
        }

        val decision = sitePermissionManager.getDecision(
            origin,
            "location"
        )

        if (decision == SitePermissionManager.Decision.BLOCK) {
            callback.invoke(origin, false, true)
            return
        }

        fun continueWithAndroidPermission() {
            val fineGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            val coarseGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            if (fineGranted || coarseGranted) {
                callback.invoke(origin, true, true)
                return
            }

            pendingLocationCallback?.let { oldCallback ->
                pendingLocationOrigin?.let { oldOrigin ->
                    oldCallback.invoke(oldOrigin, false, false)
                }
            }

            pendingLocationOrigin = origin
            pendingLocationCallback = callback

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationPermissionRequestCode
            )
        }

        if (decision == SitePermissionManager.Decision.ALLOW) {
            continueWithAndroidPermission()
            return
        }

        val host = runCatching {
            Uri.parse(origin).host
        }.getOrNull() ?: origin

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Location permission")
            .setMessage(
                "$host wants to access your location. " +
                    "Allow and remember this choice for this site?"
            )
            .setPositiveButton("Allow") { _, _ ->
                sitePermissionManager.setDecision(
                    origin,
                    "location",
                    SitePermissionManager.Decision.ALLOW
                )

                continueWithAndroidPermission()
            }
            .setNegativeButton("Block") { _, _ ->
                sitePermissionManager.setDecision(
                    origin,
                    "location",
                    SitePermissionManager.Decision.BLOCK
                )

                callback.invoke(origin, false, true)
            }
            .setNeutralButton("Cancel") { _, _ ->
                callback.invoke(origin, false, false)
            }
            .setOnCancelListener {
                callback.invoke(origin, false, false)
            }
            .show()
    }

    private fun showFullscreenView(
        view: View?,
        callback: WebChromeClient.CustomViewCallback?
    ) {
        if (view == null) {
            callback?.onCustomViewHidden()
            return
        }

        if (fullscreenView != null) {
            callback?.onCustomViewHidden()
            return
        }

        fullscreenView = view
        fullscreenCallback = callback
        previousSystemUiVisibility = window.decorView.systemUiVisibility

        val decor = window.decorView as ViewGroup
        decor.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        view.setBackgroundColor(Color.BLACK)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun hideFullscreenView() {
        val view = fullscreenView ?: return

        (view.parent as? ViewGroup)?.removeView(view)

        fullscreenView = null

        window.decorView.systemUiVisibility =
            previousSystemUiVisibility

        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    private fun handleOlikhUri(uri: Uri): Boolean {
        if (!uri.scheme.equals("olikh", ignoreCase = true)) {
            return false
        }

        when (uri.host?.lowercase()) {
            "search" -> {
                val query =
                    uri.getQueryParameter("q")
                        ?.trim()
                        .orEmpty()

                if (query.isNotBlank()) {
                    openInput(query)
                }

                return true
            }

            "new-tab" -> {
                createNewTab(
                    initialUrl = "about:blank"
                )
                return true
            }

            "incognito" -> {
                createNewTab(
                    incognito = true,
                    initialUrl = "about:blank"
                )

                Toast.makeText(
                    this,
                    "Incognito tab opened",
                    Toast.LENGTH_SHORT
                ).show()

                return true
            }

            "history" -> {
                showHistory()
                return true
            }

            "bookmarks" -> {
                showBookmarks()
                return true
            }

            "downloads" -> {
                showDownloads()
                return true
            }

            "settings" -> {
                showSettings()
                return true
            }

            "toggle-blocker" -> {
                olikhBlocker.setEnabled(
                    !olikhBlocker.isEnabled()
                )

                showOlikhStartPage()
                return true
            }

            "start" -> {
                showOlikhStartPage()
                return true
            }
        }

        return false
    }

    private fun handleExternalUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false

        if (scheme == "olikh") {
            return handleOlikhUri(uri)
        }

        if (scheme == "http" || scheme == "https") {
            return false
        }

        if (scheme == "intent") {
            return try {
                val intent = Intent.parseUri(
                    uri.toString(),
                    Intent.URI_INTENT_SCHEME
                )

                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    val fallback =
                        intent.getStringExtra(
                            "browser_fallback_url"
                        )

                    if (!fallback.isNullOrBlank()) {
                        webView.loadUrl(fallback)
                    } else {
                        Toast.makeText(
                            this,
                            "App not available",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                true
            } catch (_: Exception) {
                true
            }
        }

        return try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
            )
            true
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "No app available for this link",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
    }

    private fun viewPageSource() {
        val url = webView.url ?: return

        if (!url.startsWith("http://") &&
            !url.startsWith("https://")
        ) {
            Toast.makeText(
                this,
                "Source unavailable",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        createNewTab(
            initialUrl = "view-source:$url"
        )
    }

    private fun saveWebArchive() {
        val title = webView.title
            ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "OLIKH_page"

        val dir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )

        val path = "${dir.absolutePath}/$title.mht"

        webView.saveWebArchive(
            path,
            false
        ) { saved ->
            Toast.makeText(
                this,
                if (saved != null)
                    "Page saved to Downloads"
                else
                    "Could not save page",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshCurrentPage() {
        showingErrorPage = false
        failedUrl = null
        webView.reload()
    }

    private fun installSitePermissionChromeClient(
        targetWebView: WebView,
        tab: BrowserTab? = null
    ) {
        targetWebView.webChromeClient =
            object : WebChromeClient() {

                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int
                ) {
                    if (
                        tab == null ||
                        activeTab === tab
                    ) {
                        progressBar.progress = newProgress

                        progressBar.visibility =
                            if (newProgress >= 100) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                    }
                }

                override fun onReceivedTitle(
                    view: WebView?,
                    pageTitle: String?
                ) {
                    val resolvedTitle =
                        pageTitle ?: "OLIKH"

                    if (tab != null) {
                        tab.title = resolvedTitle
                    }

                    if (
                        tab == null ||
                        activeTab === tab
                    ) {
                        this@MainActivity.title =
                            resolvedTitle
                    }
                }

                override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                return openFileChooser(
                    filePathCallback,
                    fileChooserParams
                )
            }


            override fun onShowCustomView(
                view: View?,
                callback: CustomViewCallback?
            ) {
                showFullscreenView(view, callback)
            }

            override fun onHideCustomView() {
                hideFullscreenView()
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture) return false

                val transport =
                    resultMsg?.obj as? WebView.WebViewTransport
                        ?: return false

                val popup = WebView(this@MainActivity)

                popup.settings.apply {
                    javaScriptEnabled = isJavaScriptEnabled()
                    domStorageEnabled = isDomStorageEnabled()
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                }

                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false

                        if (handleExternalUri(uri)) return true

                        createNewTab(
                            initialUrl = uri.toString()
                        )

                        popup.destroy()
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        url: String?
                    ): Boolean {
                        if (url.isNullOrBlank()) return false

                        val uri = Uri.parse(url)

                        if (handleExternalUri(uri)) return true

                        createNewTab(initialUrl = url)
                        popup.destroy()
                        return true
                    }
                }

                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }


                override fun onCloseWindow(window: WebView?) {
                    window?.let {
                        val index = tabs.indexOfFirst { tab ->
                            tab.webView === it
                        }

                        if (index >= 0) {
                            closeTab(index)
                        } else {
                            it.destroy()
                        }
                    }
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    androidx.appcompat.app.AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle("Page message")
                        .setMessage(message ?: "")
                        .setPositiveButton("OK") { _, _ ->
                            result?.confirm()
                        }
                        .setOnCancelListener {
                            result?.cancel()
                        }
                        .show()

                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    androidx.appcompat.app.AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle("Page confirmation")
                        .setMessage(message ?: "")
                        .setPositiveButton("OK") { _, _ ->
                            result?.confirm()
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            result?.cancel()
                        }
                        .setOnCancelListener {
                            result?.cancel()
                        }
                        .show()

                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    val input = EditText(this@MainActivity).apply {
                        setText(defaultValue ?: "")
                        setSingleLine(true)
                    }

                    androidx.appcompat.app.AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle(message ?: "Page input")
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            result?.confirm(
                                input.text.toString()
                            )
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            result?.cancel()
                        }
                        .setOnCancelListener {
                            result?.cancel()
                        }
                        .show()

                    return true
                }

                override fun onConsoleMessage(
                    consoleMessage: ConsoleMessage?
                ): Boolean {
                    return super.onConsoleMessage(
                        consoleMessage
                    )
                }

            override fun onPermissionRequest(
                    request: PermissionRequest?
                ) {
                    if (request == null) return

                    runOnUiThread {
                        handleWebPermissionRequest(request)
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    handleGeolocationRequest(
                        origin,
                        callback
                    )
                }
            }
    }


    private fun isSafeBrowsingEnabled(): Boolean =
        browserPrefs.getBoolean("safe_browsing_enabled", true)

    private fun isForceDarkEnabled(): Boolean =
        browserPrefs.getBoolean("force_dark_enabled", false)

    private fun isMixedContentAllowed(): Boolean =
        browserPrefs.getBoolean("mixed_content_allowed", false)

    private fun isFormDataEnabled(): Boolean =
        browserPrefs.getBoolean("form_data_enabled", true)

    private fun isPasswordSavingEnabled(): Boolean =
        browserPrefs.getBoolean("password_saving_enabled", false)

    private fun isNetworkLoadsBlocked(): Boolean =
        browserPrefs.getBoolean("network_loads_blocked", false)

    private fun currentTextZoom(): Int =
        browserPrefs.getInt("text_zoom", 100)

    private fun currentMinimumFontSize(): Int =
        browserPrefs.getInt("minimum_font_size", 8)

    private fun isUserAgentOverrideEnabled(): Boolean =
        browserPrefs.getBoolean("custom_user_agent_enabled", false)

    private fun isMediaGestureRequired(): Boolean =
        browserPrefs.getBoolean("media_gesture_required", true)

    private fun applyAdvancedSettings(target: WebView) {
        target.settings.apply {
            safeBrowsingEnabled = isSafeBrowsingEnabled()

            mixedContentMode =
                if (isMixedContentAllowed()) {
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                } else {
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }

            saveFormData = isFormDataEnabled()

            blockNetworkLoads = isNetworkLoadsBlocked()

            textZoom = currentTextZoom()

            minimumFontSize = currentMinimumFontSize()

            mediaPlaybackRequiresUserGesture =
                isMediaGestureRequired()

            if (isUserAgentOverrideEnabled()) {
                userAgentString =
                    browserPrefs.getString(
                        "custom_user_agent",
                        userAgentString
                    )
            }
        }
    }

    private fun applyAdvancedSettingsToAllTabs() {
        tabs.forEach {
            applyAdvancedSettings(it.webView)
        }

        applyAdvancedSettings(webView)
    }

    private fun saveAdvancedBoolean(
        key: String,
        enabled: Boolean
    ) {
        browserPrefs.edit()
            .putBoolean(key, enabled)
            .apply()

        applyAdvancedSettingsToAllTabs()
    }

    private fun showTextZoomSelector() {
        val values = intArrayOf(
            75, 90, 100, 110, 125, 150, 175, 200
        )

        val labels =
            values.map { "$it%" }.toTypedArray()

        val selected =
            values.indexOf(currentTextZoom())
                .takeIf { it >= 0 } ?: 2

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Text zoom")
            .setSingleChoiceItems(
                labels,
                selected
            ) { dialog, which ->

                browserPrefs.edit()
                    .putInt("text_zoom", values[which])
                    .apply()

                applyAdvancedSettingsToAllTabs()

                Toast.makeText(
                    this,
                    "Text zoom ${values[which]}%",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showMinimumFontSelector() {
        val values = intArrayOf(
            6, 8, 10, 12, 14, 16, 18, 20
        )

        val labels =
            values.map { "$it px" }.toTypedArray()

        val selected =
            values.indexOf(currentMinimumFontSize())
                .takeIf { it >= 0 } ?: 1

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Minimum font size")
            .setSingleChoiceItems(
                labels,
                selected
            ) { dialog, which ->

                browserPrefs.edit()
                    .putInt(
                        "minimum_font_size",
                        values[which]
                    )
                    .apply()

                applyAdvancedSettingsToAllTabs()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showCustomUserAgentDialog() {
        val input = EditText(this).apply {
            setSingleLine(false)

            setText(
                browserPrefs.getString(
                    "custom_user_agent",
                    webView.settings.userAgentString
                )
            )
        }

        val padding =
            (20 * resources.displayMetrics.density)
                .toInt()

        val container =
            FrameLayout(this).apply {
                setPadding(
                    padding,
                    0,
                    padding,
                    0
                )

                addView(
                    input,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom User-Agent")
            .setView(container)

            .setNegativeButton(
                "Cancel",
                null
            )

            .setNeutralButton("Reset") { _, _ ->
                browserPrefs.edit()
                    .remove("custom_user_agent")
                    .putBoolean(
                        "custom_user_agent_enabled",
                        false
                    )
                    .apply()

                Toast.makeText(
                    this,
                    "Custom User-Agent disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .setPositiveButton("Save") { _, _ ->
                val ua =
                    input.text.toString().trim()

                if (ua.isNotEmpty()) {
                    browserPrefs.edit()
                        .putString(
                            "custom_user_agent",
                            ua
                        )
                        .putBoolean(
                            "custom_user_agent_enabled",
                            true
                        )
                        .apply()

                    applyAdvancedSettingsToAllTabs()

                    Toast.makeText(
                        this,
                        "Custom User-Agent enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }


    private fun applyReadingDisplaySettings(
        target: WebView
    ) {
        target.settings.apply {
            defaultFontSize = currentDefaultFontSize()
            defaultFixedFontSize = currentFixedFontSize()

            defaultTextEncodingName =
                currentTextEncoding()

            sansSerifFontFamily =
                currentSansFont()

            serifFontFamily =
                currentSerifFont()

            fixedFontFamily =
                currentMonospaceFont()

            offscreenPreRaster =
                isOffscreenPreRasterEnabled()

        }

        if (isAutoFitScaleEnabled()) {
            target.setInitialScale(0)
        }
    }

    private fun applyReadingDisplayToAllTabs() {
        tabs.forEach {
            applyReadingDisplaySettings(it.webView)
        }

        applyReadingDisplaySettings(webView)
    }

    private fun saveReadingBoolean(
        key: String,
        enabled: Boolean
    ) {
        browserPrefs.edit()
            .putBoolean(key, enabled)
            .apply()

        applyReadingDisplayToAllTabs()
    }

    private fun showDefaultFontSizeSelector() {
        val values =
            intArrayOf(12, 14, 16, 18, 20, 22, 24, 28)

        val labels =
            values.map { "$it px" }.toTypedArray()

        val selected =
            values.indexOf(currentDefaultFontSize())
                .takeIf { it >= 0 } ?: 2

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Default font size")
            .setSingleChoiceItems(
                labels,
                selected
            ) { dialog, which ->

                browserPrefs.edit()
                    .putInt(
                        "default_font_size",
                        values[which]
                    )
                    .apply()

                applyReadingDisplayToAllTabs()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showFixedFontSizeSelector() {
        val values =
            intArrayOf(10, 12, 13, 14, 16, 18, 20, 24)

        val labels =
            values.map { "$it px" }.toTypedArray()

        val selected =
            values.indexOf(currentFixedFontSize())
                .takeIf { it >= 0 } ?: 2

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Fixed-width font size")
            .setSingleChoiceItems(
                labels,
                selected
            ) { dialog, which ->

                browserPrefs.edit()
                    .putInt(
                        "fixed_font_size",
                        values[which]
                    )
                    .apply()

                applyReadingDisplayToAllTabs()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showEncodingSelector() {
        val values = arrayOf(
            "UTF-8",
            "ISO-8859-1",
            "windows-1252",
            "UTF-16"
        )

        val selected =
            values.indexOf(currentTextEncoding())
                .takeIf { it >= 0 } ?: 0

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Text encoding")
            .setSingleChoiceItems(
                values,
                selected
            ) { dialog, which ->

                browserPrefs.edit()
                    .putString(
                        "text_encoding",
                        values[which]
                    )
                    .apply()

                applyReadingDisplayToAllTabs()
                webView.reload()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showFontSelector(
        title: String,
        key: String,
        current: String
    ) {
        val values = arrayOf(
            "sans-serif",
            "serif",
            "monospace",
            "cursive"
        )

        val selected =
            values.indexOf(current)
                .takeIf { it >= 0 } ?: 0

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(
                values,
                selected
            ) { dialog, which ->

                browserPrefs.edit()
                    .putString(
                        key,
                        values[which]
                    )
                    .apply()

                applyReadingDisplayToAllTabs()
                webView.reload()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun toggleReaderMode() {
        val enabled = !isReaderModeEnabled()

        browserPrefs.edit()
            .putBoolean(
                "reader_mode_enabled",
                enabled
            )
            .apply()

        if (enabled) {
            val js = """
                javascript:(function(){
                    var style =
                        document.getElementById(
                            'olikh-reader-style'
                        );

                    if(!style){
                        style =
                            document.createElement('style');

                        style.id =
                            'olikh-reader-style';

                        style.innerHTML =
                            'body{' +
                            'max-width:850px;' +
                            'margin:auto!important;' +
                            'padding:24px!important;' +
                            'line-height:1.7!important;' +
                            'font-size:18px!important;' +
                            '}' +
                            'img,video{' +
                            'max-width:100%!important;' +
                            'height:auto!important;' +
                            '}' +
                            'aside,nav{' +
                            'display:none!important;' +
                            '}';

                        document.head.appendChild(style);
                    }
                })()
            """.trimIndent()

            webView.loadUrl(js)
        } else {
            webView.reload()
        }

        Toast.makeText(
            this,
            "Reader mode " +
                if (enabled) "enabled" else "disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showReadingDisplaySettings() {
        val options = arrayOf(
            "Reader mode: " +
                if (isReaderModeEnabled())
                    "On"
                else
                    "Off",

            "Default font size: " +
                "${currentDefaultFontSize()} px",

            "Fixed-width font size: " +
                "${currentFixedFontSize()} px",

            "Text encoding: " +
                currentTextEncoding(),

            "Sans-serif font: " +
                currentSansFont(),

            "Serif font: " +
                currentSerifFont(),

            "Monospace font: " +
                currentMonospaceFont(),

            "Offscreen pre-render: " +
                if (isOffscreenPreRasterEnabled())
                    "On"
                else
                    "Off",

            "Initial focus: " +
                if (isInitialFocusEnabled())
                    "On"
                else
                    "Off",

            "Auto-fit page scale: " +
                if (isAutoFitScaleEnabled())
                    "On"
                else
                    "Off"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reading & display")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleReaderMode()

                    1 -> showDefaultFontSizeSelector()

                    2 -> showFixedFontSizeSelector()

                    3 -> showEncodingSelector()

                    4 -> showFontSelector(
                        "Sans-serif font",
                        "sans_font",
                        currentSansFont()
                    )

                    5 -> showFontSelector(
                        "Serif font",
                        "serif_font",
                        currentSerifFont()
                    )

                    6 -> showFontSelector(
                        "Monospace font",
                        "monospace_font",
                        currentMonospaceFont()
                    )

                    7 -> saveReadingBoolean(
                        "offscreen_preraster_enabled",
                        !isOffscreenPreRasterEnabled()
                    )

                    8 -> saveReadingBoolean(
                        "initial_focus_enabled",
                        !isInitialFocusEnabled()
                    )

                    9 -> saveReadingBoolean(
                        "auto_fit_scale_enabled",
                        !isAutoFitScaleEnabled()
                    )
                }
            }
            .setNegativeButton("Back", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showAdvancedBrowsingSettings() {
        val options = arrayOf(

            "Safe Browsing: " +
                if (isSafeBrowsingEnabled())
                    "On"
                else
                    "Off",

            "Dark page preference: " +
                if (isForceDarkEnabled())
                    "On"
                else
                    "Off",

            "Allow mixed HTTP content: " +
                if (isMixedContentAllowed())
                    "On"
                else
                    "Off",

            "Save form data: " +
                if (isFormDataEnabled())
                    "On"
                else
                    "Off",

            "Password saving preference: " +
                if (isPasswordSavingEnabled())
                    "On"
                else
                    "Off",

            "Block network loads: " +
                if (isNetworkLoadsBlocked())
                    "On"
                else
                    "Off",

            "Text zoom: ${currentTextZoom()}%",

            "Minimum font: " +
                "${currentMinimumFontSize()} px",

            "Custom User-Agent: " +
                if (isUserAgentOverrideEnabled())
                    "On"
                else
                    "Off",

            "Media requires tap: " +
                if (isMediaGestureRequired())
                    "On"
                else
                    "Off"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Advanced browsing")
            .setItems(options) { _, which ->

                when (which) {

                    0 -> saveAdvancedBoolean(
                        "safe_browsing_enabled",
                        !isSafeBrowsingEnabled()
                    )

                    1 -> {
                        val enabled =
                            !isForceDarkEnabled()

                        browserPrefs.edit()
                            .putBoolean(
                                "force_dark_enabled",
                                enabled
                            )
                            .apply()

                        Toast.makeText(
                            this,
                            if (enabled)
                                "Dark page preference enabled"
                            else
                                "Dark page preference disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    2 -> saveAdvancedBoolean(
                        "mixed_content_allowed",
                        !isMixedContentAllowed()
                    )

                    3 -> saveAdvancedBoolean(
                        "form_data_enabled",
                        !isFormDataEnabled()
                    )

                    4 -> {
                        val enabled =
                            !isPasswordSavingEnabled()

                        browserPrefs.edit()
                            .putBoolean(
                                "password_saving_enabled",
                                enabled
                            )
                            .apply()

                        Toast.makeText(
                            this,
                            "Password saving preference " +
                                if (enabled)
                                    "enabled"
                                else
                                    "disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    5 -> saveAdvancedBoolean(
                        "network_loads_blocked",
                        !isNetworkLoadsBlocked()
                    )

                    6 -> showTextZoomSelector()

                    7 -> showMinimumFontSelector()

                    8 -> showCustomUserAgentDialog()

                    9 -> saveAdvancedBoolean(
                        "media_gesture_required",
                        !isMediaGestureRequired()
                    )
                }
            }

            .setNegativeButton(
                "Back",
                null
            )

            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showWebPageSettings() {
        val options = arrayOf(
            "Autoplay media: " +
                if (isAutoplayEnabled()) "On" else "Off",

            "Pinch zoom: " +
                if (areZoomGesturesEnabled()) "On" else "Off",

            "Wide viewport: " +
                if (isWideViewportEnabled()) "On" else "Off",

            "Overview mode: " +
                if (isOverviewModeEnabled()) "On" else "Off",

            "Content access: " +
                if (isContentAccessEnabled()) "On" else "Off",

            "File access: " +
                if (isFileAccessEnabled()) "On" else "Off",

            "JavaScript pop-ups: " +
                if (areJsPopupsEnabled()) "On" else "Off",

            "Multiple windows: " +
                if (areMultipleWindowsEnabled()) "On" else "Off",

            "Browser cache: " +
                if (isCacheEnabled()) "On" else "Off",

            "Desktop viewport: " +
                if (isDesktopViewportEnabled()) "On" else "Off"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Web page settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setAutoplayEnabled(!isAutoplayEnabled())
                    1 -> setZoomGesturesEnabled(!areZoomGesturesEnabled())
                    2 -> setWideViewportEnabled(!isWideViewportEnabled())
                    3 -> setOverviewModeEnabled(!isOverviewModeEnabled())
                    4 -> setContentAccessEnabled(!isContentAccessEnabled())
                    5 -> setFileAccessEnabled(!isFileAccessEnabled())
                    6 -> setJsPopupsEnabled(!areJsPopupsEnabled())
                    7 -> setMultipleWindowsEnabled(!areMultipleWindowsEnabled())
                    8 -> setCacheEnabled(!isCacheEnabled())
                    9 -> setDesktopViewportEnabled(!isDesktopViewportEnabled())
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showSettings()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun setAutoplayEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("autoplay_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.mediaPlaybackRequiresUserGesture = !enabled
        }

        settingToast("Autoplay media", enabled)
    }

    private fun setZoomGesturesEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("zoom_gestures_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.builtInZoomControls = enabled
            it.webView.settings.displayZoomControls = false
        }

        settingToast("Pinch zoom", enabled)
    }

    private fun setWideViewportEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("wide_viewport_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.useWideViewPort = enabled
        }

        webView.reload()
        settingToast("Wide viewport", enabled)
    }

    private fun setOverviewModeEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("overview_mode_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.loadWithOverviewMode = enabled
        }

        webView.reload()
        settingToast("Overview mode", enabled)
    }

    private fun setContentAccessEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("content_access_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.allowContentAccess = enabled
        }

        settingToast("Content access", enabled)
    }

    private fun setFileAccessEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("file_access_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.allowFileAccess = enabled
        }

        settingToast("File access", enabled)
    }

    private fun setJsPopupsEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("js_popups_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.javaScriptCanOpenWindowsAutomatically = enabled
        }

        settingToast("JavaScript pop-ups", enabled)
    }

    private fun setMultipleWindowsEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("multiple_windows_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.setSupportMultipleWindows(enabled)
        }

        settingToast("Multiple windows", enabled)
    }

    private fun setCacheEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("cache_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.cacheMode =
                if (enabled) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_NO_CACHE
                }
        }

        if (!enabled) {
            tabs.forEach {
                it.webView.clearCache(true)
            }
        }

        settingToast("Browser cache", enabled)
    }

    private fun setDesktopViewportEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("desktop_viewport_enabled", enabled)
            .apply()

        tabs.forEach {
            it.webView.settings.useWideViewPort =
                enabled || isWideViewportEnabled()

            it.webView.settings.loadWithOverviewMode =
                enabled || isOverviewModeEnabled()
        }

        webView.reload()
        settingToast("Desktop viewport", enabled)
    }

    private fun settingToast(name: String, enabled: Boolean) {
        Toast.makeText(
            this,
            "$name ${if (enabled) "enabled" else "disabled"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showPrivacySecuritySettings() {
        val options = arrayOf(
            "Cookies",
            "Tracking protection",
            "Site permissions",
            "Storage & data",
            "Content settings",
            "Reset privacy settings"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Privacy & security")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCookieSettings()
                    1 -> showTrackingProtectionSettings()
                    2 -> showSitePermissionSettings()
                    3 -> showStorageDataSettings()
                    4 -> showContentSettings()
                    5 -> resetPrivacySettings()
                }
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showCookieSettings() {
        val options = arrayOf(
            "Cookies: " +
                if (areCookiesEnabled()) "On" else "Off",

            "Third-party cookies: " +
                if (areThirdPartyCookiesEnabled()) "On" else "Off",

            "Clear cookies"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cookies")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setCookiesEnabled(!areCookiesEnabled())
                    1 -> setThirdPartyCookiesEnabled(
                        !areThirdPartyCookiesEnabled()
                    )
                    2 -> clearCookiesOnly()
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showPrivacySecuritySettings()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showTrackingProtectionSettings() {
        val currentUrl = webView.url.orEmpty()

        val currentHost = runCatching {
            Uri.parse(currentUrl).host
        }.getOrNull()
            ?.lowercase()
            ?.trimEnd('.')

        val currentSiteAllowed =
            !currentHost.isNullOrBlank() &&
                olikhBlocker.isAllowedHost(currentHost)

        val options = arrayOf(
            "Ad & tracker blocking: " +
                if (olikhBlocker.isEnabled()) "On" else "Off",

            "Blocked requests: ${olikhBlocker.blockedRequests()}",

            "Blocked domains: ${olikhBlocker.blockedHostCount()}",

            "Allowed sites: ${olikhBlocker.allowedHostCount()}",

            when {
                currentHost.isNullOrBlank() ->
                    "Current site protection unavailable"

                currentSiteAllowed ->
                    "Enable protection for: $currentHost"

                else ->
                    "Allow current site: $currentHost"
            },

            "Reset blocked counter",

            "Clear site allowlist",

            "Do Not Track: " +
                if (isDoNotTrackEnabled()) "On" else "Off"
        )

        val dialog =
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tracking protection")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val enabled =
                                !olikhBlocker.isEnabled()

                            olikhBlocker.setEnabled(enabled)

                            Toast.makeText(
                                this,
                                if (enabled) {
                                    "Ad & tracker blocking enabled"
                                } else {
                                    "Ad & tracker blocking disabled"
                                },
                                Toast.LENGTH_SHORT
                            ).show()

                            webView.reload()
                            showTrackingProtectionSettings()
                        }

                        1 -> {
                            Toast.makeText(
                                this,
                                "${olikhBlocker.blockedRequests()} requests blocked",
                                Toast.LENGTH_SHORT
                            ).show()

                            showTrackingProtectionSettings()
                        }

                        2 -> {
                            Toast.makeText(
                                this,
                                "${olikhBlocker.blockedHostCount()} blocked domains",
                                Toast.LENGTH_SHORT
                            ).show()

                            showTrackingProtectionSettings()
                        }

                        3 -> {
                            Toast.makeText(
                                this,
                                "${olikhBlocker.allowedHostCount()} sites allowed",
                                Toast.LENGTH_SHORT
                            ).show()

                            showTrackingProtectionSettings()
                        }

                        4 -> {
                            if (currentHost.isNullOrBlank()) {
                                Toast.makeText(
                                    this,
                                    "No website is currently open",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (currentSiteAllowed) {
                                olikhBlocker.removeAllowedHost(currentHost)

                                Toast.makeText(
                                    this,
                                    "Protection enabled for $currentHost",
                                    Toast.LENGTH_SHORT
                                ).show()

                                webView.reload()
                            } else {
                                olikhBlocker.addAllowedHost(currentHost)

                                Toast.makeText(
                                    this,
                                    "$currentHost added to allowlist",
                                    Toast.LENGTH_SHORT
                                ).show()

                                webView.reload()
                            }

                            showTrackingProtectionSettings()
                        }

                        5 -> {
                            olikhBlocker.resetCounter()

                            Toast.makeText(
                                this,
                                "Blocked counter reset",
                                Toast.LENGTH_SHORT
                            ).show()

                            showTrackingProtectionSettings()
                        }

                        6 -> {
                            olikhBlocker.clearAllowlist()

                            Toast.makeText(
                                this,
                                "Site allowlist cleared",
                                Toast.LENGTH_SHORT
                            ).show()

                            webView.reload()
                            showTrackingProtectionSettings()
                        }

                        7 -> {
                            setDoNotTrackEnabled(
                                !isDoNotTrackEnabled()
                            )

                            showTrackingProtectionSettings()
                        }
                    }
                }
                .setNegativeButton("Back") { _, _ ->
                    showPrivacySecuritySettings()
                }
                .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showSitePermissionSettings() {
        val options = arrayOf(
            "Camera: " +
                if (cameraPermissionEnabled()) "On" else "Off",

            "Microphone: " +
                if (microphonePermissionEnabled()) "On" else "Off",

            "Location: " +
                if (locationPermissionEnabled()) "On" else "Off",

            "Saved site permissions",
            "Clear location permissions"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Site permissions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setCameraPermissionEnabled(
                        !cameraPermissionEnabled()
                    )

                    1 -> setMicrophonePermissionEnabled(
                        !microphonePermissionEnabled()
                    )

                    2 -> setLocationPermissionEnabled(
                        !locationPermissionEnabled()
                    )

                    3 -> showSavedSitePermissions()
                    4 -> clearLocationPermissions()
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showPrivacySecuritySettings()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showSavedSitePermissions() {
        val saved =
            sitePermissionManager.getSavedPermissions()

        if (saved.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Saved site permissions")
                .setMessage("No site permissions have been saved yet.")
                .setPositiveButton("OK", null)
                .show()

            return
        }

        val hosts = saved.keys.sorted()

        val labels = hosts.map { host ->
            val permissions = saved[host].orEmpty()

            val details = permissions.entries
                .sortedBy { it.key }
                .joinToString(", ") { (permission, decision) ->

                    val name = when (permission) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            "Camera"

                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            "Microphone"

                        "location" ->
                            "Location"

                        else ->
                            permission
                    }

                    val state = when (decision) {
                        SitePermissionManager.Decision.ALLOW ->
                            "Allow"

                        SitePermissionManager.Decision.BLOCK ->
                            "Block"

                        SitePermissionManager.Decision.ASK ->
                            "Ask"
                    }

                    "$name: $state"
                }

            "$host\n$details"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Saved site permissions")
            .setItems(labels) { _, which ->
                showSavedSitePermissionDetails(
                    hosts[which]
                )
            }
            .setNeutralButton("Clear all") { _, _ ->
                sitePermissionManager.clearAll()

                GeolocationPermissions.getInstance()
                    .clearAll()

                Toast.makeText(
                    this,
                    "Saved site permissions cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showSitePermissionSettings()
            }
            .show()
    }

    private fun showSavedSitePermissionDetails(
        host: String
    ) {
        val permissions =
            sitePermissionManager
                .getSavedPermissions()[host]
                .orEmpty()

        val details = permissions.entries
            .sortedBy { it.key }
            .joinToString("\n") { (permission, decision) ->

                val name = when (permission) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                        "Camera"

                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        "Microphone"

                    "location" ->
                        "Location"

                    else ->
                        permission
                }

                val state = when (decision) {
                    SitePermissionManager.Decision.ALLOW ->
                        "Allow"

                    SitePermissionManager.Decision.BLOCK ->
                        "Block"

                    SitePermissionManager.Decision.ASK ->
                        "Ask"
                }

                "$name: $state"
            }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(host)
            .setMessage(
                details.ifBlank {
                    "No saved permissions"
                }
            )
            .setPositiveButton("Clear permissions") { _, _ ->
                sitePermissionManager.clearSite(
                    "https://$host"
                )

                GeolocationPermissions.getInstance()
                    .clear(host)

                Toast.makeText(
                    this,
                    "Permissions cleared for $host",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showSavedSitePermissions()
            }
            .show()
    }

    private fun showStorageDataSettings() {
        val options = arrayOf(
            "DOM storage: " +
                if (isDomStorageEnabled()) "On" else "Off",

            "Database storage: " +
                if (isDatabaseStorageEnabled()) "On" else "Off",

            "Clear cache",
            "Clear history",
            "Clear form data",
            "Clear website storage"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Storage & data")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setDomStorageEnabled(
                        !isDomStorageEnabled()
                    )

                    1 -> setDatabaseStorageEnabled(
                        !isDatabaseStorageEnabled()
                    )

                    2 -> clearCacheOnly()
                    3 -> clearHistoryOnly()
                    4 -> clearFormDataOnly()
                    5 -> clearWebsiteStorage()
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showPrivacySecuritySettings()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showContentSettings() {
        val options = arrayOf(
            "Block images: " +
                if (!areImagesEnabled()) "On" else "Off"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Content settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setImagesEnabled(
                        !areImagesEnabled()
                    )
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showPrivacySecuritySettings()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun setCookiesEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("cookies_enabled", enabled)
            .apply()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(enabled)

        tabs.forEach { tab ->
            cookieManager.setAcceptThirdPartyCookies(
                tab.webView,
                enabled && areThirdPartyCookiesEnabled()
            )
        }

        if (!enabled) {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }

        Toast.makeText(
            this,
            if (enabled) "Cookies enabled" else "Cookies disabled and cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setThirdPartyCookiesEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("third_party_cookies_enabled", enabled)
            .apply()

        val cookieManager = CookieManager.getInstance()

        tabs.forEach { tab ->
            cookieManager.setAcceptThirdPartyCookies(
                tab.webView,
                areCookiesEnabled() && enabled
            )
        }

        Toast.makeText(
            this,
            if (enabled) {
                "Third-party cookies enabled"
            } else {
                "Third-party cookies disabled"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setDoNotTrackEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("do_not_track_enabled", enabled)
            .apply()

        tabs.forEach { tab ->
            if (enabled) {
                tab.webView.settings.userAgentString =
                    tab.webView.settings.userAgentString
                        ?.replace(" OLIKH_DNT", "")
                        .orEmpty() + " OLIKH_DNT"
            } else {
                tab.webView.settings.userAgentString =
                    tab.webView.settings.userAgentString
                        ?.replace(" OLIKH_DNT", "")
            }
        }

        Toast.makeText(
            this,
            if (enabled) "Do Not Track enabled" else "Do Not Track disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setImagesEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("images_enabled", enabled)
            .apply()

        tabs.forEach { tab ->
            tab.webView.settings.loadsImagesAutomatically = enabled
            tab.webView.settings.blockNetworkImage = !enabled
        }

        if (enabled) {
            webView.reload()
        }

        Toast.makeText(
            this,
            if (enabled) "Images enabled" else "Images blocked",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setDomStorageEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("dom_storage_enabled", enabled)
            .apply()

        tabs.forEach { tab ->
            tab.webView.settings.domStorageEnabled = enabled
        }

        Toast.makeText(
            this,
            if (enabled) "DOM storage enabled" else "DOM storage disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setDatabaseStorageEnabled(enabled: Boolean) {
        browserPrefs.edit()
            .putBoolean("database_storage_enabled", enabled)
            .apply()

        tabs.forEach { tab ->
            tab.webView.settings.databaseEnabled = enabled
        }

        Toast.makeText(
            this,
            if (enabled) {
                "Database storage enabled"
            } else {
                "Database storage disabled"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearCookiesOnly() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }

        Toast.makeText(
            this,
            "Cookies cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearCacheOnly() {
        tabs.forEach { tab ->
            tab.webView.clearCache(true)
        }

        Toast.makeText(
            this,
            "Cache cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearHistoryOnly() {
        historyManager.clear()

        tabs.forEach { tab ->
            tab.webView.clearHistory()
        }

        Toast.makeText(
            this,
            "History cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearFormDataOnly() {
        tabs.forEach { tab ->
            tab.webView.clearFormData()
        }

        Toast.makeText(
            this,
            "Form data cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearWebsiteStorage() {
        android.webkit.WebStorage.getInstance()
            .deleteAllData()

        Toast.makeText(
            this,
            "Website storage cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearLocationPermissions() {
        android.webkit.GeolocationPermissions.getInstance()
            .clearAll()

        Toast.makeText(
            this,
            "Location permissions cleared",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun resetPrivacySettings() {
        browserPrefs.edit()
            .remove("cookies_enabled")
            .remove("third_party_cookies_enabled")
            .remove("do_not_track_enabled")
            .remove("images_enabled")
            .remove("dom_storage_enabled")
            .remove("database_storage_enabled")
            .apply()

        val cookieManager = CookieManager.getInstance()

        cookieManager.setAcceptCookie(true)

        tabs.forEach { tab ->
            cookieManager.setAcceptThirdPartyCookies(
                tab.webView,
                true
            )

            tab.webView.settings.loadsImagesAutomatically = true
            tab.webView.settings.blockNetworkImage = false
            tab.webView.settings.domStorageEnabled = true
            tab.webView.settings.databaseEnabled = true

            tab.webView.settings.userAgentString =
                tab.webView.settings.userAgentString
                    ?.replace(" OLIKH_DNT", "")
        }

        Toast.makeText(
            this,
            "Privacy settings reset",
            Toast.LENGTH_SHORT
        ).show()

        webView.reload()
    }

    private fun showJavaScriptSetting() {
        val options = arrayOf(
            "On",
            "Off"
        )

        val selected =
            if (isJavaScriptEnabled()) 0 else 1

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("JavaScript")
            .setSingleChoiceItems(
                options,
                selected
            ) { dialog, which ->
                val enabled = which == 0

                browserPrefs.edit()
                    .putBoolean("javascript_enabled", enabled)
                    .apply()

                tabs.forEach { tab ->
                    tab.webView.settings.javaScriptEnabled = enabled
                }

                Toast.makeText(
                    this,
                    if (enabled) {
                        "JavaScript enabled"
                    } else {
                        "JavaScript disabled"
                    },
                    Toast.LENGTH_SHORT
                ).show()

                webView.reload()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showHomepageSettings() {
        val input = EditText(this).apply {
            setText(homePage)
            hint = "https://example.com"
            setSingleLine(true)
            selectAll()
        }

        val padding = (20 * resources.displayMetrics.density).toInt()

        val container = FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)

            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Homepage")
            .setMessage("Set the page opened by Home and new tabs.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset") { _, _ ->
                browserPrefs.edit()
                    .remove("home_page")
                    .apply()

                Toast.makeText(
                    this,
                    "Homepage reset to Google",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton("Save") { _, _ ->
                var url = input.text.toString().trim()

                if (url.isBlank()) {
                    Toast.makeText(
                        this,
                        "Homepage cannot be empty",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (!url.startsWith("http://", true) &&
                    !url.startsWith("https://", true)
                ) {
                    url = "https://$url"
                }

                val parsed = Uri.parse(url)

                if (parsed.host.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        "Invalid homepage URL",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                browserPrefs.edit()
                    .putString("home_page", url)
                    .apply()

                Toast.makeText(
                    this,
                    "Homepage saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showSearchEngineSelector() {
        val engines = arrayOf(
            "Google",
            "DuckDuckGo",
            "Bing",
            "Brave"
        )

        val current = currentSearchEngine()
        val selected = engines.indexOf(current)
            .takeIf { it >= 0 }
            ?: 0

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Search engine")
            .setSingleChoiceItems(
                engines,
                selected
            ) { dialog, which ->
                val engine = engines[which]

                browserPrefs.edit()
                    .putString("search_engine", engine)
                    .apply()

                Toast.makeText(
                    this,
                    "$engine selected",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun showZoomMenu() {
        val options = arrayOf(
            "Zoom in",
            "Zoom out",
            "Reset zoom"
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Page zoom")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> webView.zoomIn()
                    1 -> webView.zoomOut()

                    2 -> {
                        webView.setInitialScale(0)
                        webView.reload()

                        Toast.makeText(
                            this,
                            "Zoom reset",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
    }

    private fun confirmClearBrowsingData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear browsing data?")
            .setMessage("This will clear history, cookies, WebView cache and form data.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                historyManager.clear()

                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }

                tabs.forEach { tab ->
                    tab.webView.clearCache(true)
                    tab.webView.clearFormData()
                    tab.webView.clearHistory()
                }

                Toast.makeText(
                    this,
                    "Browsing data cleared",
                    Toast.LENGTH_SHORT
                ).show()

                updateNavigationButtons()
            }
            .show()
    }

    private fun showFindInPage() {
        val input = EditText(this).apply {
            hint = "Find text on this page"
            setSingleLine(true)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Find in page")
            .setView(input)
            .setPositiveButton("Find", null)
            .setNegativeButton("Close") { _, _ ->
                webView.clearMatches()
            }
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val query = input.text.toString().trim()

                    if (query.isNotEmpty()) {
                        webView.findAllAsync(query)
                        webView.findNext(true)
                    }
                }
        }

        dialog.show()
    }

    private fun shareCurrentPage() {
        val url = webView.url
            ?.takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }
            ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, webView.title ?: "OLIKH")
            putExtra(Intent.EXTRA_TEXT, url)
        }

        startActivity(
            Intent.createChooser(
                shareIntent,
                "Share page"
            )
        )
    }

    private fun pasteAndGo() {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager

        val clip = clipboard.primaryClip

        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(
                this,
                "Clipboard is empty",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val value =
            clip.getItemAt(0)
                .coerceToText(this)
                ?.toString()
                ?.trim()
                .orEmpty()

        if (value.isBlank()) {
            Toast.makeText(
                this,
                "Clipboard has no text",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        addressBar.setText(value)
        openInput(value)
    }

    private fun duplicateCurrentTab() {
        val currentUrl =
            webView.url
                ?.takeIf {
                    it.startsWith("http://") ||
                        it.startsWith("https://")
                }
                ?: "about:blank"

        createNewTab(
            incognito = activeTab?.incognito == true,
            initialUrl = currentUrl
        )
    }

    private fun copyCurrentPageTitle() {
        val value = webView.title?.replace("\n", " ")?.trim().orEmpty()
        if (value.isBlank()) {
            Toast.makeText(this, "Page has no title", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OLIKH page title", value))
        Toast.makeText(this, "Page title copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyCurrentLinkAsText() {
        val url = webView.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        val pageTitle = webView.title?.replace("\n", " ")?.trim()?.takeIf { it.isNotBlank() } ?: url
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OLIKH page", "$pageTitle\n$url"))
        Toast.makeText(this, "Page title and URL copied", Toast.LENGTH_SHORT).show()
    }

    private fun openCurrentPageInNewTab() {
        val url = webView.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        createNewTab(incognito = activeTab?.incognito == true, initialUrl = url)
    }

    private fun copyCurrentUrl() {
        val url = webView.url
            ?.takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }
            ?: return

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "OLIKH URL",
                url
            )
        )

        Toast.makeText(
            this,
            "URL copied",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleDesktopSite() {
        val settings = webView.settings
        val currentUa = settings.userAgentString ?: ""

        val desktopEnabled =
            currentUa.contains("OLIKH_DESKTOP")

        if (desktopEnabled) {
            settings.userAgentString = null
            settings.useWideViewPort = isDesktopViewportEnabled() || isWideViewportEnabled()
            settings.loadWithOverviewMode = isDesktopViewportEnabled() || isOverviewModeEnabled()

            Toast.makeText(
                this,
                "Mobile site",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val desktopUa =
                "Mozilla/5.0 (X11; Linux x86_64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/138.0.0.0 Safari/537.36 OLIKH_DESKTOP"

            settings.userAgentString = desktopUa
            settings.useWideViewPort = isDesktopViewportEnabled() || isWideViewportEnabled()
            settings.loadWithOverviewMode = isDesktopViewportEnabled() || isOverviewModeEnabled()

            Toast.makeText(
                this,
                "Desktop site",
                Toast.LENGTH_SHORT
            ).show()
        }

        webView.reload()
    }

    private fun openInput(rawInput: String) {
        val input = rawInput.trim()

        if (input.isEmpty()) return

        showingErrorPage = false
        failedUrl = null

        val url = when {
            input.startsWith("http://", true) ||
                input.startsWith("https://", true) -> input

            input.contains(".") &&
                !input.contains(" ") -> "https://$input"

            else -> {
                buildSearchUrl(input)
            }
        }

        webView.loadUrl(url)
    }

    private fun showNetworkError(
        url: String,
        description: String
    ) {
        if (showingErrorPage) return

        showingErrorPage = true
        failedUrl = url

        webView.stopLoading()
        progressBar.visibility = View.GONE

        addressBar.setText(
                    if (isOlikhStartPageUrl(url)) "OLIKH Start"
                    else url
                )
        title = "OLIKH"

        val safeHost = escapeHtml(
            runCatching {
                Uri.parse(url).host ?: url
            }.getOrDefault(url)
        )

        val safeDescription = escapeHtml(description)

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="dark">
                <style>
                    * {
                        box-sizing: border-box;
                    }

                    html, body {
                        margin: 0;
                        min-height: 100%;
                        background: #0d0f12;
                        color: #f5f7fa;
                        font-family: sans-serif;
                    }

                    body {
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 32px 24px;
                    }

                    .card {
                        width: 100%;
                        max-width: 520px;
                    }

                    .icon {
                        width: 58px;
                        height: 58px;
                        border-radius: 18px;
                        background: #1b1f25;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 28px;
                        margin-bottom: 28px;
                    }

                    h1 {
                        margin: 0 0 12px 0;
                        font-size: 32px;
                        line-height: 1.15;
                    }

                    .host {
                        color: #ffffff;
                        font-size: 16px;
                        margin-bottom: 12px;
                        overflow-wrap: anywhere;
                    }

                    .description {
                        color: #9ca3ad;
                        font-size: 15px;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }

                    button {
                        border: 0;
                        border-radius: 999px;
                        padding: 14px 24px;
                        background: #ffffff;
                        color: #101216;
                        font-size: 15px;
                        font-weight: 700;
                    }

                    button:active {
                        opacity: 0.75;
                    }

                    .brand {
                        margin-top: 38px;
                        color: #666d78;
                        font-size: 12px;
                        letter-spacing: 3px;
                        font-weight: 700;
                    }
                </style>
            </head>

            <body>
                <div class="card">

                    <div class="icon">↻</div>

                    <h1>Can't reach this page</h1>

                    <div class="host">
                        $safeHost
                    </div>

                    <div class="description">
                        $safeDescription<br><br>
                        Check your internet connection or DNS,
                        then try again.
                    </div>

                    <button onclick="location.href='olikh://retry'">
                        Try again
                    </button>

                    <div class="brand">
                        OLIKH
                    </div>

                </div>
            </body>
            </html>
        """.trimIndent()

        webView.webViewClient = createErrorAwareClient()

        webView.loadDataWithBaseURL(
            "https://olikh.local/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun createErrorAwareClient(): WebViewClient {
        return object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                    ?: return super.shouldInterceptRequest(view, request)

                if (olikhBlocker.shouldBlock(url)) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "No Content",
                        mapOf(
                            "Cache-Control" to "no-store",
                            "X-OLIKH-Blocked" to "1"
                        ),
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {

                val uri = request?.url ?: return false

                if (uri.toString() == "olikh://retry") {
                    retryFailedPage()
                    return true
                }

                if (handleOlikhUri(uri)) {
                    return true
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                updateNavigationButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true &&
                    !showingErrorPage
                ) {
                    showNetworkError(
                        request.url.toString(),
                        error?.description?.toString()
                            ?: "The page could not be loaded."
                    )
                }
            }
        }
    }

    private fun retryFailedPage() {
        val url = failedUrl ?: return

        showingErrorPage = false
        failedUrl = null

        installNormalWebViewClient()

        webView.loadUrl(url)
    }

    private fun installNormalWebViewClient() {
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                    ?: return super.shouldInterceptRequest(view, request)

                if (olikhBlocker.shouldBlock(url)) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "No Content",
                        mapOf(
                            "Cache-Control" to "no-store",
                            "X-OLIKH-Blocked" to "1"
                        ),
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }




            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {

                val uri = request?.url ?: return false

                if (handleOlikhUri(uri)) {
                    return true
                }

                return handleExternalUri(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {

                if (url.isNullOrBlank()) return false

                val uri = Uri.parse(url)

                if (handleOlikhUri(uri)) {
                    return true
                }

                return handleExternalUri(uri)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(
                    view,
                    request,
                    errorResponse
                )

                handleMainFrameHttpError(
                    null,
                    request,
                    errorResponse
                )
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handleSslFailure(
                    null,
                    view,
                    handler,
                    error
                )
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                progressBar.visibility = View.VISIBLE

                url?.let {
                    if (!addressBar.hasFocus()) {
                        addressBar.setText(it)
                    }
                }
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                progressBar.visibility = View.GONE

                url?.let {
                    if (!addressBar.hasFocus()) {
                        addressBar.setText(it)
                    }
                }

                updateNavigationButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                if (request?.isForMainFrame == true) {
                    showNetworkError(
                        request.url.toString(),
                        error?.description?.toString()
                            ?: "The page could not be loaded."
                    )
                }
            }
        }
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun saveTabs() {
        val editor = tabPrefs.edit()

        editor.clear()

        val persistentTabs = tabs.filterNot { it.incognito }

        editor.putInt("tab_count", persistentTabs.size)

        val currentTab = activeTab
        val persistentActiveIndex =
            if (currentTab != null && !currentTab.incognito) {
                persistentTabs.indexOf(currentTab).coerceAtLeast(0)
            } else {
                0
            }

        editor.putInt(
            "active_tab",
            persistentActiveIndex
        )

        persistentTabs.forEachIndexed { index, tab ->
            val currentUrl = tab.webView.url
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: tab.url.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                }
                ?: homePage

            editor.putString("tab_${index}_url", currentUrl)
            editor.putString(
                "tab_${index}_title",
                tab.title.ifBlank { "New Tab" }
            )
        }

        editor.apply()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun restoreTabs(): Boolean {
        val count = tabPrefs.getInt("tab_count", 0)

        if (count <= 0) return false

        val urls = (0 until count).map { index ->
            tabPrefs.getString("tab_${index}_url", homePage)
                ?.takeIf {
                    it.startsWith("http://") ||
                        it.startsWith("https://")
                }
                ?: homePage
        }

        val titles = (0 until count).map { index ->
            tabPrefs.getString("tab_${index}_title", "New Tab")
                ?: "New Tab"
        }

        val wantedActiveIndex = tabPrefs
            .getInt("active_tab", 0)
            .coerceIn(0, urls.lastIndex)

        tabs.forEach { tab ->
            (tab.webView.parent as? android.view.ViewGroup)
                ?.removeView(tab.webView)

            tab.webView.stopLoading()
            tab.webView.webChromeClient = null
            tab.webView.webViewClient = WebViewClient()
            tab.webView.destroy()
        }

        tabs.clear()
        browserContainer.removeAllViews()

        urls.forEachIndexed { index, url ->
            val restoredWebView = WebView(this)

            restoredWebView.layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

            restoredWebView.settings.apply {
                javaScriptEnabled = isJavaScriptEnabled()
                domStorageEnabled = isDomStorageEnabled()
                databaseEnabled = isDatabaseStorageEnabled()

                loadsImagesAutomatically = areImagesEnabled()
                blockNetworkImage = !areImagesEnabled()

                useWideViewPort = isDesktopViewportEnabled() || isWideViewportEnabled()
                loadWithOverviewMode = isDesktopViewportEnabled() || isOverviewModeEnabled()

                setSupportZoom(areZoomGesturesEnabled())
                builtInZoomControls = areZoomGesturesEnabled()
                displayZoomControls = false
                setGeolocationEnabled(locationPermissionEnabled())

                cacheMode =
                if (isCacheEnabled()) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_NO_CACHE
                }
                mediaPlaybackRequiresUserGesture = !isAutoplayEnabled()

                allowContentAccess = isContentAccessEnabled()
                allowFileAccess = isFileAccessEnabled()

                javaScriptCanOpenWindowsAutomatically = areJsPopupsEnabled()
                setSupportMultipleWindows(areMultipleWindowsEnabled())
            }

            applyReadingDisplaySettings(restoredWebView)

            CookieManager.getInstance().apply {
                setAcceptCookie(areCookiesEnabled())
                setAcceptThirdPartyCookies(restoredWebView, areCookiesEnabled() && areThirdPartyCookiesEnabled())
            }

            installDownloadListener(restoredWebView)
            installLongPressActions(restoredWebView)

            val tab = BrowserTab(
                webView = restoredWebView,
                title = titles[index],
                url = url
            )

            tabs.add(tab)

            restoredWebView.webViewClient =
                createTabWebViewClient(tab)

            installSitePermissionChromeClient(restoredWebView, tab)
        }

        activeTabIndex = wantedActiveIndex
        switchToTab(activeTabIndex)

        tabs.forEach { tab ->
            tab.webView.loadUrl(tab.url)
        }

        btnTabs.text = tabs.size.toString()
        return true
    }

    private fun updateNavigationButtons() {
        findViewById<ImageButton>(R.id.btnBack).isEnabled =
            webView.canGoBack()

        findViewById<ImageButton>(R.id.btnForward).isEnabled =
            webView.canGoForward()

        updateBookmarkButton()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == locationPermissionRequestCode) {
            val origin = pendingLocationOrigin
            val callback = pendingLocationCallback

            pendingLocationOrigin = null
            pendingLocationCallback = null

            if (origin != null && callback != null) {
                val granted =
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                callback.invoke(
                    origin,
                    granted,
                    granted
                )
            }

            return
        }

        if (requestCode != webPermissionRequestCode) {
            return
        }

        val request =
            pendingWebPermissionRequest ?: return

        pendingWebPermissionRequest = null

        val allowed = mutableListOf<String>()

        if (
            request.resources.contains(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE
            ) &&
            cameraPermissionEnabled() &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            allowed +=
                PermissionRequest.RESOURCE_VIDEO_CAPTURE
        }

        if (
            request.resources.contains(
                PermissionRequest.RESOURCE_AUDIO_CAPTURE
            ) &&
            microphonePermissionEnabled() &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            allowed +=
                PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }

        if (allowed.isEmpty()) {
            request.deny()
        } else {
            request.grant(allowed.toTypedArray())
        }
    }

    override fun onBackPressed() {
        if (fullscreenView != null) {
            hideFullscreenView()
            return
        }

        if (webView.canGoBack()) {
            showingErrorPage = false
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveTabs()
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        saveTabs()
        super.onStop()
    }

    override fun onDestroy() {
        pendingClientCertRequest?.cancel()
        pendingClientCertRequest = null

        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null

        pendingLocationCallback?.let { callback ->
            pendingLocationOrigin?.let { origin ->
                callback.invoke(origin, false, false)
            }
        }
        pendingLocationOrigin = null
        pendingLocationCallback = null

        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }
}
