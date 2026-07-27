package com.subho.olikh

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var browserContainer: FrameLayout
    private lateinit var btnTabs: Button
    private lateinit var btnNewTab: Button

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0

    private val activeTab: BrowserTab?
        get() = tabs.getOrNull(activeTabIndex)

    private val homePage = "https://www.google.com"

    private var failedUrl: String? = null
    private var showingErrorPage = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar)
        progressBar = findViewById(R.id.progressBar)
        browserContainer = findViewById(R.id.browserContainer)
        btnTabs = findViewById(R.id.btnTabs)
        btnNewTab = findViewById(R.id.btnNewTab)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnForward = findViewById<Button>(R.id.btnForward)
        val btnHome = findViewById<Button>(R.id.btnHome)
        val btnReload = findViewById<Button>(R.id.btnReload)

        tabs.add(
            BrowserTab(
                webView = webView,
                title = "OLIKH",
                url = homePage
            )
        )
        activeTabIndex = 0
        btnTabs.text = tabs.size.toString()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            loadsImagesAutomatically = true
            blockNetworkImage = false

            useWideViewPort = true
            loadWithOverviewMode = true

            builtInZoomControls = true
            displayZoomControls = false

            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true

            allowContentAccess = true
            allowFileAccess = false

            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                if (!showingErrorPage) {
                    progressBar.visibility = View.VISIBLE
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

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!showingErrorPage) {
                    progressBar.progress = newProgress
                    progressBar.visibility =
                        if (newProgress >= 100) View.GONE else View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)

                if (!showingErrorPage) {
                    this@MainActivity.title = title ?: "OLIKH"
                }
            }
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

        if (savedInstanceState == null) {
            webView.loadUrl(homePage)
        } else {
            webView.restoreState(savedInstanceState)
        }

        btnNewTab.setOnClickListener {
            createNewTab()
        }

        btnTabs.setOnClickListener {
            showTabSwitcher()
        }

        btnTabs.setOnLongClickListener {
            closeCurrentTab()
            true
        }

        updateNavigationButtons()
    }

    private fun showTabSwitcher() {
        if (tabs.isEmpty()) return

        val items = tabs.mapIndexed { index, tab ->
            val marker = if (index == activeTabIndex) "●" else "○"

            val displayTitle = tab.title
                .replace("\n", " ")
                .trim()
                .ifBlank {
                    tab.url.ifBlank { "New Tab" }
                }
                .take(45)

            "$marker ${index + 1}. $displayTitle"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Tabs · ${tabs.size}")
            .setItems(items) { _, index ->
                switchToTab(index)
            }
            .setPositiveButton("+ New tab") { _, _ ->
                createNewTab()
            }
            .setNegativeButton("Close current") { _, _ ->
                closeCurrentTab()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun closeCurrentTab() {
        if (tabs.isEmpty()) return

        val closingIndex = activeTabIndex
        val closingTab = tabs.removeAt(closingIndex)

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
    private fun createNewTab() {
        val newWebView = WebView(this)

        newWebView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        newWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            loadsImagesAutomatically = true
            blockNetworkImage = false

            useWideViewPort = true
            loadWithOverviewMode = true

            builtInZoomControls = true
            displayZoomControls = false

            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true

            allowContentAccess = true
            allowFileAccess = false

            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(newWebView, true)
        }

        val tab = BrowserTab(
            webView = newWebView,
            title = "New Tab",
            url = homePage
        )

        tabs.add(tab)
        activeTabIndex = tabs.lastIndex

        newWebView.webViewClient = createTabWebViewClient(tab)

        newWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (activeTab === tab) {
                    progressBar.progress = newProgress
                    progressBar.visibility =
                        if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title ?: "New Tab"

                if (activeTab === tab) {
                    this@MainActivity.title = tab.title
                }
            }
        }

        switchToTab(activeTabIndex)
        newWebView.loadUrl(homePage)
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
    }

    private fun createTabWebViewClient(tab: BrowserTab): WebViewClient {
        return object : WebViewClient() {

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

                if (activeTab === tab) {
                    progressBar.visibility = View.GONE

                    url?.let {
                        if (!addressBar.hasFocus()) {
                            addressBar.setText(it)
                        }
                    }

                    updateNavigationButtons()
                }
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

        addressBar.setText(url)
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

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request?.url?.toString() == "olikh://retry") {
                    retryFailedPage()
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

    private fun updateNavigationButtons() {
        findViewById<Button>(R.id.btnBack).isEnabled =
            webView.canGoBack()

        findViewById<Button>(R.id.btnForward).isEnabled =
            webView.canGoForward()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            showingErrorPage = false
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }
}
