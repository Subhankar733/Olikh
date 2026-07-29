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

        val quickAccessHtml =
            buildQuickAccessHtml()

        val html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport"
      content="width=device-width, initial-scale=1.0, viewport-fit=cover">

<meta name="color-scheme" content="dark">

<style>

* {
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
}

html, body {
    margin: 0;
    width: 100%;
    min-height: 100%;
    background:
        radial-gradient(
            circle at 50% -10%,
            #232833 0%,
            #101319 38%,
            #090b0f 72%
        );

    color: #f7f8fa;

    font-family:
        -apple-system,
        BlinkMacSystemFont,
        "Segoe UI",
        sans-serif;
}

body {
    min-height: 100vh;
    padding:
        max(36px, env(safe-area-inset-top))
        22px
        40px;

    display: flex;
    justify-content: center;
}

.page {
    width: 100%;
    max-width: 680px;
}

.brand {
    margin-top: 7vh;
    text-align: center;
}

.logo {
    width: 72px;
    height: 72px;

    margin: 0 auto 18px;

    border-radius: 23px;

    display: flex;
    align-items: center;
    justify-content: center;

    font-size: 29px;
    font-weight: 800;
    letter-spacing: -2px;

    color: #090b0f;

    background:
        linear-gradient(
            145deg,
            #ffffff,
            #cbd0d8
        );

    box-shadow:
        0 18px 50px rgba(0,0,0,.45),
        inset 0 1px 0 rgba(255,255,255,.9);
}

h1 {
    margin: 0;

    font-size: 36px;
    font-weight: 760;

    letter-spacing: -1.4px;
}

.subtitle {
    margin-top: 8px;

    color: #8e96a3;

    font-size: 14px;
}

.search {
    margin-top: 38px;

    display: flex;
    align-items: center;

    height: 58px;

    padding: 0 18px;

    border-radius: 20px;

    background: rgba(255,255,255,.075);

    border:
        1px solid rgba(255,255,255,.10);

    box-shadow:
        0 16px 40px rgba(0,0,0,.25);

    backdrop-filter: blur(22px);
}

.search span {
    font-size: 20px;

    margin-right: 12px;

    opacity: .75;
}

.search input {
    width: 100%;

    border: 0;
    outline: 0;

    background: transparent;

    color: #fff;

    font-size: 16px;
}

.search input::placeholder {
    color: #747c88;
}

.protection {
    margin-top: 22px;

    cursor: pointer;
    transition:
        transform .12s ease,
        background .12s ease;

    padding: 20px;

    border-radius: 22px;

    background:
        linear-gradient(
            145deg,
            rgba(255,255,255,.085),
            rgba(255,255,255,.045)
        );

    border:
        1px solid rgba(255,255,255,.09);

    box-shadow:
        0 18px 45px rgba(0,0,0,.22);
}

.protection:active {
    transform: scale(.985);
}

.protection-top {
    display: flex;
    align-items: center;
}

.shield {
    width: 46px;
    height: 46px;

    border-radius: 15px;

    display: flex;
    align-items: center;
    justify-content: center;

    margin-right: 14px;

    background:
        linear-gradient(
            145deg,
            #e9edf2,
            #aeb5bf
        );

    color: #0b0d10;

    font-size: 21px;
    font-weight: 900;
}

.protection-title {
    font-size: 16px;
    font-weight: 700;
}

.protection-status {
    margin-top: 4px;

    font-size: 13px;

    color: #9098a5;
}

.stats {
    margin-top: 20px;

    display: grid;

    grid-template-columns: 1fr 1fr;

    gap: 10px;
}

.stat {
    padding: 15px;

    border-radius: 16px;

    background:
        rgba(0,0,0,.20);
}

.number {
    font-size: 23px;
    font-weight: 750;
}

.label {
    margin-top: 4px;

    color: #858d99;

    font-size: 12px;
}

.quick {
    margin-top: 28px;
}

.quick-title {
    margin-bottom: 13px;

    color: #8f97a3;

    font-size: 12px;

    font-weight: 700;

    text-transform: uppercase;

    letter-spacing: 1.2px;
}

.grid {
    display: grid;

    grid-template-columns:
        repeat(4, 1fr);

    gap: 11px;
}

.site {
    min-height: 76px;

    border-radius: 18px;

    display: flex;
    flex-direction: column;

    align-items: center;
    justify-content: center;

    text-decoration: none;

    color: #e9ecf0;

    background:
        rgba(255,255,255,.055);

    border:
        1px solid rgba(255,255,255,.07);
}

.site-icon {
    font-size: 21px;
    margin-bottom: 7px;
}

.site-name {
    font-size: 11px;
    color: #a8afb9;
}

.footer {
    margin-top: 34px;

    text-align: center;

    color: #5f6670;

    font-size: 11px;

    letter-spacing: .8px;
}

</style>
</head>

<body>

<div class="page">

    <div class="brand">

        <div class="logo">
            O
        </div>

        <h1>OLIKH</h1>

        <div class="subtitle">
            Private. Fast. Yours.
        </div>

    </div>

    <form class="search"
          onsubmit="
              event.preventDefault();
              var q =
                  document.getElementById('q')
                  .value.trim();

              if(q) {
                  location.href =
                      'olikh://search?q=' +
                      encodeURIComponent(q);
              }
          ">

        <span>&#8981;</span>

        <input
            id="q"
            autocomplete="off"
            placeholder="Search the web">

    </form>

    <div class="protection"
         onclick="location.href='olikh://toggle-blocker'"
         role="button">

        <div class="protection-top">

            <div class="shield">
                $blockerIcon
            </div>

            <div>

                <div class="protection-title">
                    Ad & Tracker Protection
                </div>

                <div class="protection-status">
                    $blockerStatus
                </div>

            </div>

        </div>

        <div class="stats">

            <div class="stat">

                <div class="number">
                    $blockedRequests
                </div>

                <div class="label">
                    Requests blocked
                </div>

            </div>

            <div class="stat">

                <div class="number">
                    $blockedDomains
                </div>

                <div class="label">
                    Protection domains
                </div>

            </div>

        </div>

    </div>

    <div class="quick">

        <div class="quick-title">
            Quick access
        </div>

        <div class="grid">

            $quickAccessHtml

        </div>

    </div>

</div>

</body>
</html>
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
            showOlikhStartPage()
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

            onNewTab = {
                createNewTab()
            }
        ).show()
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
            "Refresh page"
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
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            animateDialogEntrance(dialog)
        }

        dialog.show()
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
                val query = URLEncoder.encode(input, "UTF-8")
                "https://www.google.com/search?q=$query"
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
