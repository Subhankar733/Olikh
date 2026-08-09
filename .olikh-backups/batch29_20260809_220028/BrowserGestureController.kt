package com.subho.olikh

import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import kotlin.math.abs

class BrowserGestureController(
    private val surface: View,
    private val webView: WebView
) {
    private var downX = 0f
    private var downY = 0f

    fun attach() {
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    false
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY

                    if (abs(dx) > 120f && abs(dx) > abs(dy) * 1.35f) {
                        if (dx > 0f && webView.canGoBack()) {
                            webView.goBack()
                            true
                        } else if (dx < 0f && webView.canGoForward()) {
                            webView.goForward()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> false
                else -> false
            }
        }
    }
}
