package com.subho.olikh

import android.webkit.WebView

data class BrowserTab(
    val webView: WebView,
    var title: String = "New Tab",
    var url: String = "",
    var failedUrl: String? = null,
    var showingError: Boolean = false,
    var incognito: Boolean = false
)
