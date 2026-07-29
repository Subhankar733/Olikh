package com.subho.olikh

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class OlikhBlocker {

    private val enabled = AtomicBoolean(true)

    private val blockedHosts =
        ConcurrentHashMap.newKeySet<String>()

    private val allowedHosts =
        ConcurrentHashMap.newKeySet<String>()

    private val blockedCount = AtomicLong(0)

    init {
        blockedHosts.addAll(DEFAULT_BLOCKED_HOSTS)
    }

    fun shouldBlock(url: String): Boolean {
        if (!enabled.get()) return false

        val host = extractHost(url) ?: return false

        if (matchesHost(host, allowedHosts)) {
            return false
        }

        if (matchesHost(host, blockedHosts)) {
            blockedCount.incrementAndGet()
            return true
        }

        return false
    }

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun isEnabled(): Boolean =
        enabled.get()

    fun addBlockedHost(host: String) {
        normalizeHost(host)?.let {
            blockedHosts.add(it)
        }
    }

    fun addBlockedHosts(hosts: Collection<String>) {
        hosts.forEach(::addBlockedHost)
    }

    fun removeBlockedHost(host: String) {
        normalizeHost(host)?.let {
            blockedHosts.remove(it)
        }
    }

    fun addAllowedHost(host: String) {
        normalizeHost(host)?.let {
            allowedHosts.add(it)
        }
    }

    fun removeAllowedHost(host: String) {
        normalizeHost(host)?.let {
            allowedHosts.remove(it)
        }
    }

    fun clearAllowlist() {
        allowedHosts.clear()
    }

    fun blockedRequests(): Long =
        blockedCount.get()

    fun resetCounter() {
        blockedCount.set(0)
    }

    fun blockedHostCount(): Int =
        blockedHosts.size

    fun allowedHostCount(): Int =
        allowedHosts.size

    private fun extractHost(url: String): String? {
        return runCatching {
            Uri.parse(url)
                .host
                ?.lowercase()
                ?.trimEnd('.')
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
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

            val dot = current.indexOf('.')

            if (dot < 0) {
                return false
            }

            current =
                current.substring(dot + 1)
        }
    }

    private fun normalizeHost(
        value: String
    ): String? {

        var host = value
            .trim()
            .lowercase()

        if (host.startsWith("http://") ||
            host.startsWith("https://")
        ) {
            host = runCatching {
                Uri.parse(host).host
            }.getOrNull() ?: return null
        }

        host = host
            .trimStart('.')
            .trimEnd('.')

        return host.takeIf {
            it.isNotBlank()
        }
    }

    companion object {

        private val DEFAULT_BLOCKED_HOSTS =
            setOf(

                // Google advertising
                "doubleclick.net",
                "googlesyndication.com",
                "googleadservices.com",
                "adservice.google.com",
                "pagead2.googlesyndication.com",
                "securepubads.g.doubleclick.net",

                // Google tracking / analytics
                "google-analytics.com",
                "googletagmanager.com",

                // Meta tracking
                "connect.facebook.net",

                // Microsoft advertising
                "bat.bing.com",
                "ads.microsoft.com",

                // Amazon advertising
                "amazon-adsystem.com",

                // Yahoo advertising
                "ads.yahoo.com",

                // Common ad networks
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

                // Mobile advertising
                "adcolony.com",
                "applovin.com",
                "unityads.unity3d.com",
                "vungle.com",
                "inmobi.com",
                "chartboost.com",

                // Analytics / tracking
                "scorecardresearch.com",
                "quantserve.com",
                "hotjar.com",
                "mixpanel.com",
                "segment.io",
                "segment.com",

                // Additional tracking / advertising
                "moatads.com",
                "advertising.com",
                "media.net",
                "yieldmo.com",
                "yieldmanager.com"
            )
    }
}
