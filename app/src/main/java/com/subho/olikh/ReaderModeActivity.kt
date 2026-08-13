package com.subho.olikh

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ReaderModeActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var originalUrl = ""
    private var readerActive = false
    private var showingOriginal = false
    private var fontScale = 1.0f
    private var darkTheme = false
    private var wideLayout = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        originalUrl = intent.getStringExtra("url")?.trim().orEmpty()
        if (originalUrl.isBlank() ||
            (!originalUrl.startsWith("http://", true) &&
             !originalUrl.startsWith("https://", true))) {
            Toast.makeText(this, "Open a normal webpage first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 246, 241))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(24, 26, 31))
        }

        toolbar.addView(Button(this).apply {
            text = "‹"
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))

        toolbar.addView(TextView(this).apply {
            text = "Reader Mode"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))

        toolbar.addView(Button(this).apply {
            text = "A−"
            setTextColor(Color.WHITE)
            setOnClickListener { changeFont(-0.1f) }
        }, LinearLayout.LayoutParams(dp(52), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "A+"
            setTextColor(Color.WHITE)
            setOnClickListener { changeFont(0.1f) }
        }, LinearLayout.LayoutParams(dp(52), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "Theme"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleTheme() }
        }, LinearLayout.LayoutParams(dp(70), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "Width"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleWidth() }
        }, LinearLayout.LayoutParams(dp(66), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "Share"
            setTextColor(Color.WHITE)
            setOnClickListener { sharePage() }
        }, LinearLayout.LayoutParams(dp(70), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "Copy"
            setTextColor(Color.WHITE)
            setOnClickListener { copyArticleText() }
        }, LinearLayout.LayoutParams(dp(64), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "Print"
            setTextColor(Color.WHITE)
            setOnClickListener { printPage() }
        }, LinearLayout.LayoutParams(dp(64), dp(44)))

        toolbar.addView(Button(this).apply {
            text = "Original"
            setTextColor(Color.WHITE)
            setOnClickListener {
                showingOriginal = true
                readerActive = false
                webView.loadUrl(originalUrl)
            }
        }, LinearLayout.LayoutParams(dp(82), dp(44)))

        root.addView(toolbar)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (showingOriginal) {
                        showingOriginal = false
                        return
                    }
                    if (!readerActive) activateReaderMode()
                }
            }
        }

        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        webView.loadUrl(originalUrl)
    }

    private fun activateReaderMode() {
        readerActive = true

        val js = """
            (function() {
              try {
                var selectors = [
                  'article','main','[role="main"]','.article','.post',
                  '.post-content','.entry-content','.article-content',
                  '.story-body','.content'
                ];
                var best = null, bestScore = 0;

                selectors.forEach(function(sel) {
                  document.querySelectorAll(sel).forEach(function(el) {
                    var score = (el.innerText || '').trim().length;
                    if (score > bestScore) {
                      best = el;
                      bestScore = score;
                    }
                  });
                });

                if (!best) best = document.body;

                var clone = best.cloneNode(true);

                clone.querySelectorAll(
                  'script,style,noscript,iframe,nav,footer,aside,form,header,' +
                  '[role="navigation"],[role="banner"],[aria-hidden="true"],' +
                  '.ad,.ads,.advert,.advertisement,.cookie,.cookies,.popup,.modal'
                ).forEach(function(el) {
                  el.remove();
                });

                var title =
                  document.title ||
                  ((document.querySelector('h1') || {}).innerText) ||
                  'OLIKH Reader';

                document.documentElement.innerHTML =
                  '<head>' +
                  '<meta name="viewport" content="width=device-width,initial-scale=1">' +
                  '<title>' + title.replace(/</g, '&lt;') + '</title>' +
                  '<style>' +
                  'html,body{margin:0;padding:0;background:#f8f6f1;color:#242424}' +
                  'body{font-family:serif;font-size:20px;line-height:1.72;transition:background .2s,color .2s}' +
                  '.olikh-reader{max-width:760px;margin:0 auto;padding:28px 20px 72px}' +
                  'img,video{max-width:100%;height:auto}' +
                  'pre{white-space:pre-wrap;overflow:auto}' +
                  'a{color:#245b9c}' +
                  'h1,h2,h3{line-height:1.25}' +
                  '</style></head><body></body>';

                clone.className = 'olikh-reader';
                document.body.appendChild(clone);
                window.scrollTo(0, 0);
              } catch (e) {}
            })();
        """.trimIndent()

        webView.evaluateJavascript($js, null)
    }

    private fun toggleTheme() {
        darkTheme = !darkTheme
        val js = if (darkTheme) {
            "document.documentElement.style.background='#181818';document.body.style.background='#181818';document.body.style.color='#e8e8e8';document.querySelectorAll('a').forEach(function(a){a.style.color='#8ab4f8'});"
        } else {
            "document.documentElement.style.background='#f8f6f1';document.body.style.background='#f8f6f1';document.body.style.color='#242424';document.querySelectorAll('a').forEach(function(a){a.style.color='#245b9c'});"
        }
        webView.evaluateJavascript(js, null)
        Toast.makeText(
            this,
            if (darkTheme) "Reader dark theme" else "Reader light theme",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleWidth() {
        wideLayout = !wideLayout
        val width = if (wideLayout) "760px" else "620px"
        webView.evaluateJavascript(
            "document.querySelector('.olikh-reader').style.maxWidth='$width';",
            null
        )
        Toast.makeText(
            this,
            if (wideLayout) "Wide reading width" else "Narrow reading width",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun copyArticleText() {
        webView.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : '';})()"
        ) { raw ->
            val text = raw
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()

            if (text.isBlank()) {
                Toast.makeText(this, "No readable article text", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }

            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("OLIKH Reader", text)
            )
            Toast.makeText(this, "Article text copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePage() {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, webView.title ?: "OLIKH Reader")
                        putExtra(Intent.EXTRA_TEXT, originalUrl)
                    },
                    "Share page"
                )
            )
        }.onFailure {
            Toast.makeText(this, "Unable to share page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun printPage() {
        runCatching {
            val manager = getSystemService(PrintManager::class.java)
            val adapter = webView.createPrintDocumentAdapter(
                (webView.title ?: "OLIKH Reader").take(60)
            )
            manager.print(
                webView.title?.take(60) ?: "OLIKH Reader",
                adapter,
                PrintAttributes.Builder().build()
            )
        }.onFailure {
            Toast.makeText(this, "Printing unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeFont(delta: Float) {
        fontScale = (fontScale + delta).coerceIn(0.75f, 1.5f)
        val size = (20f * fontScale).toInt()
        webView.evaluateJavascript(
            "document.body.style.fontSize='${size}px';",
            null
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
