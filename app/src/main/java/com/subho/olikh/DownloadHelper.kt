package com.subho.olikh

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast

object DownloadHelper {

    fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(
                context,
                "This download link is not supported.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        runCatching {
            val fileName = URLUtil.guessFileName(
                url,
                contentDisposition,
                mimeType
            )

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading with OLIKH")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
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
                    .getCookie(url)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        addRequestHeader("Cookie", it)
                    }
            }

            val manager =
                context.getSystemService(Context.DOWNLOAD_SERVICE)
                    as DownloadManager

            manager.enqueue(request)

            Toast.makeText(
                context,
                "Downloading: $fileName",
                Toast.LENGTH_SHORT
            ).show()

        }.onFailure {
            Toast.makeText(
                context,
                "Download failed to start.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
