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
            it.length <= 8192 &&
            it != "about:blank" &&
            !it.startsWith("data:", ignoreCase = true)
        }.orEmpty()

    private fun cleanTitle(title: String): String =
        title.trim()
            .take(300)
            .ifBlank { "Tab" }

    fun save(
        tabs: List<BrowserTab>,
        activeIndex: Int
    ) {
        val normal = JSONArray()
        val seenUrls = HashSet<String>()

        tabs.filter { !it.incognito }.forEach { tab ->
            val url = cleanUrl(tab.webView.url ?: tab.url)
            if (url.isBlank() || !seenUrls.add(url)) return@forEach

            normal.put(
                JSONObject()
                    .put("title", cleanTitle(tab.title))
                    .put("url", url)
            )
        }

        val activePosition =
            activeIndex.coerceIn(
                0,
                (tabs.size - 1).coerceAtLeast(0)
            )

        val activeNormalIndex =
            if (tabs.isEmpty()) {
                0
            } else {
                val visibleNormalTabs =
                    tabs.take(activePosition + 1)
                        .count { !it.incognito }

                (visibleNormalTabs - 1).coerceAtLeast(0)
            }

        val safeActive =
            if (normal.length() == 0) 0
            else activeNormalIndex.coerceIn(0, normal.length() - 1)

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
        val seenUrls = HashSet<String>()

        entries
            .filter { !it.incognito }
            .take(20)
            .forEach {
                val url = cleanUrl(it.url)
                if (url.isBlank() || !seenUrls.add(url)) return@forEach

                array.put(
                    JSONObject()
                        .put("title", cleanTitle(it.title))
                        .put("url", url)
                        .put("groupId", it.groupId.orEmpty())
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
                            title = cleanTitle(item.optString("title")),
                            url = url,
                            groupId = item.optString("groupId").trim().ifBlank { null },
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
    val groupId: String? = null,
    val incognito: Boolean = false
)
