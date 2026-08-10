package com.subho.olikh

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class DownloadManagerActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var summary: TextView
    private lateinit var downloadManager: DownloadManager

    private val refreshIntervalMs = 1500L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDownloads()
            root.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(243, 244, 246))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(android.graphics.Color.rgb(17, 19, 24))
        }

        header.addView(Button(this).apply {
            text = "‹"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        header.addView(TextView(this).apply {
            text = "Downloads"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))

        header.addView(Button(this).apply {
            text = "CLEAR"
            textSize = 10f
            setOnClickListener { clearCompleted() }
        }, LinearLayout.LayoutParams(-2, dp(44)))

        root.addView(header)

        summary = TextView(this).apply {
            textSize = 11f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        root.addView(summary)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.tag = list

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        refreshDownloads()
        root.post(refreshRunnable)
    }

    override fun onStop() {
        root.removeCallbacks(refreshRunnable)
        super.onStop()
    }

    private fun refreshDownloads() {
        val list = root.tag as? LinearLayout ?: return
        list.removeAllViews()

        val cursor = runCatching {
            downloadManager.query(DownloadManager.Query())
        }.getOrNull()

        if (cursor == null) {
            summary.text = "Downloads unavailable"
            addEmpty(list, "Android DownloadManager is unavailable on this device.")
            return
        }

        var total = 0
        var active = 0
        var completed = 0

        cursor.use {
            val idIndex = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleIndex = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytesIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            if (idIndex < 0 || titleIndex < 0 || statusIndex < 0 ||
                bytesIndex < 0 || totalBytesIndex < 0) {
                summary.text = "Downloads unavailable"
                addEmpty(list, "Download information is unavailable.")
                return
            }

            val dateIndex = it.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
            val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_URI)
            val localUriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val mediaIndex = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)

            while (it.moveToNext()) {
                total++
                val id = it.getLong(idIndex)
                val title = it.getString(titleIndex).orEmpty().ifBlank { "Download #$id" }
                val status = it.getInt(statusIndex)
                val bytes = it.getLong(bytesIndex)
                val totalBytes = it.getLong(totalBytesIndex)
                val timestamp = if (dateIndex >= 0) it.getLong(dateIndex) else 0L
                val sourceUri = if (uriIndex >= 0) it.getString(uriIndex) else null
                val localUri = if (localUriIndex >= 0) it.getString(localUriIndex) else null
                val mediaType = if (mediaIndex >= 0) it.getString(mediaIndex) else null

                if (status == DownloadManager.STATUS_SUCCESSFUL) completed++
                if (status == DownloadManager.STATUS_RUNNING ||
                    status == DownloadManager.STATUS_PENDING) active++

                addDownloadCard(
                    list, id, title, status, bytes, totalBytes,
                    timestamp, sourceUri, localUri, mediaType
                )
            }
        }

        summary.text = "$total downloads • $active active • $completed completed"
        if (total == 0) addEmpty(list, "No downloads yet.")
    }

    private fun addDownloadCard(
        list: LinearLayout,
        id: Long,
        title: String,
        status: Int,
        bytes: Long,
        totalBytes: Long,
        timestamp: Long,
        sourceUri: String?,
        localUri: String?,
        mediaType: String?
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            setBackgroundColor(android.graphics.Color.WHITE)
        }

        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(20, 22, 27))
        })

        val sizeText = if (totalBytes > 0L) {
            "${formatBytes(bytes)} / ${formatBytes(totalBytes)}"
        } else {
            formatBytes(bytes)
        }

        val dateText = if (timestamp > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(timestamp))
        } else ""

        card.addView(TextView(this).apply {
            text = listOf(statusLabel(status), sizeText, dateText)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            textSize = 10f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, dp(4), 0, dp(8))
        })

        if (status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING) {

            val progress = ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 1000
                progress = if (totalBytes > 0L) {
                    ((bytes.coerceAtLeast(0L).toDouble() * max.toDouble()) /
                        totalBytes.toDouble())
                        .coerceIn(0.0, max.toDouble())
                        .toInt()
                } else {
                    0
                }
            }
            card.addView(progress, LinearLayout.LayoutParams(-1, dp(5)))
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            buttons.addView(actionButton("OPEN") {
                openDownloadedFile(id, localUri, mediaType)
            })
            buttons.addView(actionButton("SHARE") {
                shareDownloadedFile(id, localUri, mediaType)
            })
        }

        if (status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING) {
            buttons.addView(actionButton("CANCEL") {
                downloadManager.remove(id)
                refreshDownloads()
                toast("Download cancelled")
            })
        }

        if (status == DownloadManager.STATUS_FAILED) {
            buttons.addView(actionButton("REMOVE") {
                downloadManager.remove(id)
                refreshDownloads()
            })
        }

        if (!sourceUri.isNullOrBlank()) {
            buttons.addView(actionButton("COPY URL") {
                copyText("Download URL", sourceUri)
            })
        }

        buttons.addView(actionButton("DELETE") {
            downloadManager.remove(id)
            refreshDownloads()
        })

        card.addView(buttons)
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(4), 0, dp(4))
        })
    }

    private fun clearCompleted() {
        val cursor = runCatching {
            downloadManager.query(DownloadManager.Query())
        }.getOrNull() ?: return

        var removed = 0
        cursor.use {
            val idIndex = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)

            if (idIndex < 0 || statusIndex < 0) {
                refreshDownloads()
                toast("Download information is unavailable")
                return
            }

            while (it.moveToNext()) {
                if (it.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    if (downloadManager.remove(it.getLong(idIndex)) > 0) removed++
                }
            }
        }

        refreshDownloads()
        toast(if (removed == 0) "No completed downloads"
              else "$removed completed downloads cleared")
    }

    private fun openDownloadedFile(id: Long, localUri: String?, mediaType: String?) {
        val uri = runCatching {
            downloadManager.getUriForDownloadedFile(id)
        }.getOrNull() ?: localUri?.let(Uri::parse)

        if (uri == null) {
            toast("Downloaded file is unavailable")
            return
        }

        val mime = mediaType?.takeIf { it.isNotBlank() } ?: guessMime(uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching { startActivity(intent) }
            .onFailure { toast("No app can open this file") }
    }

    private fun shareDownloadedFile(id: Long, localUri: String?, mediaType: String?) {
        val uri = runCatching {
            downloadManager.getUriForDownloadedFile(id)
        }.getOrNull() ?: localUri?.let(Uri::parse)

        if (uri == null) {
            toast("Downloaded file is unavailable")
            return
        }

        val mime = mediaType?.takeIf { it.isNotBlank() } ?: guessMime(uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("OLIKH download", uri)
        }

        runCatching {
            startActivity(Intent.createChooser(intent, "Share download"))
        }.onFailure {
            toast("Unable to share this file")
        }
    }

    private fun guessMime(uri: Uri): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }

    private fun statusLabel(status: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "Pending"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_PAUSED -> "Paused by Android"
        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
        DownloadManager.STATUS_FAILED -> "Failed"
        else -> "Unknown"
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 9f
            setOnClickListener { action() }
        }

    private fun addEmpty(list: LinearLayout, text: String) {
        list.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(60), dp(20), dp(60))
        })
    }

    private fun copyText(label: String, value: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        toast("Copied")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        if (bytes < 1024L * 1024L) return "%.1f KB".format(bytes / 1024.0)
        if (bytes < 1024L * 1024L * 1024L) {
            return "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
        return "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
