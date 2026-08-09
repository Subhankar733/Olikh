package com.subho.olikh

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast

class DownloadHelper(
    private val context: Context
) {

    fun downloadFile(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val cleanUrl = url.trim()

        if (
            !cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            Toast.makeText(
                context,
                "This download link is not supported.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        runCatching {
            val fileName = sanitizeFileName(
                URLUtil.guessFileName(
                    cleanUrl,
                    contentDisposition,
                    mimeType
                )
            )

            val request = DownloadManager.Request(
                Uri.parse(cleanUrl)
            ).apply {
                setTitle(fileName)
                setDescription("Downloading with OLIKH")
                setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )

                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }

                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }

                CookieManager.getInstance()
                    .getCookie(cleanUrl)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { cookies ->
                        addRequestHeader("Cookie", cookies)
                    }
            }

            val manager =
                context.getSystemService(
                    Context.DOWNLOAD_SERVICE
                ) as DownloadManager

            val downloadId = manager.enqueue(request)

            context.getSharedPreferences(
                "olikh_downloads",
                Context.MODE_PRIVATE
            ).edit()
                .putString("download_$downloadId", fileName)
                .putString("url_$downloadId", cleanUrl)
                .apply()

            Toast.makeText(
                context,
                "Download started: $fileName",
                Toast.LENGTH_SHORT
            ).show()

        }.onFailure { error ->
            Toast.makeText(
                context,
                "Download failed: ${error.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val clean = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')

        return clean.takeIf { it.isNotBlank() } ?: "download"
    }
}
