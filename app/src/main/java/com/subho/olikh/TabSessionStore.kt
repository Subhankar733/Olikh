package com.subho.olikh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent tab/session storage.
 *
 * Normal tabs are persisted.
 * Incognito tabs are intentionally never persisted.
 * Recently closed normal tabs are persisted separately.
 */
class TabSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "olikh_tab_session",
        Context.MODE_PRIVATE
    )

    data class SessionTab(
        val title: String,
        val url: String,
        val incognito: Boolean = false
    )

    private fun cleanUrl(url: String): String =
        url.trim().takeIf {
            it.isNotBlank() &&
            it != "about:blank" &&
            !it.startsWith("data:", ignoreCase = true)
        }.orEmpty()

    fun save(
        tabs: List<BrowserTab>,
        activeIndex: Int
    ) {
        val normal = JSONArray()

        tabs.filter { !it.incognito }.forEach { tab ->
            val url = cleanUrl(tab.webView.url ?: tab.url)
            if (url.isBlank()) return@forEach

            normal.put(
                JSONObject()
                    .put("title", tab.title.ifBlank { "Tab" })
                    .put("url", url)
            )
        }

        val safeActive =
            if (normal.length() == 0) 0
            else activeIndex.coerceIn(0, normal.length() - 1)

        prefs.edit()
            .putString("tabs", normal.toString())
            .putInt("active", safeActive)
            .apply()
    }

    fun restore(): Pair<List<SessionTab>, Int> {
        val raw = prefs.getString("tabs", null)
            ?: return emptyList<SessionTab>() to 0

        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<SessionTab>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = cleanUrl(item.optString("url"))
                if (url.isBlank()) continue

                result += SessionTab(
                    title = item.optString("title").ifBlank { "Tab" },
                    url = url,
                    incognito = false
                )
            }

            val active = prefs.getInt("active", 0)
                .coerceIn(0, (result.size - 1).coerceAtLeast(0))

            result to active
        }.getOrElse {
            clear()
            emptyList<SessionTab>() to 0
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun saveRecentlyClosed(
        entries: List<ClosedTabSnapshot>
    ) {
        val array = JSONArray()

        entries
            .filter { !it.incognito }
            .take(20)
            .forEach {
                val url = cleanUrl(it.url)
                if (url.isBlank()) return@forEach

                array.put(
                    JSONObject()
                        .put("title", it.title.ifBlank { "Tab" })
                        .put("url", url)
                )
            }

        prefs.edit()
            .putString("recently_closed", array.toString())
            .apply()
    }

    fun restoreRecentlyClosed(): MutableList<ClosedTabSnapshot> {
        val raw = prefs.getString("recently_closed", null)
            ?: return mutableListOf()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = cleanUrl(item.optString("url"))
                    if (url.isBlank()) continue

                    add(
                        ClosedTabSnapshot(
                            title = item.optString("title").ifBlank { "Tab" },
                            url = url,
                            incognito = false
                        )
                    )
                }
            }.toMutableList()
        }.getOrElse {
            mutableListOf()
        }
    }
}

data class ClosedTabSnapshot(
    val title: String,
    val url: String,
    val incognito: Boolean = false
)
