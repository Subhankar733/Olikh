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
        popup.menu.add("Power controls")
        popup.menu.add("Library & sessions")
        popup.menu.add("Smart browser V16")
        popup.menu.add("Security center V17")
        popup.menu.add("Page utility V18")
        popup.menu.add("Download & permissions V19")
        popup.menu.add("Privacy dashboard V20")
        popup.menu.add("Web app & media V21")
        popup.menu.add("Command center V22")
        popup.menu.add("Navigation & tabs V23")
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

                "Power controls" -> {
                    showPowerControlsV14()
                    true
                }

                "Library & sessions" -> {
                    showLibrarySessionsV15()
                    true
                }

                "Smart browser V16" -> {
                    showSmartBrowserV16()
                    true
                }

                "Security center V17" -> {
                    showSecurityCenterV17()
                    true
                }

                "Page utility V18" -> {
                    showPageUtilityV18()
                    true
                }

                "Download & permissions V19" -> {
                    showDownloadPermissionsV19()
                    true
                }

                "Privacy dashboard V20" -> {
                    showPrivacyDashboardV20()
                    true
                }

                "Web app & media V21" -> {
                    showWebAppMediaV21()
                    true
                }

                "Command center V22" -> {
                    showCommandCenterV22()
                    true
                }

                "Navigation & tabs V23" -> {
                    showNavigationTabsV23()
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


    private fun showNavigationTabsV23() {
        val current = if (tabs.isEmpty()) 0 else activeTabIndex + 1
        val items = arrayOf(
            "Tab overview", "Previous tab", "Next tab", "New tab",
            "New private tab", "Duplicate current tab", "Close current tab",
            "Close other tabs", "Reopen last closed tab", "Tab status"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Navigation & tabs V23")
            .setMessage("Active tab: $current / ${tabs.size}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showTabManager()
                    1 -> switchToPreviousTabV23()
                    2 -> switchToNextTabV23()
                    3 -> createNewTab(initialUrl = "about:blank")
                    4 -> createNewTab(incognito = true)
                    5 -> duplicateCurrentTab()
                    6 -> closeCurrentTab()
                    7 -> closeOtherTabsV23()
                    8 -> reopenLastClosedTab()
                    9 -> showTabStatusV23()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun switchToPreviousTabV23() {
        if (tabs.size < 2) {
            Toast.makeText(this, "No previous tab", Toast.LENGTH_SHORT).show()
            return
        }
        switchToTab(if (activeTabIndex <= 0) tabs.lastIndex else activeTabIndex - 1)
    }

    private fun switchToNextTabV23() {
        if (tabs.size < 2) {
            Toast.makeText(this, "No next tab", Toast.LENGTH_SHORT).show()
            return
        }
        switchToTab(if (activeTabIndex >= tabs.lastIndex) 0 else activeTabIndex + 1)
    }

    private fun closeOtherTabsV23() {
        if (tabs.size <= 1) {
            Toast.makeText(this, "No other tabs to close", Toast.LENGTH_SHORT).show()
            return
        }

        val keepTab = tabs.getOrNull(activeTabIndex) ?: return
        val closingTabs = tabs.filter { it !== keepTab }

        closingTabs.forEach { tab ->
            rememberClosedTab(tab)
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
            tab.webView.stopLoading()
            tab.webView.webChromeClient = null
            tab.webView.webViewClient = WebViewClient()
            tab.webView.removeAllViews()
            tab.webView.destroy()
        }

        tabs.clear()
        tabs.add(keepTab)
        activeTabIndex = 0
        switchToTab(0)

        Toast.makeText(
            this,
            "${closingTabs.size} other tab(s) closed",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showTabStatusV23() {
        val tab = activeTab
        val current = if (tabs.isEmpty()) 0 else activeTabIndex + 1
        val titleText = tab?.title?.trim()?.ifBlank { "Untitled" } ?: "None"
        val urlText = tab?.webView?.url?.trim()?.takeIf { it.isNotBlank() }
            ?: tab?.url?.trim()?.ifBlank { "about:blank" }
            ?: "None"
        val mode = if (tab?.incognito == true) "Private" else "Normal"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tab status V23")
            .setMessage(
                "Tabs: ${tabs.size}\n" +
                "Active: $current / ${tabs.size}\n" +
                "Mode: $mode\n" +
                "Title: $titleText\n" +
                "URL: $urlText"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCommandCenterV22() {
        val items=arrayOf("Privacy & security","Downloads & permissions","Web app & media","Research & inspection","Productivity","Library & sessions","Smart browser","Page utilities","Power controls","Browser systems","Quick access","Downloads","Settings","Open start page","New private tab","Duplicate current tab","Reopen closed tab","Share page","Copy URL","Desktop / mobile site","Clear browsing data","Command center status")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("OLIKH Command Center V22").setItems(items){_,i->
            when(i){
                0->showPrivacyDashboardV20()
                1->showDownloadPermissionsV19()
                2->showWebAppMediaV21()
                3->showResearchToolsV13()
                4->showProductivityToolsV12()
                5->showLibrarySessionsV15()
                6->showSmartBrowserV16()
                7->showPageUtilityV18()
                8->showPowerControlsV14()
                9->showBrowserSystemsV11()
                10->showQuickAccessManager()
                11->showDownloads()
                12->showSettings()
                13->showOlikhStartPage()
                14->createNewTab(incognito=true)
                15->duplicateCurrentTab()
                16->reopenLastClosedTab()
                17->shareCurrentPage()
                18->copyCurrentUrl()
                19->toggleDesktopSite()
                20->confirmClearBrowsingData()
                21->commandCenterStatusV22()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun commandCenterStatusV22(){
        val mode = if (webView.settings.userAgentString?.contains("OLIKH_DESKTOP") == true) "Desktop" else "Mobile"
        val text = """OLIKH V22
Tab: ${webView.title.orEmpty()}
Mode: $mode
Incognito: ${activeTab?.incognito == true}
JavaScript: ${webView.settings.javaScriptEnabled}
Blocker: ${olikhBlocker.isEnabled()}"""
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Command center status")
            .setMessage(text)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showWebAppMediaV21() {
        val items=arrayOf(
            "Web app diagnostics","Detect manifest","Open manifest","Service worker clues",
            "Add page shortcut","Copy app metadata","Media diagnostics","List video sources",
            "List audio sources","Pause all media","Resume all media","Mute all media",
            "Unmute all media","Playback 0.75x","Playback 1x","Playback 1.25x",
            "Playback 1.5x","Playback 2x","Loop video ON","Loop video OFF",
            "Picture-in-Picture request","Fullscreen first video","Exit fullscreen",
            "Disable autoplay","Enable autoplay","Keep screen awake","Release screen awake",
            "Copy media report","V21 status"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Web app & media V21").setItems(items){_,i->
            when(i){
                0->webAppDiagnosticsV21()
                1->manifestV21(false)
                2->manifestV21(true)
                3->serviceWorkerV21()
                4->shortcutV21()
                5->appMetadataV21()
                6->mediaDiagnosticsV21()
                7->mediaSourcesV21("video")
                8->mediaSourcesV21("audio")
                9->mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>e.pause())","Media paused")
                10->mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>e.play().catch(()=>{}))","Resume requested")
                11->mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>e.muted=true)","Muted")
                12->mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>e.muted=false)","Unmuted")
                13->rateV21(0.75)
                14->rateV21(1.0)
                15->rateV21(1.25)
                16->rateV21(1.5)
                17->rateV21(2.0)
                18->mediaJsV21("let v=document.querySelector('video');if(v)v.loop=true","Loop ON")
                19->mediaJsV21("let v=document.querySelector('video');if(v)v.loop=false","Loop OFF")
                20->pipV21()
                21->mediaJsV21("let v=document.querySelector('video');if(v&&v.requestFullscreen)v.requestFullscreen()","Fullscreen requested")
                22->mediaJsV21("if(document.fullscreenElement)document.exitFullscreen()","Exit requested")
                23->mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>{e.autoplay=false;e.removeAttribute('autoplay')})","Autoplay disabled")
                24->mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>e.autoplay=true)","Autoplay enabled")
                25->{window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);toastV21("Screen awake ON")}
                26->{window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);toastV21("Screen awake OFF")}
                27->mediaReportV21()
                28->statusV21()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun toastV21(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
    private fun mediaJsV21(js:String,msg:String){webView.evaluateJavascript(js,null);toastV21(msg)}

    private fun webAppDiagnosticsV21(){
        val js="(function(){let m=document.querySelector('link[rel=manifest]');let t=document.querySelector('meta[name=theme-color]');return 'Manifest: '+(m?m.href:'none')+' | Theme: '+(t?t.content:'none')+' | HTTPS: '+(location.protocol==='https:')})()"
        webView.evaluateJavascript(js){r->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Web app diagnostics").setMessage(r.trim('"')).setPositiveButton("Close",null).show()
        }
    }

    private fun manifestV21(open:Boolean){
        webView.evaluateJavascript("(function(){let m=document.querySelector('link[rel=manifest]');return m?m.href:''})()"){r->
            val u=r.trim('"')
            if(u.isBlank())toastV21("No manifest found")
            else if(open)createNewTab(initialUrl=u) else toastV21("Manifest found")
        }
    }

    private fun serviceWorkerV21(){
        webView.evaluateJavascript("(function(){return 'ServiceWorker API: '+('serviceWorker' in navigator)+' | Cache API: '+('caches' in window)+' | HTTPS: '+(location.protocol==='https:')})()"){r->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Service worker clues").setMessage(r.trim('"')).setPositiveButton("Close",null).show()
        }
    }

    private fun shortcutV21(){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV21("No page");return}
        val shortcut=android.content.Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply{
            putExtra(android.content.Intent.EXTRA_SHORTCUT_NAME,webView.title ?: "OLIKH Web App")
            putExtra(android.content.Intent.EXTRA_SHORTCUT_INTENT,android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(u)))
            putExtra("duplicate",false)
        }
        try{sendBroadcast(shortcut);toastV21("Shortcut requested")}catch(e:Exception){toastV21("Shortcut unavailable")}
    }

    private fun appMetadataV21(){
        webView.evaluateJavascript("(function(){let m=document.querySelector('link[rel=manifest]');return 'Title: '+document.title+' | URL: '+location.href+' | Manifest: '+(m?m.href:'none')})()"){r->
            megaCopy("OLIKH web app",r.trim('"'),"App metadata copied")
        }
    }

    private fun mediaDiagnosticsV21(){
        webView.evaluateJavascript("(function(){let m=[...document.querySelectorAll('video,audio')];return 'Media: '+m.length+' | Playing: '+m.filter(x=>!x.paused).length+' | Muted: '+m.filter(x=>x.muted).length})()"){r->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Media diagnostics").setMessage(r.trim('"')).setPositiveButton("Close",null).show()
        }
    }

    private fun mediaSourcesV21(tag:String){
        webView.evaluateJavascript("(function(){return [...document.querySelectorAll('$tag')].map(x=>x.currentSrc||x.src).filter(Boolean).join(' | ')})()"){r->
            val t=r.trim('"')
            if(t.isBlank())toastV21("No $tag source") else megaCopy("OLIKH $tag sources",t,"Sources copied")
        }
    }

    private fun rateV21(rate:Double){
        mediaJsV21("document.querySelectorAll('video,audio').forEach(e=>e.playbackRate=$rate)","Playback ${rate}x")
    }

    private fun pipV21(){
        val js="(async function(){let v=document.querySelector('video');if(!v)return 'No video';if(!document.pictureInPictureEnabled)return 'PiP unavailable';try{await v.requestPictureInPicture();return 'PiP requested'}catch(e){return 'PiP failed'}})()"
        webView.evaluateJavascript(js){r->toastV21(r.trim('"'))}
    }

    private fun mediaReportV21(){
        webView.evaluateJavascript("(function(){let m=[...document.querySelectorAll('video,audio')];return 'Page: '+location.href+' | Media: '+m.length+' | Playing: '+m.filter(x=>!x.paused).length+' | Fullscreen: '+!!document.fullscreenElement})()"){r->
            megaCopy("OLIKH media report",r.trim('"'),"Media report copied")
        }
    }

    private fun statusV21(){
        val t="Page: ${webView.title.orEmpty()}\nURL: ${webView.url.orEmpty()}\nFullscreen: ${fullscreenView!=null}\nScreen awake: ${(window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0}"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("V21 status").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun showPrivacyDashboardV20() {
        val items=arrayOf("Privacy report","Clear current site storage","Clear cookies","Clear WebView cache",
            "Clear browsing history","Clear form data","Third-party cookies status","Toggle third-party cookies",
            "JavaScript status","Toggle JavaScript","Mixed content status","Block mixed content",
            "Compatibility mixed content","Connection info","Copy current URL","Share current URL",
            "Open externally","Incognito status","Open private tab","Clear privacy bundle","Copy privacy report","V20 status")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Privacy dashboard V20").setItems(items){_,i->
            when(i){
                0->privacyReportDialogV20()
                1->clearSiteDataV20()
                2->clearCookiesV20()
                3->{webView.clearCache(true);toastV20("Cache cleared")}
                4->{historyManager.clear();toastV20("History cleared")}
                5->{webView.clearFormData();toastV20("Form data cleared")}
                6->toastV20("Third-party cookies: "+if(android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(webView))"ALLOWED" else "BLOCKED")
                7->toggleThirdPartyV20()
                8->toastV20("JavaScript: "+if(webView.settings.javaScriptEnabled)"ON" else "OFF")
                9->{webView.settings.javaScriptEnabled=!webView.settings.javaScriptEnabled;toastV20("JavaScript: "+if(webView.settings.javaScriptEnabled)"ON" else "OFF")}
                10->mixedStatusV20()
                11->{webView.settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW;toastV20("Mixed content blocked")}
                12->{webView.settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE;toastV20("Compatibility mode enabled")}
                13->connectionInfoV20()
                14->megaCopy("OLIKH URL",webView.url.orEmpty(),"URL copied")
                15->shareV20()
                16->externalV20()
                17->toastV20("Incognito: "+if(activeTab?.incognito==true)"YES" else "NO")
                18->createNewTab(incognito=true)
                19->clearPrivacyBundleV20()
                20->megaCopy("OLIKH privacy report",privacyReportV20(),"Privacy report copied")
                21->toastV20("V20 privacy tools ready")
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun toastV20(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()

    private fun clearCookiesV20(){
        android.webkit.CookieManager.getInstance().removeAllCookies{toastV20("Cookies cleared")}
        android.webkit.CookieManager.getInstance().flush()
    }

    private fun clearSiteDataV20(){
        webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}",null)
        toastV20("Current site storage cleared")
    }

    private fun toggleThirdPartyV20(){
        val cm=android.webkit.CookieManager.getInstance()
        val next=!cm.acceptThirdPartyCookies(webView)
        cm.setAcceptThirdPartyCookies(webView,next)
        toastV20("Third-party cookies: "+if(next)"ALLOWED" else "BLOCKED")
    }

    private fun mixedStatusV20(){
        val t=when(webView.settings.mixedContentMode){
            android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW->"BLOCKED"
            android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE->"COMPATIBILITY"
            else->"ALLOWED"
        }
        toastV20("Mixed content: $t")
    }

    private fun connectionInfoV20(){
        val u=android.net.Uri.parse(webView.url.orEmpty())
        val t="Scheme: ${u.scheme.orEmpty()}\nHost: ${u.host.orEmpty()}\nHTTPS: ${u.scheme.equals("https",true)}\nIncognito: ${activeTab?.incognito==true}"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Connection info").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun shareV20(){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV20("No URL");return}
        startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply{
            type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,u)
        },"Share page"))
    }

    private fun externalV20(){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV20("No URL");return}
        try{startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(u)))}
        catch(e:Exception){toastV20("No external browser")}
    }

    private fun clearPrivacyBundleV20(){
        webView.clearCache(true);webView.clearFormData()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        historyManager.clear()
        toastV20("Privacy data bundle cleared")
    }

    private fun privacyReportV20():String{
        val u=android.net.Uri.parse(webView.url.orEmpty())
        val third=android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(webView)
        val mixed=when(webView.settings.mixedContentMode){
            android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW->"Blocked"
            android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE->"Compatibility"
            else->"Allowed"
        }
        return "Site: ${u.host.orEmpty()}\nHTTPS: ${u.scheme.equals("https",true)}\nIncognito: ${activeTab?.incognito==true}\nJavaScript: ${webView.settings.javaScriptEnabled}\nThird-party cookies: $third\nMixed content: $mixed\nBlocker: ${olikhBlocker.isEnabled()}"
    }

    private fun privacyReportDialogV20(){
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Privacy report").setMessage(privacyReportV20())
            .setPositiveButton("Copy"){_,_->megaCopy("OLIKH privacy report",privacyReportV20(),"Privacy report copied")}
            .setNegativeButton("Close",null).show()
    }

    private fun showDownloadPermissionsV19() {
        val options=arrayOf(
            "Download manager","Download summary","Open Android Downloads","Clear download history",
            "Current site permissions","Open OLIKH app permissions","Camera permission status",
            "Microphone permission status","Location permission status","Request camera permission",
            "Request microphone permission","Request location permission","File upload readiness",
            "Open document picker","Open image picker","Fullscreen status","Enter video fullscreen",
            "Exit fullscreen","Pause page media","Resume page media","Mute page media",
            "Unmute page media","Save recovery snapshot","Restore recovery snapshot",
            "Crash recovery status","Current tab diagnostics","Copy V19 report","V19 status"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Download & permissions V19").setItems(options){_,i->
            when(i){
                0->showDownloads()
                1->downloadSummaryV19()
                2->openDownloadsV19()
                3->{getSharedPreferences("olikh_downloads",MODE_PRIVATE).edit().clear().apply();toastV19("Download history cleared")}
                4->sitePermissionsV19()
                5->openPermissionsV19()
                6->permissionStatusV19(android.Manifest.permission.CAMERA,"Camera")
                7->permissionStatusV19(android.Manifest.permission.RECORD_AUDIO,"Microphone")
                8->permissionStatusV19(android.Manifest.permission.ACCESS_FINE_LOCATION,"Location")
                9->requestPermissionV19(android.Manifest.permission.CAMERA,"Camera")
                10->requestPermissionV19(android.Manifest.permission.RECORD_AUDIO,"Microphone")
                11->requestPermissionV19(android.Manifest.permission.ACCESS_FINE_LOCATION,"Location")
                12->fileUploadStatusV19()
                13->openPickerV19("*/*")
                14->openPickerV19("image/*")
                15->toastV19("Fullscreen: "+if(fullscreenView!=null)"ACTIVE" else "INACTIVE")
                16->mediaV19("let v=document.querySelector('video');if(v&&v.requestFullscreen)v.requestFullscreen()","Fullscreen requested")
                17->mediaV19("if(document.fullscreenElement)document.exitFullscreen()","Fullscreen exit requested")
                18->mediaV19("document.querySelectorAll('video,audio').forEach(e=>e.pause())","Media paused")
                19->mediaV19("document.querySelectorAll('video,audio').forEach(e=>e.play().catch(()=>{}))","Resume requested")
                20->mediaV19("document.querySelectorAll('video,audio').forEach(e=>e.muted=true)","Media muted")
                21->mediaV19("document.querySelectorAll('video,audio').forEach(e=>e.muted=false)","Media unmuted")
                22->saveRecoveryV19()
                23->restoreRecoveryV19()
                24->recoveryStatusV19()
                25->tabDiagnosticsV19()
                26->megaCopy("OLIKH V19",reportV19(),"V19 report copied")
                27->statusV19()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun prefsV19()=getSharedPreferences("olikh_v19",MODE_PRIVATE)
    private fun toastV19(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()

    private fun downloadSummaryV19(){
        val prefs=getSharedPreferences("olikh_downloads",MODE_PRIVATE)
        val ids=prefs.all.keys.filter{it.startsWith("download_")}
        val manager=getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var active=0
        var complete=0
        var failed=0
        ids.forEach{key->
            val id=key.removePrefix("download_").toLongOrNull() ?: return@forEach
            runCatching{
                manager.query(DownloadManager.Query().setFilterById(id))?.use{c->
                    if(c.moveToFirst()){
                        val x=c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        when(if(x>=0)c.getInt(x) else -1){
                            DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PENDING,DownloadManager.STATUS_PAUSED->active++
                            DownloadManager.STATUS_SUCCESSFUL->complete++
                            DownloadManager.STATUS_FAILED->failed++
                        }
                    }
                }
            }
        }
        val t="Tracked: ${ids.size}\nActive/queued: $active\nComplete: $complete\nFailed: $failed"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Download summary").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun openDownloadsV19(){
        try{startActivity(android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))}
        catch(e:Exception){toastV19("Downloads app unavailable")}
    }

    private fun hasPermissionV19(p:String)=
        androidx.core.content.ContextCompat.checkSelfPermission(this,p)==android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun sitePermissionsV19(){
        val host=try{android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty()}catch(e:Exception){""}
        val t="Site: $host\nCamera: ${hasPermissionV19(android.Manifest.permission.CAMERA)}\nMicrophone: ${hasPermissionV19(android.Manifest.permission.RECORD_AUDIO)}\nLocation: ${hasPermissionV19(android.Manifest.permission.ACCESS_FINE_LOCATION)}"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Current site permissions").setMessage(t)
            .setPositiveButton("App permissions"){_,_->openPermissionsV19()}.setNegativeButton("Close",null).show()
    }

    private fun openPermissionsV19(){
        startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+packageName)))
    }

    private fun permissionStatusV19(p:String,name:String){
        toastV19("$name permission: "+if(hasPermissionV19(p))"GRANTED" else "NOT GRANTED")
    }

    private fun requestPermissionV19(p:String,name:String){
        if(hasPermissionV19(p)){toastV19("$name already granted");return}
        androidx.core.app.ActivityCompat.requestPermissions(this,arrayOf(p),7190)
    }

    private fun fileUploadStatusV19(){
        val t="File chooser: "+if(fileUploadCallback!=null)"ACTIVE" else "READY"+"\nRequest code: $fileChooserRequestCode"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("File upload").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun openPickerV19(type:String){
        val i=android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            this.type=type
        }
        try{startActivityForResult(i,7191)}catch(e:Exception){toastV19("Document picker unavailable")}
    }

    private fun mediaV19(js:String,msg:String){webView.evaluateJavascript(js,null);toastV19(msg)}

    private fun currentUrlsV19():List<String> =
        tabs.mapNotNull{it.webView.url}.filter{it.startsWith("http://")||it.startsWith("https://")}.distinct()

    private fun saveRecoveryV19(){
        val urls=currentUrlsV19()
        prefsV19().edit().putString("recovery",urls.joinToString("\n")).putLong("saved_at",System.currentTimeMillis()).apply()
        toastV19("Recovery saved: "+urls.size+" tabs")
    }

    private fun restoreRecoveryV19(){
        val urls=prefsV19().getString("recovery","").orEmpty().split("\n").map{it.trim()}.filter{it.isNotBlank()}
        if(urls.isEmpty()){toastV19("No recovery snapshot");return}
        urls.forEach{createNewTab(initialUrl=it)}
        toastV19("Recovery restored")
    }

    private fun recoveryStatusV19(){
        val count=prefsV19().getString("recovery","").orEmpty().split("\n").count{it.isNotBlank()}
        val saved=prefsV19().getLong("saved_at",0L)
        val t="Saved tabs: $count\nSnapshot: "+if(saved>0)java.util.Date(saved).toString() else "None"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Crash recovery").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun tabDiagnosticsV19(){
        val t="Tabs: ${tabs.size}\nActive index: $activeTabIndex\nIncognito: ${activeTab?.incognito==true}\nURL: ${webView.url.orEmpty()}\nBack: ${webView.canGoBack()}\nForward: ${webView.canGoForward()}\nProgress: ${webView.progress}%"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Tab diagnostics").setMessage(t)
            .setPositiveButton("Copy"){_,_->megaCopy("OLIKH diagnostics",t,"Diagnostics copied")}
            .setNegativeButton("Close",null).show()
    }

    private fun reportV19():String =
        "Downloads tracked: "+getSharedPreferences("olikh_downloads",MODE_PRIVATE).all.size+
        "\nCamera: "+hasPermissionV19(android.Manifest.permission.CAMERA)+
        "\nMicrophone: "+hasPermissionV19(android.Manifest.permission.RECORD_AUDIO)+
        "\nLocation: "+hasPermissionV19(android.Manifest.permission.ACCESS_FINE_LOCATION)+
        "\nFullscreen: "+(fullscreenView!=null)+
        "\nOpen tabs: "+tabs.size

    private fun statusV19(){
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("V19 status").setMessage(reportV19()).setPositiveButton("Close",null).show()
    }

    private fun showPageUtilityV18() {
        val options=arrayOf(
            "Translate page","Translate selection","QR current URL","Search selected text",
            "Copy selected text","Extract links","Extract images","Extract headings",
            "Extract emails","Extract phone numbers","Copy page text","Word count",
            "Reading time","Page diagnostics","Dark page","Light page","Sepia page",
            "High contrast","Reset page style","Disable animations","Hide images",
            "Show images","Hide videos","Show videos","Zoom 80%","Zoom 100%",
            "Zoom 125%","Zoom 150%","Open externally","V18 status"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Page utility V18").setItems(options){_,i->
            when(i){
                0->translatePageV18()
                1->translateSelectionV18()
                2->qrV18()
                3->selectionV18("search")
                4->selectionV18("copy")
                5->extractV18("links")
                6->extractV18("images")
                7->extractV18("headings")
                8->extractV18("emails")
                9->extractV18("phones")
                10->extractV18("text")
                11->statsV18(false)
                12->statsV18(true)
                13->diagnosticsV18()
                14->styleV18("document.documentElement.style.filter='brightness(.72)'")
                15->styleV18("document.documentElement.style.filter='brightness(1.18)'")
                16->styleV18("document.body.style.background='#f4ecd8';document.body.style.color='#3b2f2f'")
                17->styleV18("document.documentElement.style.filter='contrast(1.6)'")
                18->{val u=webView.url.orEmpty();if(u.isNotBlank())webView.loadUrl(u)}
                19->styleV18("document.querySelectorAll('*').forEach(e=>{e.style.animation='none';e.style.transition='none'})")
                20->styleV18("document.querySelectorAll('img,picture').forEach(e=>e.style.display='none')")
                21->styleV18("document.querySelectorAll('img,picture').forEach(e=>e.style.display='')")
                22->styleV18("document.querySelectorAll('video').forEach(e=>e.style.display='none')")
                23->styleV18("document.querySelectorAll('video').forEach(e=>e.style.display='')")
                24->{webView.settings.textZoom=80;toastV18("Zoom 80%")}
                25->{webView.settings.textZoom=100;toastV18("Zoom 100%")}
                26->{webView.settings.textZoom=125;toastV18("Zoom 125%")}
                27->{webView.settings.textZoom=150;toastV18("Zoom 150%")}
                28->openExternalV18()
                29->statusV18()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun toastV18(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()

    private fun translatePageV18(){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV18("No page");return}
        webView.loadUrl("https://translate.google.com/translate?sl=auto&tl=en&u="+android.net.Uri.encode(u))
    }

    private fun translateSelectionV18(){
        webView.evaluateJavascript("window.getSelection().toString()"){r->
            val q=r.trim('"').replace("\\n"," ").trim()
            if(q.isBlank())toastV18("Select text first")
            else webView.loadUrl("https://translate.google.com/?sl=auto&tl=en&text="+android.net.Uri.encode(q))
        }
    }

    private fun qrV18(){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV18("No URL");return}
        createNewTab(initialUrl="https://quickchart.io/qr?size=300&text="+android.net.Uri.encode(u))
    }

    private fun selectionV18(mode:String){
        webView.evaluateJavascript("window.getSelection().toString()"){r->
            val t=r.trim('"').replace("\\n"," ").trim()
            if(t.isBlank())toastV18("Select text first")
            else if(mode=="search")createNewTab(initialUrl="https://www.google.com/search?q="+android.net.Uri.encode(t))
            else megaCopy("OLIKH selection",t,"Selection copied")
        }
    }

    private fun extractV18(kind:String){
        val js=when(kind){
            "links"->"(function(){return Array.from(document.links).map(a=>a.href).filter(Boolean).slice(0,300).join('\\n')})()"
            "images"->"(function(){return Array.from(document.images).map(a=>a.src).filter(Boolean).slice(0,300).join('\\n')})()"
            "headings"->"(function(){return Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6')).map(a=>a.innerText.trim()).filter(Boolean).slice(0,300).join('\\n')})()"
            "emails"->"(function(){let m=document.body.innerText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/gi)||[];return [...new Set(m)].join('\\n')})()"
            "phones"->"(function(){let m=document.body.innerText.match(/\\+?[0-9][0-9 ()-]{7,}[0-9]/g)||[];return [...new Set(m)].slice(0,200).join('\\n')})()"
            else->"(function(){return document.body.innerText.slice(0,50000)})()"
        }
        webView.evaluateJavascript(js){r->
            val t=r.trim('"').replace("\\n","\n").replace("\\t","\t")
            megaCopy("OLIKH "+kind,t,kind+" copied")
        }
    }

    private fun statsV18(reading:Boolean){
        webView.evaluateJavascript("(function(){return (document.body.innerText.trim().match(/\\S+/g)||[]).length})()"){r->
            val w=r.toIntOrNull() ?: 0
            val msg=if(reading)"Approx reading time: "+kotlin.math.max(1,(w+199)/200)+" min" else "Words: "+w
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(if(reading)"Reading time" else "Word count").setMessage(msg).setPositiveButton("Close",null).show()
        }
    }

    private fun diagnosticsV18(){
        webView.evaluateJavascript("(function(){return 'Links: '+document.links.length+' | Images: '+document.images.length+' | Scripts: '+document.scripts.length+' | Frames: '+document.querySelectorAll('iframe').length})()"){r->
            val t="Title: "+webView.title.orEmpty()+"\nURL: "+webView.url.orEmpty()+"\n"+r.trim('"')
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Page diagnostics").setMessage(t)
                .setPositiveButton("Copy"){_,_->megaCopy("OLIKH diagnostics",t,"Diagnostics copied")}.setNegativeButton("Close",null).show()
        }
    }

    private fun styleV18(js:String){webView.evaluateJavascript(js,null);toastV18("Page style applied")}

    private fun openExternalV18(){
        try{startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(webView.url.orEmpty())))}
        catch(e:Exception){toastV18("No external app")}
    }

    private fun statusV18(){
        val t="Title: "+webView.title.orEmpty()+"\nURL: "+webView.url.orEmpty()+"\nText zoom: "+webView.settings.textZoom+"%"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("V18 status").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun showSecurityCenterV17() {
        val options=arrayOf(
            "Security dashboard","HTTPS-only ON/OFF","Upgrade page to HTTPS","Cookie status",
            "Accept cookies ON/OFF","Third-party cookies ON/OFF","Clear site cookies","Clear all cookies",
            "Clear WebView storage","Permission center","Open Android app settings","Desktop UA",
            "Mobile UA","Reset UA","Copy UA","Media dashboard","Pause all media","Resume all media",
            "Mute all media","Unmute all media","Disable autoplay","Enable autoplay","Video fullscreen",
            "Exit fullscreen","Open Downloads","Share page","Copy page URL","Backup browser data",
            "V17 status"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Security center V17").setItems(options){_,i->
            when(i){
                0->securityDashboardV17()
                1->toggleHttpsV17()
                2->upgradeHttpsV17()
                3->cookieStatusV17()
                4->{val c=android.webkit.CookieManager.getInstance();val n=!c.acceptCookie();c.setAcceptCookie(n);toastV17("Cookies: "+n)}
                5->{val c=android.webkit.CookieManager.getInstance();val n=!c.acceptThirdPartyCookies(webView);c.setAcceptThirdPartyCookies(webView,n);toastV17("Third-party cookies: "+n)}
                6->clearSiteCookiesV17()
                7->{android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.CookieManager.getInstance().flush();toastV17("All cookies cleared")}
                8->{android.webkit.WebStorage.getInstance().deleteAllData();toastV17("Web storage cleared")}
                9->permissionCenterV17()
                10->openAppSettingsV17()
                11->setDesktopUaV17()
                12->setMobileUaV17()
                13->{webView.settings.userAgentString=null;webView.reload();toastV17("UA reset")}
                14->megaCopy("OLIKH UA",webView.settings.userAgentString.orEmpty(),"UA copied")
                15->mediaDashboardV17()
                16->mediaV17("document.querySelectorAll('video,audio').forEach(e=>e.pause())","Media paused")
                17->mediaV17("document.querySelectorAll('video,audio').forEach(e=>e.play().catch(()=>{}))","Resume requested")
                18->mediaV17("document.querySelectorAll('video,audio').forEach(e=>e.muted=true)","Muted")
                19->mediaV17("document.querySelectorAll('video,audio').forEach(e=>e.muted=false)","Unmuted")
                20->mediaV17("document.querySelectorAll('video,audio').forEach(e=>{e.autoplay=false;e.pause()})","Autoplay disabled")
                21->mediaV17("document.querySelectorAll('video,audio').forEach(e=>e.autoplay=true)","Autoplay enabled")
                22->mediaV17("let v=document.querySelector('video');if(v&&v.requestFullscreen)v.requestFullscreen()","Fullscreen requested")
                23->mediaV17("if(document.fullscreenElement)document.exitFullscreen()","Fullscreen exit requested")
                24->showDownloads()
                25->shareCurrentPage()
                26->copyCurrentUrl()
                27->backupV17()
                28->statusV17()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun prefsV17()=getSharedPreferences("olikh_v17",MODE_PRIVATE)
    private fun toastV17(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
    private fun hostV17()=try{android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty()}catch(e:Exception){""}

    private fun securityReportV17():String{
        val c=android.webkit.CookieManager.getInstance()
        return "Host: "+hostV17()+"\nHTTPS: "+webView.url.orEmpty().startsWith("https://")+
            "\nHTTPS-only: "+prefsV17().getBoolean("https_only",false)+"\nCookies: "+c.acceptCookie()+
            "\nThird-party: "+c.acceptThirdPartyCookies(webView)+"\nJavaScript: "+webView.settings.javaScriptEnabled+
            "\nDOM storage: "+webView.settings.domStorageEnabled
    }

    private fun securityDashboardV17(){
        val r=securityReportV17()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Security dashboard").setMessage(r)
            .setPositiveButton("Copy"){_,_->megaCopy("OLIKH security",r,"Security report copied")}
            .setNegativeButton("Close",null).show()
    }

    private fun toggleHttpsV17(){
        val n=!prefsV17().getBoolean("https_only",false);prefsV17().edit().putBoolean("https_only",n).apply()
        if(n)upgradeHttpsV17();toastV17("HTTPS-only: "+if(n)"ON" else "OFF")
    }

    private fun upgradeHttpsV17(){
        val u=webView.url.orEmpty()
        if(u.startsWith("http://"))webView.loadUrl("https://"+u.removePrefix("http://")) else toastV17(if(u.startsWith("https://"))"Already HTTPS" else "Not HTTP")
    }

    private fun cookieStatusV17(){
        val c=android.webkit.CookieManager.getInstance()
        val t="Cookies: "+c.acceptCookie()+"\nThird-party: "+c.acceptThirdPartyCookies(webView)+"\nSite data: "+c.getCookie(webView.url.orEmpty()).orEmpty().isNotBlank()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Cookie status").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun clearSiteCookiesV17(){
        val c=android.webkit.CookieManager.getInstance();val u=webView.url.orEmpty()
        c.getCookie(u).orEmpty().split(";").map{it.substringBefore("=").trim()}.filter{it.isNotBlank()}.forEach{c.setCookie(u,it+"=; Max-Age=0; Path=/")}
        c.flush();toastV17("Site cookies cleared")
    }

    private fun permissionCenterV17(){
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Permission center")
            .setMessage("Review OLIKH camera, microphone, location and notification permissions in Android App settings.")
            .setPositiveButton("App settings"){_,_->openAppSettingsV17()}.setNegativeButton("Close",null).show()
    }

    private fun openAppSettingsV17(){
        startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+packageName)))
    }

    private fun setDesktopUaV17(){
        webView.settings.userAgentString="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36"
        webView.reload();toastV17("Desktop UA")
    }

    private fun setMobileUaV17(){
        webView.settings.userAgentString="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36"
        webView.reload();toastV17("Mobile UA")
    }

    private fun mediaV17(js:String,msg:String){webView.evaluateJavascript(js,null);toastV17(msg)}

    private fun mediaDashboardV17(){
        webView.evaluateJavascript("(function(){return 'Videos: '+document.querySelectorAll('video').length+' | Audio: '+document.querySelectorAll('audio').length})();"){r->
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Media dashboard").setMessage(r.trim('"')).setPositiveButton("Close",null).show()
        }
    }

    private fun backupV17(){
        val a=getSharedPreferences("olikh_v15",MODE_PRIVATE)
        val b=getSharedPreferences("olikh_v16",MODE_PRIVATE)
        val d="BOOKMARKS="+a.getString("bookmark_folders","").orEmpty()+"\nGROUPS="+a.getString("tab_groups","").orEmpty()+
            "\nPINNED="+a.getString("pinned_tabs","").orEmpty()+"\nSESSION="+a.getString("saved_session","").orEmpty()+
            "\nRECOVERY="+b.getString("recovery_urls","").orEmpty()
        megaCopy("OLIKH backup",d,"Backup copied")
    }

    private fun statusV17(){
        val t=securityReportV17()+"\nUA: "+webView.settings.userAgentString.orEmpty().take(60)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("V17 status").setMessage(t).setPositiveButton("Close",null).show()
    }

    private fun showSmartBrowserV16() {
        val options=arrayOf(
            "Smart tab switcher","Search open tabs","Save recovery session","Restore recovery session",
            "Auto recovery ON/OFF","Search bookmark folders","Reading mode","Reading text 90%",
            "Reading text 110%","Reading text 130%","Reading text 150%","Exit reading mode",
            "Desktop mode for this site ON/OFF","JavaScript for this site ON/OFF",
            "Images for this site ON/OFF","Remember current site settings","Apply saved site settings",
            "Forget current site settings","Privacy dashboard","Clear current site cookies",
            "Clear current site storage","Copy current site host","Copy privacy report",
            "Compact page UI","Comfort page UI","Reset page UI","Reload current tab",
            "Stop current tab","Duplicate current tab","Open current page incognito",
            "Copy all open tab URLs","Save all open tabs as recovery","V16 status"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Smart browser V16").setItems(options){_,w->
            when(w){
                0->smartTabSwitcherV16()
                1->searchOpenTabsV16()
                2->saveRecoveryV16()
                3->restoreRecoveryV16()
                4->toggleAutoRecoveryV16()
                5->searchBookmarksV16()
                6->readingModeV16()
                7->{webView.settings.textZoom=90;toastV16("Text zoom 90%")}
                8->{webView.settings.textZoom=110;toastV16("Text zoom 110%")}
                9->{webView.settings.textZoom=130;toastV16("Text zoom 130%")}
                10->{webView.settings.textZoom=150;toastV16("Text zoom 150%")}
                11->{val u=webView.url.orEmpty();if(u.isNotBlank())webView.loadUrl(u)}
                12->toggleDesktopSiteV16()
                13->{webView.settings.javaScriptEnabled=!webView.settings.javaScriptEnabled;toastV16("JavaScript: "+webView.settings.javaScriptEnabled)}
                14->{webView.settings.loadsImagesAutomatically=!webView.settings.loadsImagesAutomatically;toastV16("Images: "+webView.settings.loadsImagesAutomatically)}
                15->rememberSiteSettingsV16()
                16->applySiteSettingsV16()
                17->forgetSiteSettingsV16()
                18->privacyDashboardV16()
                19->clearSiteCookiesV16()
                20->{webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();true}catch(e){false}",null);toastV16("Site storage cleared")}
                21->megaCopy("OLIKH host",hostV16(),"Host copied")
                22->megaCopy("OLIKH privacy",privacyReportV16(),"Privacy report copied")
                23->{webView.settings.textZoom=90;webView.evaluateJavascript("document.body.style.lineHeight='1.25';document.body.style.zoom='0.92';",null)}
                24->{webView.settings.textZoom=115;webView.evaluateJavascript("document.body.style.lineHeight='1.65';document.body.style.zoom='1';",null)}
                25->{webView.settings.textZoom=100;val u=webView.url.orEmpty();if(u.isNotBlank())webView.loadUrl(u)}
                26->webView.reload()
                27->webView.stopLoading()
                28->duplicateCurrentTab()
                29->{val u=webView.url.orEmpty();if(u.isNotBlank())createNewTab(incognito=true,initialUrl=u)}
                30->megaCopy("OLIKH open tabs",currentOpenUrlsV16().joinToString("\n"),"Open tab URLs copied")
                31->saveRecoveryV16()
                32->statusV16()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun prefsV16()=getSharedPreferences("olikh_v16",MODE_PRIVATE)
    private fun toastV16(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()

    private fun hostV16():String=
        try{android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty()}catch(e:Exception){""}

    private fun currentOpenUrlsV16():List<String> =
        tabs.mapNotNull{it.webView.url}.filter{it.startsWith("http://")||it.startsWith("https://")}.distinct()

    private fun smartTabSwitcherV16(){
        if(tabs.isEmpty()){toastV16("No tabs");return}
        val labels=tabs.mapIndexed{i,t->
            val title=t.webView.title?.takeIf{it.isNotBlank()} ?: "Tab ${i+1}"
            val host=try{android.net.Uri.parse(t.webView.url.orEmpty()).host.orEmpty()}catch(e:Exception){""}
            title+"\n"+host+(if(t.incognito)" • Incognito" else "")
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Smart tabs").setItems(labels){_,i->switchToTab(i)}
            .setNegativeButton("Close",null).show()
    }

    private fun searchOpenTabsV16(){
        val input=android.widget.EditText(this);input.hint="Title or URL"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Search open tabs").setView(input)
            .setPositiveButton("Search"){_,_->
                val q=input.text.toString().trim()
                val hits=tabs.mapIndexedNotNull{i,t->if(q.isNotBlank()&&(t.webView.title.orEmpty()+" "+t.webView.url.orEmpty()).contains(q,true))i else null}
                if(hits.isEmpty())toastV16("No matching tab") else switchToTab(hits.first())
            }.setNegativeButton("Cancel",null).show()
    }

    private fun saveRecoveryV16(){
        val urls=currentOpenUrlsV16()
        prefsV16().edit().putString("recovery_urls",urls.joinToString("\n")).apply()
        toastV16("Recovery saved: "+urls.size)
    }

    private fun restoreRecoveryV16(){
        val urls=prefsV16().getString("recovery_urls","").orEmpty().split("\n").map{it.trim()}.filter{it.isNotBlank()}
        if(urls.isEmpty()){toastV16("No recovery session");return}
        urls.forEach{createNewTab(initialUrl=it)}
        toastV16("Recovery restored")
    }

    private fun toggleAutoRecoveryV16(){
        val now=!prefsV16().getBoolean("auto_recovery",false)
        prefsV16().edit().putBoolean("auto_recovery",now).apply()
        if(now)saveRecoveryV16()
        toastV16("Auto recovery: "+if(now)"ON" else "OFF")
    }

    private fun searchBookmarksV16(){
        val raw=getSharedPreferences("olikh_v15",MODE_PRIVATE).getString("bookmark_folders","").orEmpty()
        val input=android.widget.EditText(this);input.hint="Search folders / URLs"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Search bookmarks").setView(input)
            .setPositiveButton("Search"){_,_->
                val q=input.text.toString().trim()
                val result=raw.split("\n").filter{q.isNotBlank()&&it.contains(q,true)}.take(100).joinToString("\n").ifBlank{"No matches"}
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Results").setMessage(result).setPositiveButton("Close",null).show()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun readingModeV16(){
        val js="document.querySelectorAll('nav,aside,footer,header,form,iframe').forEach(e=>e.style.display='none');document.body.style.maxWidth='820px';document.body.style.margin='0 auto';document.body.style.padding='24px';document.body.style.lineHeight='1.75';document.body.style.fontSize='18px';"
        webView.evaluateJavascript(js,null);toastV16("Reading mode applied")
    }

    private fun toggleDesktopSiteV16(){
        val h=hostV16();if(h.isBlank()){toastV16("No website");return}
        val key="desktop_"+h
        val now=!prefsV16().getBoolean(key,false)
        prefsV16().edit().putBoolean(key,now).apply()
        if(now){
            val ua=webView.settings.userAgentString.orEmpty()
            webView.settings.userAgentString=ua.replace("Mobile","").replace("Android","X11; Linux x86_64")
        }else webView.settings.userAgentString=null
        webView.reload();toastV16("Desktop mode: "+if(now)"ON" else "OFF")
    }

    private fun rememberSiteSettingsV16(){
        val h=hostV16();if(h.isBlank()){toastV16("No website");return}
        prefsV16().edit().putBoolean("js_"+h,webView.settings.javaScriptEnabled)
            .putBoolean("img_"+h,webView.settings.loadsImagesAutomatically)
            .putInt("zoom_"+h,webView.settings.textZoom).putBoolean("has_"+h,true).apply()
        toastV16("Site settings remembered")
    }

    private fun applySiteSettingsV16(){
        val h=hostV16()
        if(h.isBlank()||!prefsV16().getBoolean("has_"+h,false)){toastV16("No saved settings");return}
        webView.settings.javaScriptEnabled=prefsV16().getBoolean("js_"+h,true)
        webView.settings.loadsImagesAutomatically=prefsV16().getBoolean("img_"+h,true)
        webView.settings.textZoom=prefsV16().getInt("zoom_"+h,100)
        webView.reload();toastV16("Site settings applied")
    }

    private fun forgetSiteSettingsV16(){
        val h=hostV16()
        prefsV16().edit().remove("js_"+h).remove("img_"+h).remove("zoom_"+h).remove("has_"+h).remove("desktop_"+h).apply()
        toastV16("Site settings forgotten")
    }

    private fun privacyReportV16():String{
        val cookies=android.webkit.CookieManager.getInstance().getCookie(webView.url.orEmpty()).orEmpty()
        return "Site: "+hostV16()+"\nHTTPS: "+webView.url.orEmpty().startsWith("https://")+
            "\nCookies present: "+cookies.isNotBlank()+"\nJavaScript: "+webView.settings.javaScriptEnabled+
            "\nDOM storage: "+webView.settings.domStorageEnabled+"\nImages: "+webView.settings.loadsImagesAutomatically+
            "\nIncognito: "+(activeTab?.incognito==true)+"\nText zoom: "+webView.settings.textZoom+"%"
    }

    private fun privacyDashboardV16(){
        val r=privacyReportV16()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Privacy dashboard").setMessage(r)
            .setPositiveButton("Copy"){_,_->megaCopy("OLIKH privacy",r,"Privacy report copied")}
            .setNegativeButton("Close",null).show()
    }

    private fun clearSiteCookiesV16(){
        val cm=android.webkit.CookieManager.getInstance()
        val u=webView.url.orEmpty()
        cm.getCookie(u).orEmpty().split(";").map{it.substringBefore("=").trim()}.filter{it.isNotBlank()}.forEach{
            cm.setCookie(u,it+"=; Max-Age=0; Path=/")
        }
        cm.flush();toastV16("Current site cookies cleared")
    }

    private fun statusV16(){
        val saved=prefsV16().getString("recovery_urls","").orEmpty().split("\n").count{it.isNotBlank()}
        val text="Open tabs: "+tabs.size+"\nRecovery tabs: "+saved+"\nAuto recovery: "+
            prefsV16().getBoolean("auto_recovery",false)+"\nCurrent site: "+hostV16()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("V16 status").setMessage(text).setPositiveButton("Close",null).show()
    }

    private fun showLibrarySessionsV15() {
        val options=arrayOf(
            "Pin current tab","Unpin current tab","Show pinned tabs","Open all pinned tabs",
            "Save current tab to session","Save all open tabs to session","Restore saved session",
            "Show saved session","Clear saved session","Copy saved session URLs",
            "Create bookmark folder","Save current page to folder","Show bookmark folders",
            "Open bookmark folder","Rename bookmark folder","Delete bookmark folder",
            "Export bookmark folders","Import bookmark folders","Create tab group",
            "Add current tab to group","Show tab groups","Open tab group","Rename tab group",
            "Delete tab group","Copy tab group URLs"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Library & sessions").setItems(options){_,w->
            when(w){
                0->pinCurrentV15()
                1->unpinCurrentV15()
                2->showUrlListV15("Pinned tabs",readLinesV15("pinned_tabs"))
                3->readLinesV15("pinned_tabs").forEach{createNewTab(initialUrl=it)}
                4->{val u=webView.url.orEmpty();if(u.isNotBlank()){writeLinesV15("saved_session",listOf(u));toastV15("Current tab saved")}}
                5->{val urls=tabs.mapNotNull{it.webView.url}.filter{it.startsWith("http")}.distinct();writeLinesV15("saved_session",urls);toastV15("Open tabs saved: "+urls.size)}
                6->readLinesV15("saved_session").forEach{createNewTab(initialUrl=it)}
                7->showUrlListV15("Saved session",readLinesV15("saved_session"))
                8->{writeLinesV15("saved_session",emptyList());toastV15("Saved session cleared")}
                9->megaCopy("OLIKH session",readLinesV15("saved_session").joinToString("\\n"),"Session URLs copied")
                10->createNamedBucketV15("bookmark_folders","New bookmark folder")
                11->addCurrentToBucketV15("bookmark_folders","Save to bookmark folder")
                12->showBucketsV15("Bookmark folders","bookmark_folders")
                13->openBucketV15("bookmark_folders","Open bookmark folder")
                14->renameBucketV15("bookmark_folders","Rename bookmark folder")
                15->deleteBucketV15("bookmark_folders","Delete bookmark folder")
                16->megaCopy("OLIKH bookmark folders",prefsV15().getString("bookmark_folders","").orEmpty(),"Bookmark folders exported")
                17->importBucketsV15("bookmark_folders","Import bookmark folders")
                18->createNamedBucketV15("tab_groups","New tab group")
                19->addCurrentToBucketV15("tab_groups","Add tab to group")
                20->showBucketsV15("Tab groups","tab_groups")
                21->openBucketV15("tab_groups","Open tab group")
                22->renameBucketV15("tab_groups","Rename tab group")
                23->deleteBucketV15("tab_groups","Delete tab group")
                24->copyBucketV15("tab_groups","Copy tab group URLs")
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun prefsV15()=getSharedPreferences("olikh_v15",MODE_PRIVATE)
    private fun toastV15(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()

    private fun readLinesV15(key:String):MutableList<String>{
        return prefsV15().getString(key,"").orEmpty().split("\\n").map{it.trim()}.filter{it.isNotBlank()}.distinct().toMutableList()
    }

    private fun writeLinesV15(key:String,lines:List<String>){
        prefsV15().edit().putString(key,lines.distinct().joinToString("\\n")).apply()
    }

    private fun pinCurrentV15(){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV15("Nothing to pin");return}
        val x=readLinesV15("pinned_tabs")
        if(u !in x)x.add(u)
        writeLinesV15("pinned_tabs",x);toastV15("Tab pinned")
    }

    private fun unpinCurrentV15(){
        val u=webView.url.orEmpty()
        val x=readLinesV15("pinned_tabs")
        x.removeAll{it==u};writeLinesV15("pinned_tabs",x);toastV15("Tab unpinned")
    }

    private fun showUrlListV15(title:String,urls:List<String>){
        val a=if(urls.isEmpty()) arrayOf("Nothing saved") else urls.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setItems(a){_,i->
            if(urls.isNotEmpty())createNewTab(initialUrl=urls[i])
        }.setNegativeButton("Close",null).show()
    }

    private fun decodeBucketsV15(key:String):MutableMap<String,MutableList<String>>{
        val out=linkedMapOf<String,MutableList<String>>()
        prefsV15().getString(key,"").orEmpty().split("\\n").filter{it.isNotBlank()}.forEach{line->
            val parts=line.split("\\t",limit=2)
            if(parts.isNotEmpty()){
                val name=parts[0].trim()
                val urls=if(parts.size>1)parts[1].split("||").map{it.trim()}.filter{it.isNotBlank()}.toMutableList() else mutableListOf()
                if(name.isNotBlank())out[name]=urls
            }
        }
        return out
    }

    private fun saveBucketsV15(key:String,b:Map<String,List<String>>){
        val raw=b.entries.joinToString("\\n"){e->
            e.key.replace("\\n"," ").replace("\\t"," ")+"\\t"+e.value.distinct().joinToString("||")
        }
        prefsV15().edit().putString(key,raw).apply()
    }

    private fun createNamedBucketV15(key:String,title:String){
        val input=android.widget.EditText(this)
        input.hint="Name"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Create"){_,_->
            val n=input.text.toString().trim()
            if(n.isNotBlank()){val b=decodeBucketsV15(key);if(!b.containsKey(n))b[n]=mutableListOf();saveBucketsV15(key,b);toastV15("Created: "+n)}
        }.setNegativeButton("Cancel",null).show()
    }

    private fun chooseBucketV15(key:String,title:String,done:(String)->Unit){
        val b=decodeBucketsV15(key)
        if(b.isEmpty()){toastV15("Create one first");return}
        val names=b.keys.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setItems(names){_,i->done(names[i])}.setNegativeButton("Cancel",null).show()
    }

    private fun addCurrentToBucketV15(key:String,title:String){
        val u=webView.url.orEmpty()
        if(u.isBlank()){toastV15("No page URL");return}
        chooseBucketV15(key,title){n->
            val b=decodeBucketsV15(key);val x=b[n]?:mutableListOf()
            if(u !in x)x.add(u);b[n]=x;saveBucketsV15(key,b);toastV15("Saved to "+n)
        }
    }

    private fun showBucketsV15(title:String,key:String){
        val b=decodeBucketsV15(key)
        val text=if(b.isEmpty())"Nothing saved" else b.entries.joinToString("\\n"){it.key+" • "+it.value.size+" item(s)"}
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Close",null).show()
    }

    private fun openBucketV15(key:String,title:String){
        chooseBucketV15(key,title){n->decodeBucketsV15(key)[n].orEmpty().forEach{createNewTab(initialUrl=it)}}
    }

    private fun deleteBucketV15(key:String,title:String){
        chooseBucketV15(key,title){n->val b=decodeBucketsV15(key);b.remove(n);saveBucketsV15(key,b);toastV15("Deleted: "+n)}
    }

    private fun renameBucketV15(key:String,title:String){
        chooseBucketV15(key,title){old->
            val input=android.widget.EditText(this);input.setText(old)
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Rename"){_,_->
                val n=input.text.toString().trim()
                if(n.isNotBlank()&&n!=old){val b=decodeBucketsV15(key);val v=b.remove(old).orEmpty().toMutableList();b[n]=v;saveBucketsV15(key,b);toastV15("Renamed")}
            }.setNegativeButton("Cancel",null).show()
        }
    }

    private fun copyBucketV15(key:String,title:String){
        chooseBucketV15(key,title){n->megaCopy("OLIKH "+n,decodeBucketsV15(key)[n].orEmpty().joinToString("\\n"),"URLs copied")}
    }

    private fun importBucketsV15(key:String,title:String){
        val input=android.widget.EditText(this)
        input.hint="Paste exported folder data"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Import"){_,_->
            val raw=input.text.toString().trim()
            if(raw.isNotBlank()){prefsV15().edit().putString(key,raw).apply();toastV15("Imported")}
        }.setNegativeButton("Cancel",null).show()
    }

    private fun showPowerControlsV14() {
        val options=arrayOf(
            "JavaScript ON/OFF","Images ON/OFF","DOM storage ON/OFF","Text zoom 80%",
            "Text zoom 100%","Text zoom 120%","Text zoom 150%","Wide viewport ON/OFF",
            "Overview mode ON/OFF","Built-in zoom ON/OFF","Media gesture ON/OFF",
            "Cache: Default","Cache: No cache","Cache: Cache else network","Clear page cache",
            "Clear cookies","Clear WebView storage","Clear form data","Clear SSL preferences",
            "Custom user agent","Reset user agent","Copy user agent","Force dark page",
            "Light page override","Reset page colors","Disable autoplay","Mute media",
            "Unmute media","Pause media","Resume media","Stop loading","Reload bypass cache",
            "Page source","Page info","Save current page session","Restore saved page",
            "Copy saved session URL","Open page incognito","Site settings summary"
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Power controls").setItems(options){_,w->
            when(w){
                0->{webView.settings.javaScriptEnabled=!webView.settings.javaScriptEnabled;toastV14("JavaScript: "+webView.settings.javaScriptEnabled)}
                1->{webView.settings.loadsImagesAutomatically=!webView.settings.loadsImagesAutomatically;toastV14("Images: "+webView.settings.loadsImagesAutomatically)}
                2->{webView.settings.domStorageEnabled=!webView.settings.domStorageEnabled;toastV14("DOM storage: "+webView.settings.domStorageEnabled)}
                3->webView.settings.textZoom=80
                4->webView.settings.textZoom=100
                5->webView.settings.textZoom=120
                6->webView.settings.textZoom=150
                7->{webView.settings.useWideViewPort=!webView.settings.useWideViewPort;toastV14("Wide viewport: "+webView.settings.useWideViewPort)}
                8->{webView.settings.loadWithOverviewMode=!webView.settings.loadWithOverviewMode;toastV14("Overview: "+webView.settings.loadWithOverviewMode)}
                9->{webView.settings.builtInZoomControls=!webView.settings.builtInZoomControls;webView.settings.displayZoomControls=false;toastV14("Zoom controls: "+webView.settings.builtInZoomControls)}
                10->{webView.settings.mediaPlaybackRequiresUserGesture=!webView.settings.mediaPlaybackRequiresUserGesture;toastV14("Media gesture: "+webView.settings.mediaPlaybackRequiresUserGesture)}
                11->{webView.settings.cacheMode=android.webkit.WebSettings.LOAD_DEFAULT;toastV14("Cache default")}
                12->{webView.settings.cacheMode=android.webkit.WebSettings.LOAD_NO_CACHE;toastV14("No cache")}
                13->{webView.settings.cacheMode=android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK;toastV14("Cache else network")}
                14->{webView.clearCache(true);toastV14("Cache cleared")}
                15->{android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.CookieManager.getInstance().flush();toastV14("Cookies cleared")}
                16->{android.webkit.WebStorage.getInstance().deleteAllData();toastV14("Web storage cleared")}
                17->{webView.clearFormData();toastV14("Form data cleared")}
                18->{webView.clearSslPreferences();toastV14("SSL preferences cleared")}
                19->showUserAgentV14()
                20->{webView.settings.userAgentString=null;webView.reload();toastV14("User agent reset")}
                21->megaCopy("OLIKH user agent",webView.settings.userAgentString.orEmpty(),"User agent copied")
                22->jsV14("document.documentElement.style.filter='invert(1) hue-rotate(180deg)';document.querySelectorAll('img,video,picture').forEach(e=>e.style.filter='invert(1) hue-rotate(180deg)')")
                23->jsV14("document.body.style.background='white';document.body.style.color='black'")
                24->jsV14("document.documentElement.style.filter='';document.body.style.background='';document.body.style.color='';document.querySelectorAll('img,video,picture').forEach(e=>e.style.filter='')")
                25->jsV14("document.querySelectorAll('video,audio').forEach(e=>{e.autoplay=false;e.pause()})")
                26->jsV14("document.querySelectorAll('video,audio').forEach(e=>e.muted=true)")
                27->jsV14("document.querySelectorAll('video,audio').forEach(e=>e.muted=false)")
                28->jsV14("document.querySelectorAll('video,audio').forEach(e=>e.pause())")
                29->jsV14("document.querySelectorAll('video,audio').forEach(e=>e.play().catch(()=>{}))")
                30->{webView.stopLoading();toastV14("Loading stopped")}
                31->{val u=webView.url.orEmpty();if(u.isNotBlank())webView.loadUrl(u,mapOf("Cache-Control" to "no-cache"))}
                32->{val u=webView.url.orEmpty();if(u.startsWith("http"))createNewTab(initialUrl="view-source:"+u)}
                33->showPageInfoV14()
                34->{getSharedPreferences("olikh_v14",MODE_PRIVATE).edit().putString("session_url",webView.url.orEmpty()).apply();toastV14("Page saved")}
                35->{val u=getSharedPreferences("olikh_v14",MODE_PRIVATE).getString("session_url","").orEmpty();if(u.isNotBlank())createNewTab(initialUrl=u)else toastV14("No saved page")}
                36->{val u=getSharedPreferences("olikh_v14",MODE_PRIVATE).getString("session_url","").orEmpty();megaCopy("OLIKH session",u,"Session URL copied")}
                37->{val u=webView.url.orEmpty();if(u.isNotBlank())createNewTab(incognito=true,initialUrl=u)}
                38->showSiteSummaryV14()
            }
        }.setNegativeButton("Close",null).show()
    }

    private fun toastV14(text:String)=Toast.makeText(this,text,Toast.LENGTH_SHORT).show()

    private fun jsV14(code:String){
        webView.evaluateJavascript("(function(){try{"+code+";return true}catch(e){return false}})();",null)
        toastV14("Done")
    }

    private fun showUserAgentV14(){
        val input=android.widget.EditText(this)
        input.setText(webView.settings.userAgentString.orEmpty())
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Custom user agent").setView(input)
            .setPositiveButton("Apply"){_,_->val ua=input.text.toString().trim();if(ua.isNotBlank()){webView.settings.userAgentString=ua;webView.reload()}}
            .setNegativeButton("Cancel",null).show()
    }

    private fun showPageInfoV14(){
        val info="Title: "+webView.title.orEmpty()+"\\nURL: "+webView.url.orEmpty()+
            "\\nProgress: "+webView.progress+"%\\nText zoom: "+webView.settings.textZoom+"%"+
            "\\nJavaScript: "+webView.settings.javaScriptEnabled+"\\nDOM storage: "+webView.settings.domStorageEnabled+
            "\\nImages: "+webView.settings.loadsImagesAutomatically
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Page info").setMessage(info)
            .setPositiveButton("Copy"){_,_->megaCopy("OLIKH page info",info,"Page info copied")}.setNegativeButton("Close",null).show()
    }

    private fun showSiteSummaryV14(){
        val host=try{android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty()}catch(e:Exception){""}
        val text="Site: "+host+"\\nJavaScript: "+webView.settings.javaScriptEnabled+
            "\\nImages: "+webView.settings.loadsImagesAutomatically+"\\nDOM storage: "+webView.settings.domStorageEnabled+
            "\\nText zoom: "+webView.settings.textZoom+"%\\nCache mode: "+webView.settings.cacheMode+
            "\\nMedia gesture: "+webView.settings.mediaPlaybackRequiresUserGesture
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Site settings summary").setMessage(text)
            .setPositiveButton("Copy"){_,_->megaCopy("OLIKH site settings",text,"Site settings copied")}.setNegativeButton("Close",null).show()
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
