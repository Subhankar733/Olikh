package com.subho.olikh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val title: String,
    val url: String,
    val visitedAt: Long
)

class HistoryManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("olikh_history", Context.MODE_PRIVATE)

    fun add(title: String, url: String) {
        if (!isValidUrl(url)) return

        val cleanTitle = title
            .replace("\n", " ")
            .trim()
            .ifBlank { url }

        val entries = getAll().toMutableList()

        // Remove an existing copy of the same URL so the newest visit
        // appears at the top instead of creating duplicate rows.
        entries.removeAll { it.url == url }

        entries.add(
            0,
            HistoryEntry(
                title = cleanTitle,
                url = url,
                visitedAt = System.currentTimeMillis()
            )
        )

        // Keep history reasonably small.
        save(entries.take(MAX_HISTORY))
    }

    fun getAll(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    val url = item.optString("url")
                    if (!isValidUrl(url)) continue

                    add(
                        HistoryEntry(
                            title = item.optString("title").ifBlank { url },
                            url = url,
                            visitedAt = item.optLong("visitedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun remove(url: String): Boolean {
        val current = getAll()
        val updated = current.filterNot { it.url == url }

        if (updated.size == current.size) {
            return false
        }

        prefs.edit()
            .putString(
                "history",
                serialize(updated)
            )
            .apply()

        return true
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun save(entries: List<HistoryEntry>) {
        val array = JSONArray()

        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put("visitedAt", entry.visitedAt)
                }
            )
        }

        prefs.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("https://") ||
            url.startsWith("http://")
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 500
    }
}
