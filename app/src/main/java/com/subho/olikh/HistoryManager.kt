package com.subho.olikh

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val title: String,
    val url: String,
    val visitedAt: Long
)

class HistoryManager(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "olikh_history",
            Context.MODE_PRIVATE
        )

    fun add(title: String, url: String) {
        val cleanUrl = url.trim().take(MAX_URL_LENGTH)
        if (!isValidUrl(cleanUrl)) return

        val cleanTitle = title
            .replace("\n", " ")
            .trim()
            .take(MAX_TITLE_LENGTH)
            .takeUnless {
                it.equals("about:blank", ignoreCase = true) ||
                it.equals("OLIKH Start", ignoreCase = true)
            }
            ?: hostLabel(cleanUrl)

        val entries = getAll()
            .filterNot { it.url == cleanUrl }
            .toMutableList()

        entries.add(
            0,
            HistoryEntry(
                title = cleanTitle,
                url = cleanUrl,
                visitedAt = System.currentTimeMillis()
            )
        )

        save(entries.take(MAX_HISTORY))
    }

    fun getAll(): List<HistoryEntry> {
        val raw =
            prefs.getString(KEY_HISTORY, null)
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

                    val rawTitle =
                        item.optString("title")
                            .trim()
                            .take(MAX_TITLE_LENGTH)

                    val title =
                        if (
                            rawTitle.isBlank() ||
                            rawTitle.equals("about:blank", ignoreCase = true) ||
                            rawTitle.equals("OLIKH Start", ignoreCase = true)
                        ) {
                            hostLabel(url)
                        } else {
                            rawTitle
                        }

                    add(
                        HistoryEntry(
                            title = title,
                            url = url,
                            visitedAt = item.optLong("visitedAt").coerceAtLeast(0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        save(entries.take(MAX_HISTORY))
        return entries.take(MAX_HISTORY)
    }

    fun remove(url: String): Boolean {
        val cleanUrl = url.trim()
        val current = getAll()

        val updated =
            current.filterNot { it.url == cleanUrl }

        if (updated.size == current.size) return false

        save(updated)
        return true
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun save(entries: List<HistoryEntry>) {
        val array = JSONArray()

        entries.take(MAX_HISTORY).forEach { entry ->
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
                    put("visitedAt", entry.visitedAt.coerceAtLeast(0L))
                }
            )
        }

        prefs.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private fun isValidUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()

        if (
            !url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) return false

        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return false

        val host = uri.host?.lowercase()?.trim()
            ?: return false

        if (
            host == "olikh.local" ||
            host.endsWith(".olikh.local")
        ) return false

        return true
    }

    private fun hostLabel(url: String): String {
        return runCatching {
            Uri.parse(url)
                .host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
                ?: url
        }.getOrDefault(url)
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 500
        private const val MAX_TITLE_LENGTH = 300
        private const val MAX_URL_LENGTH = 4096
    }
}
