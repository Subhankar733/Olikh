package com.subho.olikh

import android.content.Context
import android.net.Uri

class SitePermissionManager(context: Context) {

    enum class Decision {
        ASK,
        ALLOW,
        BLOCK
    }

    private val prefs =
        context.getSharedPreferences(
            "site_permissions",
            Context.MODE_PRIVATE
        )

    private fun normalizeHost(origin: String): String? {
        return runCatching {
            Uri.parse(origin).host
                ?.lowercase()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun key(
        origin: String,
        permission: String
    ): String? {
        val host = normalizeHost(origin) ?: return null
        return "$host:$permission"
    }

    fun getDecision(
        origin: String,
        permission: String
    ): Decision {
        val key = key(origin, permission)
            ?: return Decision.ASK

        return when (prefs.getString(key, null)) {
            "allow" -> Decision.ALLOW
            "block" -> Decision.BLOCK
            else -> Decision.ASK
        }
    }

    fun setDecision(
        origin: String,
        permission: String,
        decision: Decision
    ) {
        val key = key(origin, permission) ?: return

        val editor = prefs.edit()

        when (decision) {
            Decision.ALLOW ->
                editor.putString(key, "allow")

            Decision.BLOCK ->
                editor.putString(key, "block")

            Decision.ASK ->
                editor.remove(key)
        }

        editor.apply()
    }

    fun getSavedPermissions(): Map<String, Map<String, Decision>> {
        val result =
            linkedMapOf<String, MutableMap<String, Decision>>()

        prefs.all.forEach { (key, value) ->
            val separator = key.lastIndexOf(':')

            if (separator <= 0 || separator >= key.lastIndex) {
                return@forEach
            }

            val host = key.substring(0, separator)
            val permission = key.substring(separator + 1)

            val decision = when (value as? String) {
                "allow" -> Decision.ALLOW
                "block" -> Decision.BLOCK
                else -> return@forEach
            }

            result
                .getOrPut(host) { linkedMapOf() }[permission] =
                decision
        }

        return result
    }

    fun clearSite(origin: String) {
        val host = normalizeHost(origin) ?: return
        val prefix = "$host:"

        val editor = prefs.edit()

        prefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(editor::remove)

        editor.apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
