package com.subho.olikh

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

enum class DnsProvider(val title: String, val dohUrl: String) {
    SYSTEM("System Default", ""),
    CLOUDFLARE("Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query"),
    ADGUARD("AdGuard DNS", "https://dns.adguard-dns.com/dns-query"),
    GOOGLE("Google DNS", "https://dns.google/dns-query")
}

class OlikhDnsEngine(context: Context) {
    private val prefs = context.getSharedPreferences("olikh_dns", Context.MODE_PRIVATE)
    private val currentProvider = AtomicReference(
        DnsProvider.valueOf(prefs.getString("provider", DnsProvider.SYSTEM.name) ?: DnsProvider.SYSTEM.name)
    )

    fun getProvider(): DnsProvider = currentProvider.get()

    fun setProvider(provider: DnsProvider) {
        currentProvider.set(provider)
        prefs.edit().putString("provider", provider.name).apply()
    }

    fun getDnsQueryUrl(): String = currentProvider.get().dohUrl

    fun isDohActive(): Boolean = currentProvider.get() != DnsProvider.SYSTEM
}
