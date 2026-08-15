package com.subho.olikh

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object OlikhProTools {

    fun toggleReaderMode(webView: WebView?, enabled: Boolean) {
        if (webView == null) return
        if (enabled) {
            val js = """
                (function() {
                    if (document.getElementById('olikh-reader-view')) {
                        document.getElementById('olikh-reader-view').style.display = 'block';
                        return;
                    }
                    var article = document.querySelector('article') || document.querySelector('main') || document.body;
                    var title = document.title;
                    var content = article.innerHTML;

                    var overlay = document.createElement('div');
                    overlay.id = 'olikh-reader-view';
                    overlay.style.position = 'fixed';
                    overlay.style.top = '0';
                    overlay.style.left = '0';
                    overlay.style.width = '100%';
                    overlay.style.height = '100%';
                    overlay.style.backgroundColor = '#0b0f19';
                    overlay.style.color = '#e2e8f0';
                    overlay.style.overflowY = 'auto';
                    overlay.style.padding = '24px 18px';
                    overlay.style.fontSize = '18px';
                    overlay.style.lineHeight = '1.7';
                    overlay.style.zIndex = '999999';
                    overlay.style.fontFamily = 'system-ui, -apple-system, sans-serif';

                    overlay.innerHTML = '<div style="max-width: 680px; margin: 0 auto;">' +
                        '<h1 style="color:#60a5fa; font-size: 24px; margin-bottom: 20px;">' + title + '</h1>' +
                        content + '</div>';

                    // Remove interactive script tags & clutter inside reader
                    var scripts = overlay.getElementsByTagName('script');
                    for (var i = scripts.length - 1; i >= 0; i--) scripts[i].remove();

                    document.body.appendChild(overlay);
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        } else {
            val js = """
                (function() {
                    var v = document.getElementById('olikh-reader-view');
                    if (v) v.remove();
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    fun translatePage(webView: WebView?, targetLang: String = "bn") {
        if (webView == null) return
        val js = """
            (function() {
                var script = document.createElement('script');
                script.type = 'text/javascript';
                script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
                document.head.appendChild(script);

                window.googleTranslateElementInit = function() {
                    new google.translate.TranslateElement({
                        pageLanguage: 'auto',
                        includedLanguages: '$targetLang,en,hi,ar,es',
                        layout: google.translate.TranslateElement.InlineLayout.SIMPLE,
                        autoDisplay: false
                    }, 'google_translate_element');
                };

                var div = document.getElementById('google_translate_element');
                if (!div) {
                    div = document.createElement('div');
                    div.id = 'google_translate_element';
                    div.style.position = 'fixed';
                    div.style.top = '10px';
                    div.style.right = '10px';
                    div.style.zIndex = '999999';
                    document.body.appendChild(div);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun printOrSavePdf(activity: Activity, webView: WebView) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = webView.createPrintDocumentAdapter("OLIKH_Document_" + System.currentTimeMillis())
        val jobName = "OLIKH Print Document"
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    fun capturePageScreenshot(activity: Activity, webView: WebView) {
        runCatching {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)

            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val olikhDir = File(dir, "Olikh")
            if (!olikhDir.exists()) olikhDir.mkdirs()

            val file = File(olikhDir, "Screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(activity, "Saved to Pictures/Olikh", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(activity, "Screenshot failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
