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
        if (!isValidUrl(url)) return

        val cleanTitle = title
            .replace("\n", " ")
            .trim()
            .takeUnless {
                it.equals("about:blank", ignoreCase = true) ||
                it.equals("OLIKH Start", ignoreCase = true)
            }
            ?: hostLabel(url)

        val entries = getAll()
            .filterNot { it.url == url }
            .toMutableList()

        entries.add(
            0,
            HistoryEntry(
                title = cleanTitle,
                url = url,
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
                    val item = array.getJSONObject(i)

                    val url =
                        item.optString("url").trim()

                    if (!isValidUrl(url)) {
                        continue
                    }

                    val rawTitle =
                        item.optString("title").trim()

                    val title =
                        if (
                            rawTitle.isBlank() ||
                            rawTitle.equals(
                                "about:blank",
                                ignoreCase = true
                            ) ||
                            rawTitle.equals(
                                "OLIKH Start",
                                ignoreCase = true
                            )
                        ) {
                            hostLabel(url)
                        } else {
                            rawTitle
                        }

                    add(
                        HistoryEntry(
                            title = title,
                            url = url,
                            visitedAt =
                                item.optLong("visitedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        /*
         * Rewrite the stored history after filtering.
         * This permanently removes old internal/junk entries.
         */
        save(entries.take(MAX_HISTORY))

        return entries.take(MAX_HISTORY)
    }

    fun remove(url: String): Boolean {
        val current = getAll()

        val updated =
            current.filterNot {
                it.url == url
            }

        if (updated.size == current.size) {
            return false
        }

        save(updated)

        return true
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun save(
        entries: List<HistoryEntry>
    ) {
        val array = JSONArray()

        entries.forEach { entry ->
            if (!isValidUrl(entry.url)) {
                return@forEach
            }

            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("url", entry.url)
                    put(
                        "visitedAt",
                        entry.visitedAt
                    )
                }
            )
        }

        prefs.edit()
            .putString(
                KEY_HISTORY,
                array.toString()
            )
            .apply()
    }

    private fun isValidUrl(
        rawUrl: String
    ): Boolean {
        val url = rawUrl.trim()

        if (
            !url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) {
            return false
        }

        val uri =
            runCatching {
                Uri.parse(url)
            }.getOrNull()
                ?: return false

        val host =
            uri.host
                ?.lowercase()
                ?.trim()
                ?: return false

        /*
         * Never store OLIKH's own internal pages.
         */
        if (
            host == "olikh.local" ||
            host.endsWith(".olikh.local")
        ) {
            return false
        }

        return true
    }

    private fun hostLabel(
        url: String
    ): String {
        return runCatching {
            Uri.parse(url)
                .host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
                ?: url
        }.getOrDefault(url)
    }

    companion object {
        private const val KEY_HISTORY =
            "history"

        private const val MAX_HISTORY =
            500
    }
}
