package com.subho.olikh

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class BackupRestoreActivity : AppCompatActivity() {

    private companion object {
        const val REQUEST_EXPORT = 4401
        const val REQUEST_IMPORT = 4402
        const val BACKUP_FORMAT = "olikh-backup"
        const val BACKUP_VERSION = 2
        const val MAX_ITEMS = 500
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "OLIKH Backup & Restore"
            textSize = 22f
        })

        root.addView(TextView(this).apply {
            text = "Export or restore bookmarks, history, browser preferences, advanced settings, tabs, tab groups and saved site permissions as one OLIKH JSON backup."
            textSize = 13f
            setPadding(0, dp(8), 0, dp(18))
        })

        root.addView(Button(this).apply {
            text = "EXPORT BACKUP"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/json"
                    putExtra(
                        Intent.EXTRA_TITLE,
                        "olikh-backup-${System.currentTimeMillis()}.json"
                    )
                }
                startActivityForResult(intent, REQUEST_EXPORT)
            }
        })

        root.addView(Button(this).apply {
            text = "IMPORT BACKUP"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/json"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, REQUEST_IMPORT)
            }
        })

        root.addView(Button(this).apply {
            text = "CLOSE"
            setOnClickListener { finish() }
        })

        setContentView(root)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return

        val uri = data?.data ?: return

        when (requestCode) {
            REQUEST_EXPORT -> exportBackup(uri)
            REQUEST_IMPORT -> importBackup(uri)
        }
    }

    private fun exportBackup(uri: Uri) {
        runCatching {
            val root = JSONObject().apply {
                put("format", BACKUP_FORMAT)
                put("version", BACKUP_VERSION)
                put("createdAt", System.currentTimeMillis())

                put("bookmarks", JSONArray().apply {
                    BookmarkManager(this@BackupRestoreActivity)
                        .getAll()
                        .forEach {
                            put(JSONObject().apply {
                                put("title", it.title)
                                put("url", it.url)
                                put("savedAt", it.savedAt)
                            })
                        }
                })

                put("history", JSONArray().apply {
                    HistoryManager(this@BackupRestoreActivity)
                        .getAll()
                        .forEach {
                            put(JSONObject().apply {
                                put("title", it.title)
                                put("url", it.url)
                                put("visitedAt", it.visitedAt)
                            })
                        }
                })

                put(
                    "browserPreferences",
                    preferencesToJson(
                        getSharedPreferences(
                            "olikh_browser",
                            MODE_PRIVATE
                        )
                    )
                )

                put(
                    "advancedPreferences",
                    preferencesToJson(
                        getSharedPreferences(
                            "olikh_advanced",
                            MODE_PRIVATE
                        )
                    )
                )

                put(
                    "tabSession",
                    preferencesToJson(
                        getSharedPreferences(
                            "olikh_tab_session",
                            MODE_PRIVATE
                        )
                    )
                )

                put(
                    "tabGroups",
                    preferencesToJson(
                        getSharedPreferences(
                            "olikh_tab_groups",
                            MODE_PRIVATE
                        )
                    )
                )

                put(
                    "sitePermissions",
                    preferencesToJson(
                        getSharedPreferences(
                            "site_permissions",
                            MODE_PRIVATE
                        )
                    )
                )
            }

            contentResolver.openOutputStream(uri)?.use {
                it.write(
                    root.toString(2)
                        .toByteArray(Charsets.UTF_8)
                )
            } ?: error("Unable to open backup file")

            toast("Backup v2 exported successfully")
        }.onFailure {
            toast(
                "Export failed: ${it.message ?: "unknown error"}"
            )
        }
    }

    private fun importBackup(uri: Uri) {
        runCatching {
            val text =
                contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Unable to read backup file")

            val root = JSONObject(text)

            if (root.optString("format") != BACKUP_FORMAT) {
                error("Not an OLIKH backup")
            }

            val version = root.optInt("version", -1)
            if (version != 1 && version != BACKUP_VERSION) {
                error("Unsupported backup version")
            }

            restoreBookmarks(
                root.optJSONArray("bookmarks")
                    ?: JSONArray()
            )

            restoreHistory(
                root.optJSONArray("history")
                    ?: JSONArray()
            )

            root.optJSONObject("browserPreferences")?.let {
                jsonToPreferences(
                    getSharedPreferences(
                        "olikh_browser",
                        MODE_PRIVATE
                    ),
                    it
                )
            }

            root.optJSONObject("advancedPreferences")?.let {
                jsonToPreferences(
                    getSharedPreferences(
                        "olikh_advanced",
                        MODE_PRIVATE
                    ),
                    it
                )
            }

            if (version >= 2) {
                root.optJSONObject("tabSession")?.let {
                    jsonToPreferences(
                        getSharedPreferences(
                            "olikh_tab_session",
                            MODE_PRIVATE
                        ),
                        it
                    )
                }

                root.optJSONObject("tabGroups")?.let {
                    jsonToPreferences(
                        getSharedPreferences(
                            "olikh_tab_groups",
                            MODE_PRIVATE
                        ),
                        it
                    )
                }

                root.optJSONObject("sitePermissions")?.let {
                    jsonToPreferences(
                        getSharedPreferences(
                            "site_permissions",
                            MODE_PRIVATE
                        ),
                        it
                    )
                }
            }

            toast(
                if (version == 1) {
                    "Backup restored successfully"
                } else {
                    "Backup v2 restored successfully"
                }
            )
        }.onFailure {
            toast(
                "Restore failed: ${it.message ?: "invalid backup"}"
            )
        }
    }

    private fun restoreBookmarks(array: JSONArray) {
        val clean = JSONArray()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()

            if (!isHttpUrl(url)) continue

            clean.put(JSONObject().apply {
                put(
                    "title",
                    item.optString("title")
                        .replace("\n", " ")
                        .trim()
                        .take(300)
                        .ifBlank { url }
                )
                put("url", url.take(4096))
                put(
                    "savedAt",
                    item.optLong("savedAt")
                        .coerceAtLeast(0L)
                )
            })

            if (clean.length() >= MAX_ITEMS) break
        }

        getSharedPreferences(
            "olikh_bookmarks",
            MODE_PRIVATE
        ).edit()
            .putString("bookmarks", clean.toString())
            .apply()
    }

    private fun restoreHistory(array: JSONArray) {
        val clean = JSONArray()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()

            if (!isHttpUrl(url)) continue

            clean.put(JSONObject().apply {
                put(
                    "title",
                    item.optString("title")
                        .replace("\n", " ")
                        .trim()
                        .take(300)
                        .ifBlank { url }
                )
                put("url", url.take(4096))
                put(
                    "visitedAt",
                    item.optLong("visitedAt")
                        .coerceAtLeast(0L)
                )
            })

            if (clean.length() >= MAX_ITEMS) break
        }

        getSharedPreferences(
            "olikh_history",
            MODE_PRIVATE
        ).edit()
            .putString("history", clean.toString())
            .apply()
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("https://", true) ||
            url.startsWith("http://", true)
    }

    private fun preferencesToJson(
        prefs: android.content.SharedPreferences
    ): JSONObject {
        val out = JSONObject()

        prefs.all.toSortedMap().forEach { (key, value) ->
            when (value) {
                is Boolean -> out.put(key, value)
                is Int -> out.put(key, value)
                is Long -> out.put(key, value)
                is Float -> out.put(key, value.toDouble())
                is Double -> out.put(key, value)
                is String -> out.put(key, value)
                is Set<*> -> {
                    val array = JSONArray()
                    value.filterIsInstance<String>()
                        .sorted()
                        .forEach(array::put)
                    out.put(key, array)
                }
            }
        }

        return out
    }

    private fun jsonToPreferences(
        prefs: android.content.SharedPreferences,
        json: JSONObject
    ) {
        val editor = prefs.edit()

        json.keys().forEach { key ->
            when (val value = json.opt(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(
                    key,
                    value.toFloat()
                )
                is String -> editor.putString(key, value)
                is JSONArray -> {
                    val values = buildSet {
                        for (i in 0 until value.length()) {
                            val item = value.optString(i)
                            if (item.isNotBlank()) add(item)
                        }
                    }
                    editor.putStringSet(key, values)
                }
            }
        }

        editor.apply()
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
