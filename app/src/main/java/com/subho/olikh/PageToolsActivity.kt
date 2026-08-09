package com.subho.olikh

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class PageToolsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        header.addView(Button(this).apply {
            text = "‹"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(50), dp(48)))

        header.addView(TextView(this).apply {
            text = "Page Tools"
            textSize = 20f
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))

        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(24))
        }

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        addAction(content, "Read page aloud",
            "Read visible page text with Android Text-to-Speech.") { readPageAloud() }

        addAction(content, "Stop reading",
            "Stop the current Text-to-Speech playback.") {
            tts?.stop()
            toast("Reading stopped")
        }

        addAction(content, "Copy all page text",
            "Copy visible page text to the clipboard.") { copyPageText() }

        addAction(content, "Page information",
            "Show title, URL, visible dimensions and text zoom.") { showPageInfo() }

        addAction(content, "Clear page cache",
            "Clear WebView cache without deleting cookies or site data.") {
            webView.clearCache(true)
            toast("WebView cache cleared")
        }

        addAction(content, "Zoom 80%", "Compact page text.") {
            webView.settings.textZoom = 80
            toast("Text zoom 80%")
        }

        addAction(content, "Zoom 100%", "Restore normal page text size.") {
            webView.settings.textZoom = 100
            toast("Text zoom 100%")
        }

        addAction(content, "Zoom 125%", "Increase page text size.") {
            webView.settings.textZoom = 125
            toast("Text zoom 125%")
        }

        addAction(content, "Zoom 150%", "Large text accessibility mode.") {
            webView.settings.textZoom = 150
            toast("Text zoom 150%")
        }

        addAction(content, "Capture visible page",
            "Save a screenshot of the visible WebView to OLIKH internal storage.") {
            captureVisiblePage()
        }

        addAction(content, "Reload without cache",
            "Clear WebView cache and reload the current page.") {
            webView.clearCache(true)
            webView.reload()
        }

        content.addView(TextView(this).apply {
            text = "OLIKH • Page Tools • Android-native utilities"
            textSize = 10f
            setPadding(dp(4), dp(18), dp(4), 0)
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val url = intent.getStringExtra("url").orEmpty()
        if (url.isNotBlank()) webView.loadUrl(url)

        setContentView(root)
    }

    private fun readPageAloud() {
        webView.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : '';})()"
        ) { raw ->
            val text = decodeJsString(raw).trim()
            if (text.isBlank()) {
                toast("No readable page text found")
                return@evaluateJavascript
            }
            if (!ttsReady) {
                toast("Text-to-Speech is not ready")
                return@evaluateJavascript
            }
            val safe = text.take(3500)
            tts?.speak(safe, TextToSpeech.QUEUE_FLUSH, null, "olikh-page")
            toast(if (text.length > safe.length) "Reading first part" else "Reading page")
        }
    }

    private fun copyPageText() {
        webView.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : '';})()"
        ) { raw ->
            val text = decodeJsString(raw).trim()
            if (text.isBlank()) {
                toast("No page text found")
                return@evaluateJavascript
            }
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("OLIKH page text", text)
            )
            toast("Page text copied")
        }
    }

    private fun showPageInfo() {
        val title = webView.title?.ifBlank { "Untitled" } ?: "Untitled"
        val url = webView.url ?: "Unknown"
        android.app.AlertDialog.Builder(this)
            .setTitle("Page information")
            .setMessage(
                "Title: $title\n\nURL: $url\n\n" +
                    "Visible area: ${webView.width} × ${webView.height}\n" +
                    "Text zoom: ${webView.settings.textZoom}%"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun captureVisiblePage() {
        if (webView.width <= 0 || webView.height <= 0) {
            toast("Page is not ready")
            return
        }

        val bitmap = Bitmap.createBitmap(
            webView.width, webView.height, Bitmap.Config.ARGB_8888
        )
        webView.draw(Canvas(bitmap))

        val dir = File(filesDir, "olikh_page_captures").apply { mkdirs() }
        val file = File(dir, "page_${System.currentTimeMillis()}.png")

        runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            toast("Screenshot saved inside OLIKH")
        }.onFailure {
            bitmap.recycle()
            toast("Screenshot failed")
        }
    }

    private fun decodeJsString(raw: String): String =
        raw.removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0026", "&")

    private fun addAction(
        parent: LinearLayout,
        title: String,
        summary: String,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 14f
        })
        row.addView(TextView(this).apply {
            text = summary
            textSize = 10f
            setPadding(0, dp(4), 0, dp(8))
        })
        row.addView(Button(this).apply {
            text = "RUN"
            setOnClickListener { action() }
        })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(3), 0, dp(3))
        })
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
