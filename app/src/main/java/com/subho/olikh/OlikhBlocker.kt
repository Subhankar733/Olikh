package com.subho.olikh

import android.content.Context
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class OlikhBlocker(
    context: Context
) {

    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            "olikh_blocker",
            Context.MODE_PRIVATE
        )

    private val enabled =
        AtomicBoolean(
            prefs.getBoolean("enabled", true)
        )

    private val blockedHosts =
        ConcurrentHashMap.newKeySet<String>()

    private val allowedHosts =
        ConcurrentHashMap.newKeySet<String>()

    private val blockedCount =
        AtomicLong(
            prefs.getLong("blocked_count", 0L)
        )

    init {
        blockedHosts.addAll(DEFAULT_BLOCKED_HOSTS)

        loadAssetBlocklist()

        prefs.getStringSet(
            "allowed_hosts",
            emptySet()
        )
            .orEmpty()
            .forEach { host ->
                normalizeHost(host)?.let {
                    allowedHosts.add(it)
                }
            }
    }

    fun shouldBlock(url: String): Boolean {
        if (!enabled.get()) {
            return false
        }

        val host =
            extractHost(url)
                ?: return false

        if (matchesHost(host, allowedHosts)) {
            return false
        }

        if (matchesHost(host, blockedHosts)) {
            val count =
                blockedCount.incrementAndGet()

            prefs.edit()
                .putLong("blocked_count", count)
                .apply()

            return true
        }

        return false
    }

    fun setEnabled(value: Boolean) {
        enabled.set(value)

        prefs.edit()
            .putBoolean("enabled", value)
            .apply()
    }

    fun isEnabled(): Boolean =
        enabled.get()

    fun addBlockedHost(host: String) {
        normalizeHost(host)?.let {
            blockedHosts.add(it)
        }
    }

    fun addBlockedHosts(
        hosts: Collection<String>
    ) {
        hosts.forEach(::addBlockedHost)
    }

    fun removeBlockedHost(host: String) {
        normalizeHost(host)?.let {
            blockedHosts.remove(it)
        }
    }

    fun addAllowedHost(host: String) {
        val normalized =
            normalizeHost(host)
                ?: return

        allowedHosts.add(normalized)
        saveAllowlist()
    }

    fun removeAllowedHost(host: String) {
        val normalized =
            normalizeHost(host)
                ?: return

        allowedHosts.remove(normalized)
        saveAllowlist()
    }

    fun clearAllowlist() {
        allowedHosts.clear()
        saveAllowlist()
    }

    fun blockedRequests(): Long =
        blockedCount.get()

    fun resetCounter() {
        blockedCount.set(0)

        prefs.edit()
            .putLong("blocked_count", 0L)
            .apply()
    }

    fun blockedHostCount(): Int =
        blockedHosts.size

    fun allowedHostCount(): Int =
        allowedHosts.size

    private fun saveAllowlist() {
        prefs.edit()
            .putStringSet(
                "allowed_hosts",
                HashSet(allowedHosts)
            )
            .apply()
    }

    private fun loadAssetBlocklist() {
        runCatching {
            appContext.assets
                .open("olikh_blocklist.txt")
                .bufferedReader()
                .useLines { lines ->
                    lines.forEach { rawLine ->
                        parseBlocklistLine(rawLine)
                            ?.let {
                                blockedHosts.add(it)
                            }
                    }
                }
        }
    }

    private fun parseBlocklistLine(
        rawLine: String
    ): String? {

        var line =
            rawLine.trim()

        if (line.isBlank()) {
            return null
        }

        if (
            line.startsWith("#") ||
            line.startsWith("!") ||
            line.startsWith("[")
        ) {
            return null
        }

        val commentIndex =
            line.indexOf('#')

        if (commentIndex >= 0) {
            line =
                line.substring(
                    0,
                    commentIndex
                ).trim()
        }

        if (line.isBlank()) {
            return null
        }

        val parts =
            line.split(
                Regex("\\s+")
            )

        if (parts.size >= 2 &&
            (
                parts[0] == "0.0.0.0" ||
                parts[0] == "127.0.0.1" ||
                parts[0] == "::1"
            )
        ) {
            line = parts[1]
        }

        if (line.startsWith("||")) {
            line =
                line.removePrefix("||")
                    .substringBefore("^")
                    .substringBefore("/")
        }

        if (
            line.contains("*") ||
            line.contains("/") ||
            line.contains("=") ||
            line.contains("?")
        ) {
            return null
        }

        return normalizeHost(line)
    }

    private fun extractHost(
        url: String
    ): String? {

        return runCatching {
            Uri.parse(url)
                .host
                ?.lowercase()
                ?.trimEnd('.')
        }
            .getOrNull()
            ?.takeIf {
                it.isNotBlank()
            }
    }

    private fun matchesHost(
        host: String,
        rules: Set<String>
    ): Boolean {

        var current = host

        while (true) {
            if (current in rules) {
                return true
            }

            val dot =
                current.indexOf('.')

            if (dot < 0) {
                return false
            }

            current =
                current.substring(
                    dot + 1
                )
        }
    }

    private fun normalizeHost(
        value: String
    ): String? {

        var host =
            value
                .trim()
                .lowercase()

        if (
            host.startsWith("http://") ||
            host.startsWith("https://")
        ) {
            host =
                runCatching {
                    Uri.parse(host).host
                }.getOrNull()
                    ?: return null
        }

        host =
            host
                .trimStart('.')
                .trimEnd('.')

        if (
            host.isBlank() ||
            host == "localhost" ||
            host == "localhost.localdomain"
        ) {
            return null
        }

        return host
    }

    companion object {

        private val DEFAULT_BLOCKED_HOSTS =
            setOf(
                "doubleclick.net",
                "googlesyndication.com",
                "googleadservices.com",
                "adservice.google.com",
                "google-analytics.com",
                "googletagmanager.com",
                "connect.facebook.net",
                "bat.bing.com",
                "ads.microsoft.com",
                "amazon-adsystem.com",
                "ads.yahoo.com",
                "adnxs.com",
                "adsrvr.org",
                "criteo.com",
                "criteo.net",
                "taboola.com",
                "outbrain.com",
                "pubmatic.com",
                "rubiconproject.com",
                "openx.net",
                "casalemedia.com",
                "smartadserver.com",
                "adcolony.com",
                "applovin.com",
                "unityads.unity3d.com",
                "vungle.com",
                "inmobi.com",
                "chartboost.com",
                "scorecardresearch.com",
                "quantserve.com",
                "hotjar.com",
                "mixpanel.com",
                "segment.io",
                "segment.com",
                "moatads.com",
                "advertising.com",
                "media.net",
                "yieldmo.com",
                "yieldmanager.com"
            )
    }
}
