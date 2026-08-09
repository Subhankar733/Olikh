package com.subho.olikh

import android.graphics.Bitmap
import android.widget.EditText
import android.print.PrintManager
import android.print.PrintAttributes
import android.content.Intent
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
    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var MOBILE_USER_AGENT: String = ""

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
        MOBILE_USER_AGENT = webView.settings.userAgentString
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

        addAction(content, "Find in page",
            "Search text inside the current webpage and highlight matches.") {
            findInPage()
        }

        addAction(content, "Clear page search",
            "Remove all active in-page search highlights.") {
            webView.clearMatches()
            toast("Page search cleared")
        }

        addAction(content, "Desktop site",
            "Switch the current WebView between mobile and desktop user-agent mode.") {
            setDesktopSite()
        }

        addAction(content, "Save complete page",
            "Save the current page as an offline Web Archive in OLIKH internal storage.") {
            saveCompletePage()
        }

        addAction(content, "Print page",
            "Open Android's system print preview for the current webpage.") {
            printCurrentPage()
        }

        addAction(content, "Share page",
            "Share the current page URL through Android's share sheet.") {
            shareCurrentPage()
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


    private fun findInPage() {
        val input = EditText(this).apply {
            hint = "Find text on this page"
            setSingleLine(true)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Find in page")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Find", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString().trim()
                if (query.isBlank()) {
                    toast("Enter text to search")
                    return@setOnClickListener
                }

                webView.clearMatches()
                webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                    toast(
                        if (numberOfMatches == 0) {
                            "No matches"
                        } else {
                            "${activeMatchOrdinal + 1}/$numberOfMatches matches"
                        }
                    )
                }
                webView.findAllAsync(query)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun setDesktopSite() {
        val settings = webView.settings
        val desktop = settings.userAgentString != DESKTOP_USER_AGENT
        settings.userAgentString = if (desktop) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
        webView.reload()
        toast(if (desktop) "Desktop site enabled" else "Mobile site enabled")
    }

    private fun saveCompletePage() {
        val currentUrl = webView.url
        if (currentUrl.isNullOrBlank()) {
            toast("No page loaded")
            return
        }

        val dir = File(filesDir, "olikh_saved_pages").apply { mkdirs() }
        val safeName = currentUrl
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(80)
        val file = File(dir, "page_${System.currentTimeMillis()}_$safeName")

        runCatching {
            webView.saveWebArchive(file.absolutePath)
            toast("Page saved for offline use")
        }.onFailure {
            toast("Could not save page")
        }
    }

    private fun printCurrentPage() {
        if (webView.url.isNullOrBlank()) {
            toast("No page loaded")
            return
        }

        val printManager = getSystemService(PrintManager::class.java)
        val adapter = webView.createPrintDocumentAdapter("OLIKH-page")
        printManager?.print(
            "OLIKH webpage",
            adapter,
            PrintAttributes.Builder().build()
        )
    }

    private fun shareCurrentPage() {
        val url = webView.url
        if (url.isNullOrBlank()) {
            toast("No page loaded")
            return
        }

        val title = webView.title?.takeIf { it.isNotBlank() } ?: "OLIKH page"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share page"))
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
