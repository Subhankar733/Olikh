package com.subho.olikh

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PrivacyDataActivity : AppCompatActivity() {
    private val permissions by lazy { SitePermissionManager(this) }
    private lateinit var permissionList: LinearLayout
    private lateinit var summary: TextView

    private val ink = Color.rgb(20, 22, 27)
    private val muted = Color.rgb(105, 110, 120)
    private val surface = Color.WHITE
    private val dark = Color.rgb(17, 19, 24)

    private data class ClearChoice(
        val key: String,
        val label: String,
        val description: String,
        val defaultChecked: Boolean
    )

    private val choices = listOf(
        ClearChoice("cookies", "Cookies", "Remove website login/session cookies.", true),
        ClearChoice("web_storage", "Web storage", "Remove localStorage and WebView site storage.", true),
        ClearChoice("cache", "Cached web resources", "Remove WebView cache data.", true),
        ClearChoice("history", "Browsing history", "Remove OLIKH's saved browsing history.", false),
        ClearChoice("bookmarks", "Bookmarks", "Remove saved bookmarks. Off by default.", false),
        ClearChoice("site_permissions", "Saved site permission decisions",
            "Remove OLIKH's saved Allow/Block decisions.", false)
    )

    private val boxes = linkedMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(243, 244, 246))
        }

        root.addView(buildHeader())

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(30))
        }

        content.addView(section("CLEAR BROWSING DATA"))

        content.addView(TextView(this).apply {
            text = "Choose exactly what OLIKH should erase. Downloads are never removed here."
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        choices.forEach { choice ->
            val box = CheckBox(this).apply {
                text = choice.label
                textSize = 14f
                setTextColor(ink)
                isChecked = choice.defaultChecked
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnCheckedChangeListener { _, _ -> updateSelectionSummary() }
            }
            boxes[choice.key] = box

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(surface)
                addView(box, LinearLayout.LayoutParams(-1, -2))
                addView(TextView(this@PrivacyDataActivity).apply {
                    text = choice.description
                    textSize = 10f
                    setTextColor(muted)
                    setPadding(dp(52), 0, dp(12), dp(12))
                })
            }

            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(3), 0, dp(3))
            })
        }

        summary = TextView(this).apply {
            textSize = 10f
            setTextColor(muted)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        content.addView(summary)

        content.addView(Button(this).apply {
            text = "CLEAR SELECTED DATA"
            setOnClickListener { clearSelectedData() }
        })

        content.addView(Button(this).apply {
            text = "CLEAR EVERYTHING EXCEPT DOWNLOADS"
            setOnClickListener {
                boxes.values.forEach { it.isChecked = true }
                clearSelectedData()
            }
        })

        content.addView(section("SITE PERMISSION MANAGER"))

        content.addView(TextView(this).apply {
            text = "Review OLIKH's saved website Allow/Block decisions. These are separate from Android runtime permissions."
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        permissionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(permissionList)

        content.addView(Button(this).apply {
            text = "CLEAR ALL SAVED SITE DECISIONS"
            setOnClickListener {
                permissions.clearAll()
                renderPermissions()
                toast("Saved site decisions cleared")
            }
        })

        content.addView(Button(this).apply {
            text = "OPEN ANDROID APP PERMISSIONS"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        updateSelectionSummary()
        renderPermissions()
    }

    private fun buildHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(dark)

            addView(Button(this@PrivacyDataActivity).apply {
                text = "‹"
                textSize = 24f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))

            addView(TextView(this@PrivacyDataActivity).apply {
                text = "Privacy & Data"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -1, 1f))

            addView(Button(this@PrivacyDataActivity).apply {
                text = "REFRESH"
                textSize = 9f
                setOnClickListener { renderPermissions() }
            }, LinearLayout.LayoutParams(-2, dp(44)))
        }
    }

    private fun clearSelectedData() {
        val clearCookies = boxes["cookies"]?.isChecked == true
        val clearStorage = boxes["web_storage"]?.isChecked == true
        val clearCache = boxes["cache"]?.isChecked == true
        val clearHistory = boxes["history"]?.isChecked == true
        val clearBookmarks = boxes["bookmarks"]?.isChecked == true
        val clearPermissions = boxes["site_permissions"]?.isChecked == true

        if (!clearCookies && !clearStorage && !clearCache &&
            !clearHistory && !clearBookmarks && !clearPermissions) {
            toast("Select at least one item")
            return
        }

        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
            }
        }
        if (clearStorage) WebStorage.getInstance().deleteAllData()

        if (clearCache) {
            runCatching {
                WebView(this).apply {
                    clearCache(true)
                    destroy()
                }
            }
        }

        if (clearHistory) HistoryManager(this).clear()
        if (clearBookmarks) BookmarkManager(this).clear()

        if (clearPermissions) {
            permissions.clearAll()
            renderPermissions()
        }

        renderPermissions()
        updateSelectionSummary()
        toast("Selected browsing data cleared")
    }

    private fun renderPermissions() {
        permissionList.removeAllViews()
        val saved = permissions.getSavedPermissions()

        if (saved.isEmpty()) {
            permissionList.addView(TextView(this).apply {
                text = "No saved site permission decisions."
                textSize = 11f
                setTextColor(muted)
                setPadding(dp(8), dp(12), dp(8), dp(18))
            })
            return
        }

        saved.toSortedMap().forEach { (host, decisions) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(10), dp(12))
                setBackgroundColor(surface)
            }

            card.addView(TextView(this).apply {
                text = host
                textSize = 14f
                setTextColor(ink)
            })

            card.addView(TextView(this).apply {
                text = decisions.entries.joinToString("\n") {
                    "• ${it.key}: ${it.value.name}"
                }
                textSize = 10f
                setTextColor(muted)
                setPadding(0, dp(5), 0, dp(7))
            })

            card.addView(Button(this).apply {
                text = "RESET THIS SITE"
                textSize = 9f
                setOnClickListener {
                    permissions.clearSite(host)
                    renderPermissions()
                    toast("$host reset to Ask")
                }
            })

            permissionList.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(3), 0, dp(3))
            })
        }
    }

    private fun updateSelectionSummary() {
        if (::summary.isInitialized) {
            summary.text =
                "${boxes.values.count { it.isChecked }} of ${choices.size} categories selected"
        }
    }

    private fun section(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(muted)
            setPadding(dp(5), dp(18), dp(5), dp(8))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
