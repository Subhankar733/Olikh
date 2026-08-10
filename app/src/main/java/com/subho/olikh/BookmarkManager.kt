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
        val cleanUrl = url.trim().take(MAX_URL_LENGTH)
        if (!isValidUrl(cleanUrl)) return false

        val cleanTitle = title
            .replace("\n", " ")
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank { cleanUrl }

        val entries = getAll().toMutableList()
        entries.removeAll { it.url == cleanUrl }

        entries.add(
            0,
            BookmarkEntry(
                title = cleanTitle,
                url = cleanUrl,
                savedAt = System.currentTimeMillis()
            )
        )

        save(entries.take(MAX_BOOKMARKS))
        return true
    }

    fun remove(url: String): Boolean {
        val cleanUrl = url.trim()
        val entries = getAll().toMutableList()
        val removed = entries.removeAll { it.url == cleanUrl }

        if (removed) save(entries)
        return removed
    }

    fun contains(url: String): Boolean {
        return getAll().any { it.url == url.trim() }
    }

    fun getAll(): List<BookmarkEntry> {
        val raw =
            prefs.getString(KEY_BOOKMARKS, null)
                ?: return emptyList()

        val entries = runCatching {
            val array = JSONArray(raw)

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                        ?: continue

                    val url =
                        item.optString("url")
                            .trim()
                            .take(MAX_URL_LENGTH)

                    if (!isValidUrl(url)) continue

                    add(
                        BookmarkEntry(
                            title = item.optString("title")
                                .replace("\n", " ")
                                .trim()
                                .take(MAX_TITLE_LENGTH)
                                .ifBlank { url },
                            url = url,
                            savedAt = item.optLong("savedAt")
                                .coerceAtLeast(0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        save(entries.take(MAX_BOOKMARKS))
        return entries.take(MAX_BOOKMARKS)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_BOOKMARKS)
            .apply()
    }

    private fun save(entries: List<BookmarkEntry>) {
        val array = JSONArray()

        entries.take(MAX_BOOKMARKS).forEach { entry ->
            val cleanUrl = entry.url.trim().take(MAX_URL_LENGTH)
            if (!isValidUrl(cleanUrl)) return@forEach

            array.put(
                JSONObject().apply {
                    put(
                        "title",
                        entry.title
                            .replace("\n", " ")
                            .trim()
                            .take(MAX_TITLE_LENGTH)
                    )
                    put("url", cleanUrl)
                    put("savedAt", entry.savedAt.coerceAtLeast(0L))
                }
            )
        }

        prefs.edit()
            .putString(KEY_BOOKMARKS, array.toString())
            .apply()
    }

    private fun isValidUrl(url: String): Boolean {
        val clean = url.trim()

        if (
            !clean.startsWith("https://", true) &&
            !clean.startsWith("http://", true)
        ) return false

        val uri = runCatching { Uri.parse(clean) }.getOrNull()
            ?: return false

        val host = uri.host?.lowercase()?.trim()
            ?: return false

        if (
            host == "olikh.local" ||
            host.endsWith(".olikh.local")
        ) return false

        return true
    }

    companion object {
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val MAX_BOOKMARKS = 500
        private const val MAX_TITLE_LENGTH = 300
        private const val MAX_URL_LENGTH = 4096
    }
}
