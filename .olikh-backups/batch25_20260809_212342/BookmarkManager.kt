package com.subho.olikh

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkEntry(
    val title: String,
    val url: String,
    val savedAt: Long
)

class BookmarkManager(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "olikh_bookmarks",
            Context.MODE_PRIVATE
        )

    fun add(title: String, url: String): Boolean {
        if (!isValidUrl(url)) return false

        val cleanTitle = title
            .replace("\n", " ")
            .trim()
            .ifBlank { url }

        val entries = getAll().toMutableList()

        // Same URL only needs one bookmark.
        entries.removeAll { it.url == url }

        entries.add(
            0,
            BookmarkEntry(
                title = cleanTitle,
                url = url,
                savedAt = System.currentTimeMillis()
            )
        )

        save(entries)
        return true
    }

    fun remove(url: String): Boolean {
        val entries = getAll().toMutableList()
        val removed = entries.removeAll { it.url == url }

        if (removed) {
            save(entries)
        }

        return removed
    }

    fun contains(url: String): Boolean {
        return getAll().any { it.url == url }
    }

    fun getAll(): List<BookmarkEntry> {
        val raw =
            prefs.getString(KEY_BOOKMARKS, null)
                ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val url = item.optString("url")

                    if (!isValidUrl(url)) continue

                    add(
                        BookmarkEntry(
                            title = item
                                .optString("title")
                                .ifBlank { url },

                            url = url,

                            savedAt =
                                item.optLong("savedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_BOOKMARKS)
            .apply()
    }

    private fun save(entries: List<BookmarkEntry>) {
        val array = JSONArray()

        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put("savedAt", entry.savedAt)
                }
            )
        }

        prefs.edit()
            .putString(
                KEY_BOOKMARKS,
                array.toString()
            )
            .apply()
    }

    private fun isValidUrl(url: String): Boolean {
        val clean = url.trim()

        if (
            !clean.startsWith("https://", ignoreCase = true) &&
            !clean.startsWith("http://", ignoreCase = true)
        ) {
            return false
        }

        val uri = runCatching { Uri.parse(clean) }.getOrNull()
            ?: return false

        val host = uri.host?.lowercase()?.trim()
            ?: return false

        if (
            host == "olikh.local" ||
            host.endsWith(".olikh.local")
        ) {
            return false
        }

        return true
    }

    companion object {
        private const val KEY_BOOKMARKS = "bookmarks"
    }
}
