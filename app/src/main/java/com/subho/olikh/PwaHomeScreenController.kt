package com.subho.olikh

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build

class PwaHomeScreenController(
    private val activity: Activity
) {
    fun createHomeScreenShortcut(
        url: String,
        title: String
    ): Boolean {
        val cleanUrl = url.trim()

        if (
            cleanUrl.isBlank() ||
            cleanUrl == "about:blank" ||
            (!cleanUrl.startsWith("http://", true) &&
             !cleanUrl.startsWith("https://", true))
        ) {
            return false
        }

        return runCatching {
            val host = runCatching {
                Uri.parse(cleanUrl).host
            }.getOrNull()

            val safeTitle = title.trim()
                .ifBlank { host ?: "OLIKH Web App" }
                .replace(Regex("\\s+"), " ")
                .take(40)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return@runCatching false
            }

            val manager =
                activity.getSystemService(
                    Context.SHORTCUT_SERVICE
                ) as android.content.pm.ShortcutManager

            if (!manager.isRequestPinShortcutSupported) {
                return@runCatching false
            }

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(cleanUrl)
            ).setClass(
                activity,
                MainActivity::class.java
            )

            val shortcut =
                android.content.pm.ShortcutInfo.Builder(
                    activity,
                    "olikh_web_${cleanUrl.hashCode()}"
                )
                    .setShortLabel(safeTitle)
                    .setLongLabel("Open $safeTitle in OLIKH")
                    .setIcon(
                        Icon.createWithResource(
                            activity,
                            activity.applicationInfo.icon
                        )
                    )
                    .setIntent(intent)
                    .build()

            manager.requestPinShortcut(shortcut, null)
            true
        }.getOrDefault(false)
    }
}
