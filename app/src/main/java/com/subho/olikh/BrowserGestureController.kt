package com.subho.olikh

import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import kotlin.math.abs
import kotlin.math.max

class BrowserGestureController(
    private val surface: View,
    private val webView: WebView
) {
    private var downX = 0f
    private var downY = 0f
    private var trackingGesture = false

    fun attach() {
        val density = surface.resources.displayMetrics.density
        val touchSlop = 12f * density
        val swipeThreshold = max(96f * density, touchSlop * 4f)
        val horizontalRatio = 1.5f

        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    trackingGesture = event.pointerCount == 1
                    false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    trackingGesture = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (trackingGesture) {
                        val dx = event.x - downX
                        val dy = event.y - downY

                        // Once the gesture is clearly vertical, let WebView own it.
                        if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                            trackingGesture = false
                        }
                    }
                    false
                }

                MotionEvent.ACTION_UP -> {
                    if (!trackingGesture) {
                        return@setOnTouchListener false
                    }

                    val dx = event.x - downX
                    val dy = event.y - downY

                    trackingGesture = false

                    if (
                        abs(dx) > swipeThreshold &&
                        abs(dx) > abs(dy) * horizontalRatio
                    ) {
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

                MotionEvent.ACTION_CANCEL -> {
                    trackingGesture = false
                    false
                }

                else -> false
            }
        }
    }
}
