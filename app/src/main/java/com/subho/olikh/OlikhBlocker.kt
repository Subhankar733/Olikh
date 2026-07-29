package com.subho.olikh

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class OlikhBlocker {

    private val blockedHosts = ConcurrentHashMap.newKeySet<String>()
    private val allowedHosts = ConcurrentHashMap.newKeySet<String>()
    private val blockedCount = AtomicLong(0)

    init {
        blockedHosts.addAll(
            listOf(
                "doubleclick.net",
                "googlesyndication.com",
                "googleadservices.com",
                "google-analytics.com",
                "googletagmanager.com",
                "adservice.google.com",
                "connect.facebook.net"
            )
        )
    }

    fun shouldBlock(url: String): Boolean {
        val host = runCatching {
            Uri.parse(url).host
                ?.lowercase()
                ?.trimEnd('.')
        }.getOrNull() ?: return false

        if (matchesHost(host, allowedHosts)) {
            return false
        }

        if (matchesHost(host, blockedHosts)) {
            blockedCount.incrementAndGet()
            return true
        }

        return false
    }

    fun addBlockedHost(host: String) {
        normalizeHost(host)?.let(blockedHosts::add)
    }

    fun addAllowedHost(host: String) {
        normalizeHost(host)?.let(allowedHosts::add)
    }

    fun blockedRequests(): Long = blockedCount.get()

    fun resetCounter() {
        blockedCount.set(0)
    }

    private fun matchesHost(
        host: String,
        rules: Set<String>
    ): Boolean {
        var current = host

        while (true) {
            if (current in rules) return true

            val dot = current.indexOf('.')
            if (dot < 0) return false

            current = current.substring(dot + 1)
        }
    }

    private fun normalizeHost(value: String): String? {
        val host = value
            .trim()
            .lowercase()
            .trimStart('.')
            .trimEnd('.')

        return host.takeIf { it.isNotBlank() }
    }
}
