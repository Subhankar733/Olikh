package com.subho.olikh

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.InputType
import android.util.Rational
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Batch 5 feature layer:
 * Reader mode, tab groups, PiP, print/save-page, link/selection tools,
 * translation flow, and Android sharing.
 *
 * This module is deliberately isolated from the existing browser core.
 */
object OlikhNewBrowserFeatures {

    data class TabGroup(
        val id: String,
        var name: String,
        val tabIds: MutableList<String> = mutableListOf()
    )

    data class SavedPage(
        val id: String,
        val title: String,
        val url: String,
        val file: String,
        val savedAt: Long
    )

    private const val PREFS = "olikh_batch5"
    private const val GROUPS = "groups"
    private const val SAVED = "saved_pages"

    fun installSelectionTools(
        activity: Activity,
        webView: WebView,
        search: (String) -> Unit
    ) {
        // Use stable View long-click handling instead of the unsupported
        // WebView custom-selection ActionMode member from the failed build.
        webView.setOnLongClickListener {
            webView.evaluateJavascript(
                "(function(){return window.getSelection().toString();})()"
            ) { raw ->
                val text = raw
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .trim()

                if (text.isBlank()) {
                    return@evaluateJavascript
                }

                AlertDialog.Builder(activity)
                    .setTitle("Selected text")
                    .setItems(arrayOf("Search", "Share", "Copy")) { _, which ->
                        when (which) {
                            0 -> search(text)
                            1 -> shareText(activity, text)
                            2 -> {
                                val cm = activity.getSystemService(
                                    android.content.ClipboardManager::class.java
                                )
                                cm.setPrimaryClip(
                                    ClipData.newPlainText("OLIKH selection", text)
                                )
                                toast(activity, "Copied")
                            }
                        }
                    }
                    .show()
            }

            false
        }
    }

    fun openLinkTools(activity: Activity, url: String) {
        val items = arrayOf("Open externally", "Copy link", "Share link")
        AlertDialog.Builder(activity)
            .setTitle("Link tools")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure { toast(activity, "No compatible app") }

                    1 -> {
                        val cm = activity.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("OLIKH link", url))
                        toast(activity, "Link copied")
                    }

                    2 -> shareText(activity, url)
                }
            }
            .show()
    }

    fun readerMode(activity: Activity, webView: WebView) {
        val js = """
            (function(){
              const bad = 'script,style,noscript,iframe,nav,header,footer,aside,form,button,ads,.ad,.ads,.advertisement,[aria-label*="advert"]';
              document.querySelectorAll(bad).forEach(e=>e.remove());
              const candidates=[...document.querySelectorAll('article,main,[role="main"]')];
              let best=candidates.sort((a,b)=>(b.innerText||'').length-(a.innerText||'').length)[0]||document.body;
              const text=(best.innerText||'').trim();
              document.documentElement.innerHTML =
                '<head><meta name="viewport" content="width=device-width,initial-scale=1"></head>'+
                '<body><article id="olikh-reader">'+best.innerHTML+'</article></body>';
              document.body.style.cssText='margin:0;background:#f5f1e8;color:#252525;';
              const a=document.getElementById('olikh-reader');
              a.style.cssText='max-width:760px;margin:auto;padding:28px 22px 60px;font:18px/1.75 system-ui,serif;';
              return text.length;
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val chars = result.filter { it.isDigit() }.toIntOrNull() ?: 0
            toast(activity, if (chars > 0) "Reader mode enabled" else "Reader mode unavailable")
        }
    }

    fun readerTextScale(webView: WebView, increase: Boolean) {
        val delta = if (increase) 10 else -10
        webView.evaluateJavascript(
            """
            (function(){
              const a=document.getElementById('olikh-reader')||document.body;
              const s=parseFloat(getComputedStyle(a).fontSize)||18;
              a.style.fontSize=Math.max(12,Math.min(32,s+$delta))+'px';
            })()
            """.trimIndent(),
            null
        )
    }

    fun createGroup(activity: Activity, name: String, tabIds: List<String>) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val id = "group_${System.currentTimeMillis()}"
        val clean = name.trim().ifEmpty { "Tab Group" }
        val record = "$id|$clean|${tabIds.joinToString(",")}"
        val old = prefs.getStringSet(GROUPS, emptySet()).orEmpty().toMutableSet()
        old.add(record)
        prefs.edit().putStringSet(GROUPS, old).apply()
        toast(activity, "Group created")
    }

    fun deleteGroup(activity: Activity, id: String) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val updated = prefs.getStringSet(GROUPS, emptySet())
            .orEmpty()
            .filterNot { it.startsWith("$id|") }
            .toSet()
        prefs.edit().putStringSet(GROUPS, updated).apply()
        toast(activity, "Group deleted")
    }

    fun renameGroup(activity: Activity, id: String, newName: String) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val updated = prefs.getStringSet(GROUPS, emptySet()).orEmpty().map {
            if (it.startsWith("$id|")) {
                val parts = it.split("|", limit = 3)
                "$id|${newName.trim().ifEmpty { parts.getOrElse(1) { "Tab Group" } }}|${parts.getOrElse(2) { "" }}"
            } else it
        }.toSet()
        prefs.edit().putStringSet(GROUPS, updated).apply()
        toast(activity, "Group renamed")
    }

    fun listGroups(activity: Activity): List<TabGroup> {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        return prefs.getStringSet(GROUPS, emptySet()).orEmpty().mapNotNull {
            val p = it.split("|", limit = 3)
            if (p.size < 2) null else TabGroup(
                p[0], p[1],
                p.getOrElse(2) { "" }.split(",").filter { x -> x.isNotBlank() }.toMutableList()
            )
        }
    }

    fun enterPip(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(activity, "PiP requires Android 8.0 or newer")
            return
        }
        if (!activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            toast(activity, "PiP is not supported on this device")
            return
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }

    fun printPage(activity: Activity, webView: WebView) {
        val printManager = activity.getSystemService(PrintManager::class.java)
        val adapter = webView.createPrintDocumentAdapter("OLIKH-${System.currentTimeMillis()}")
        printManager.print(
            webView.title?.take(60) ?: "OLIKH page",
            adapter,
            PrintAttributes.Builder().build()
        )
    }

    fun savePage(activity: Activity, webView: WebView) {
        val title = webView.title?.take(100)?.ifBlank { "Saved page" } ?: "Saved page"
        val url = webView.url ?: return
        val dir = File(activity.filesDir, "olikh_saved_pages").apply { mkdirs() }
        val id = "page_${System.currentTimeMillis()}"
        val file = File(dir, "$id.html")

        webView.evaluateJavascript(
            """
            (function(){
              var d=document.documentElement.cloneNode(true);
              d.querySelectorAll('script').forEach(function(x){x.remove();});
              return '<!doctype html>'+d.outerHTML;
            })()
            """.trimIndent()
        ) { raw ->
            val html = raw
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")

            runCatching {
                file.writeText(html, Charsets.UTF_8)
                val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                val record = listOf(id, title.replace("|", " "), url, file.absolutePath, System.currentTimeMillis()).joinToString("|")
                val set = prefs.getStringSet(SAVED, emptySet()).orEmpty().toMutableSet()
                set.add(record)
                prefs.edit().putStringSet(SAVED, set).apply()
                toast(activity, "Page saved offline")
            }.onFailure {
                toast(activity, "Could not save page")
            }
        }
    }

    fun savedPages(activity: Activity): List<SavedPage> {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        return prefs.getStringSet(SAVED, emptySet()).orEmpty().mapNotNull {
            val p = it.split("|", limit = 5)
            if (p.size < 5) null else SavedPage(p[0], p[1], p[2], p[3], p[4].toLongOrNull() ?: 0L)
        }.sortedByDescending { it.savedAt }
    }

    fun openSavedPage(activity: Activity, page: SavedPage) {
        val file = File(page.file)
        if (!file.exists()) {
            toast(activity, "Saved file is missing")
            return
        }
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(file), "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    fun translateCurrentPage(activity: Activity, webView: WebView) {
        val url = webView.url ?: return
        val translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=${Uri.encode(url)}"
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(translateUrl)))
        }.onFailure {
            toast(activity, "Translation unavailable")
        }
    }

    fun sharePage(activity: Activity, webView: WebView) {
        val url = webView.url ?: return
        val title = webView.title ?: url
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        activity.startActivity(Intent.createChooser(intent, "Share page"))
    }

    fun shareText(activity: Activity, text: String) {
        activity.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share with"
        ))
    }

    private fun toast(activity: Activity, text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
