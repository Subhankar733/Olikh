#!/bin/bash

echo "=========================================="
echo "    OLIKH BROWSER FULL ENGINE BUILDER     "
echo "=========================================="

# 1. Update BrowserTab Model
cat << 'TAB_MODEL_EOF' > app/src/main/java/com/subho/olikh/BrowserTab.kt
package com.subho.olikh

import android.graphics.Bitmap
import android.webkit.WebView

data class BrowserTab(
    var webView: WebView,
    var title: String = "New Tab",
    var url: String = "",
    var failedUrl: String? = null,
    var showingError: Boolean = false,
    var incognito: Boolean = false,
    var favicon: Bitmap? = null,
    var lastAccessed: Long = System.currentTimeMillis()
)
TAB_MODEL_EOF

# 2. Update DownloadHelper
cat << 'DL_HELPER_EOF' > app/src/main/java/com/subho/olikh/DownloadHelper.kt
package com.subho.olikh

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast

class DownloadHelper(private val context: Context) {

    fun downloadFile(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading file via OLIKH Browser...")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Download Started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
DL_HELPER_EOF

# 3. Update Colors (Material 3 Palette)
cat << 'COLORS_EOF' > app/src/main/res/values/colors.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#006495</color>
    <color name="onPrimary">#FFFFFF</color>
    <color name="primaryContainer">#CBE6FF</color>
    <color name="onPrimaryContainer">#001E30</color>
    <color name="background">#FCFCFF</color>
    <color name="onBackground">#191C1E</color>
    <color name="surface">#FCFCFF</color>
    <color name="onSurface">#191C1E</color>
    <color name="surfaceVariant">#DEE3EB</color>
    <color name="onSurfaceVariant">#42474E</color>
    <color name="outline">#72777F</color>
</resources>
COLORS_EOF

# 4. Git Commit and Push
echo "Syncing changes to GitHub..."
git add .
git commit -m "feat(core): update Tab model, Download Engine, and M3 Colors"
git push origin main

echo "=========================================="
echo "    ALL FEATURES UPDATED & PUSHED!        "
echo "=========================================="
