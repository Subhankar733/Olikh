package com.subho.olikh

import android.graphics.Bitmap
import android.webkit.WebView

data class BrowserTab(
    var webView: WebView,
    var title: String = "New Tab",
    var url: String = "",
    var failedUrl: String? = null,
    var showingError: Boolean = false,
    var incognito: Boolean = false,
    var favicon: Bitmap? = null,
    var lastAccessed: Long = System.currentTimeMillis()
)
