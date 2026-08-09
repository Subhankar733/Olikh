package com.subho.olikh

import android.graphics.Color
import android.os.Bundle
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
    private var fontScale = 1.0f

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
            text = "Original"
            setTextColor(Color.WHITE)
            setOnClickListener {
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
                  'body{font-family:serif;font-size:20px;line-height:1.72}' +
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

        webView.evaluateJavascript("javascript:$js", null)
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
