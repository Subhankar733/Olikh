package com.subho.olikh

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SavedPagesActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(243, 244, 246))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(17, 19, 24))
        }

        header.addView(Button(this).apply {
            text = "‹"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        header.addView(TextView(this).apply {
            text = "Saved Pages"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))

        header.addView(Button(this).apply {
            text = "REFRESH"
            textSize = 10f
            setOnClickListener { renderList() }
        }, LinearLayout.LayoutParams(-2, dp(44)))

        root.addView(header)

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(28))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        renderList()
    }

    private fun renderList() {
        list.removeAllViews()
        val pages = OlikhNewBrowserFeatures.savedPages(this)

        if (pages.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No saved pages yet.\nUse Page Tools → Save complete page."
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(dp(20), dp(60), dp(20), dp(60))
            })
            return
        }

        pages.forEach { page ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(14), dp(15), dp(14))
                setBackgroundColor(Color.WHITE)
            }

            card.addView(TextView(this).apply {
                text = page.title
                textSize = 15f
                setTextColor(Color.rgb(20, 22, 27))
            })

            card.addView(TextView(this).apply {
                text = page.url
                textSize = 10f
                setTextColor(Color.GRAY)
                setPadding(0, dp(4), 0, dp(8))
            })

            val actions = LinearLayout(this)

            actions.addView(Button(this).apply {
                text = "OPEN OFFLINE"
                textSize = 9f
                setOnClickListener {
                    openOffline(page.file, page.title, page.url)
                }
            })

            actions.addView(Button(this).apply {
                text = "OPEN ONLINE"
                textSize = 9f
                setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(page.url)))
                    }.onFailure {
                        toast("No browser available")
                    }
                }
            })

            actions.addView(Button(this).apply {
                text = "SHARE"
                textSize = 9f
                setOnClickListener {
                    sharePage(page.title, page.url)
                }
            })

            actions.addView(Button(this).apply {
                text = "DELETE"
                textSize = 9f
                setOnClickListener {
                    val target = runCatching {
                        File(page.file).canonicalFile
                    }.getOrNull()
                    val root = runCatching {
                        File(filesDir, "olikh_saved_pages").canonicalFile
                    }.getOrNull()

                    if (
                        target != null &&
                        root != null &&
                        target.path.startsWith(root.path + File.separator) &&
                        target.isFile
                    ) {
                        target.delete()
                    }

                    removeRecord(page.id)
                    renderList()
                }
            })

            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(4), 0, dp(4))
            })
        }
    }

    private fun openOffline(
        filePath: String,
        title: String,
        baseUrl: String
    ) {
        val file = File(filePath)
        val savedDir = File(filesDir, "olikh_saved_pages")

        val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
        val canonicalDir = runCatching { savedDir.canonicalFile }.getOrNull()

        if (
            canonicalFile == null ||
            canonicalDir == null ||
            !canonicalFile.path.startsWith(canonicalDir.path + File.separator) ||
            !canonicalFile.isFile
        ) {
            toast("Saved file is invalid")
            removeRecordByFile(filePath)
            renderList()
            return
        }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true

            loadDataWithBaseURL(
                baseUrl,
                canonicalFile.readText(Charsets.UTF_8),
                "text/html",
                "UTF-8",
                null
            )
        }

        val dialog = android.app.Dialog(this)
        dialog.setTitle(title)
        dialog.setContentView(web)
        dialog.setOnDismissListener {
            web.stopLoading()
            web.destroy()
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun sharePage(title: String, url: String) {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        putExtra(Intent.EXTRA_TEXT, url)
                    },
                    "Share saved page"
                )
            )
        }.onFailure {
            toast("Unable to share page")
        }
    }

    private fun removeRecord(id: String) {
        val prefs = getSharedPreferences("olikh_batch5", MODE_PRIVATE)
        val updated = prefs.getStringSet("saved_pages", emptySet())
            .orEmpty()
            .filterNot { it.startsWith("$id|") }
            .toSet()
        prefs.edit().putStringSet("saved_pages", updated).apply()
        toast("Saved page deleted")
    }

    private fun removeRecordByFile(filePath: String) {
        val prefs = getSharedPreferences("olikh_batch5", MODE_PRIVATE)
        val updated = prefs.getStringSet("saved_pages", emptySet())
            .orEmpty()
            .filterNot { record ->
                record.split("|", limit = 5).getOrNull(3) == filePath
            }
            .toSet()
        prefs.edit().putStringSet("saved_pages", updated).apply()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
