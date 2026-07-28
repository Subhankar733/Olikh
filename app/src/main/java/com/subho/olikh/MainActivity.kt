package com.subho.olikh

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
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
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.Toast
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

    private val browserPrefs by lazy {
        getSharedPreferences("olikh_browser", MODE_PRIVATE)
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

    private lateinit var btnBookmark: Button

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
        val btnMenu = findViewById<Button>(R.id.btnMenu)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnForward = findViewById<Button>(R.id.btnForward)
        val btnHome = findViewById<Button>(R.id.btnHome)
        val btnReload = findViewById<Button>(R.id.btnReload)
        val btnHistory = findViewById<Button>(R.id.btnHistory)
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

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        installDownloadListener(webView)
        installLongPressActions(webView)

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
                webView.loadUrl(homePage)
            } else {
                webView.restoreState(savedInstanceState)
            }
        }

        btnNewTab.setOnClickListener {
            createNewTab()
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

        btnBookmark.text =
            if (saved) "★" else "☆"

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

        androidx.appcompat.app.AlertDialog.Builder(this)
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
            .show()
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

        androidx.appcompat.app.AlertDialog.Builder(this)
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
            .show()
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

    private fun closeTab(index: Int) {
        val closingTab = tabs.getOrNull(index) ?: return
        val wasActive = index == activeTabIndex

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
        newWebView.loadUrl(initialUrl)
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

    private fun showBrowserMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("New incognito tab")
        popup.menu.add("Find in page")
        popup.menu.add("Share page")
        popup.menu.add("Copy URL")
        popup.menu.add("Page info")
        popup.menu.add("Open in external app")
        popup.menu.add("Save as PDF")
        popup.menu.add("Search engine")
        popup.menu.add("Zoom")

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

                "Page info" -> {
                    showPageInfo()
                    true
                }

                "Open in external app" -> {
                    openInExternalApp()
                    true
                }

                "Save as PDF" -> {
                    savePageAsPdf()
                    true
                }

                "Search engine" -> {
                    showSearchEngineSelector()
                    true
                }

                "Zoom" -> {
                    showZoomMenu()
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

        popup.show()
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

        androidx.appcompat.app.AlertDialog.Builder(this)
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
            .show()
    }

    private fun showZoomMenu() {
        val options = arrayOf(
            "Zoom in",
            "Zoom out",
            "Reset zoom"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
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
            .show()
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
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

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
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

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
                setAcceptThirdPartyCookies(restoredWebView, true)
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

            restoredWebView.webChromeClient =
                object : WebChromeClient() {

                    override fun onProgressChanged(
                        view: WebView?,
                        newProgress: Int
                    ) {
                        if (activeTab === tab) {
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
                        title: String?
                    ) {
                        tab.title = title ?: "New Tab"

                        if (activeTab === tab) {
                            this@MainActivity.title =
                                tab.title
                        }
                    }
                }
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
        findViewById<Button>(R.id.btnBack).isEnabled =
            webView.canGoBack()

        findViewById<Button>(R.id.btnForward).isEnabled =
            webView.canGoForward()

        updateBookmarkButton()
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
        saveTabs()
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        saveTabs()
        super.onStop()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }
}
