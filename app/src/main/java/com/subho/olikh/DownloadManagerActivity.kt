package com.subho.olikh

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.DateFormat
import java.util.Date

class DownloadManagerActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var summary: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var downloadManager: DownloadManager

    // Speed tracking cache: ID -> Pair(lastBytes, lastTimestamp)
    private val speedTracker = mutableMapOf<Long, Pair<Long, Long>>()

    private val refreshIntervalMs = 1000L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDownloads()
            root.postDelayed(this, refreshIntervalMs)
        }
    }

    private fun attachButtonAnim(v: View) {
        v.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.85f).setDuration(80L).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120L).start()
                }
            }
            false
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun panel(color: Int, radius: Int = 14, strokeColor: Int? = null, strokeWidth: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            strokeColor?.let { setStroke(dp(strokeWidth), it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#090D16"))
        }

        // Top App Bar
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = panel(Color.parseColor("#0F172A"), 0)
        }

        val backBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            background = panel(Color.parseColor("#1E293B"), 10)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            attachButtonAnim(this)
            setOnClickListener { finish() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(38), dp(38)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply {
            text = "Downloads"
            textSize = 19f
            setTextColor(Color.parseColor("#F8FAFC"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        summary = TextView(this).apply {
            text = "Scanning..."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        titleBox.addView(summary)
        header.addView(titleBox)

        val clearBtn = Button(this).apply {
            text = "Clear"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#EF4444"))
            background = panel(Color.parseColor("#1E293B"), 10, Color.parseColor("#334155"))
            attachButtonAnim(this)
            attachButtonAnim(this)
                setOnClickListener { confirmClearCompleted() }
        }
        header.addView(clearBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)))
        root.addView(header)

        // Scrollable List
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(36))
        }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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
        listContainer.removeAllViews()

        val cursor = runCatching {
            downloadManager.query(DownloadManager.Query())
        }.getOrNull()

        if (cursor == null) {
            summary.text = "Downloads unavailable"
            addEmpty("Android Download Manager is unavailable.")
            return
        }

        var total = 0
        var active = 0
        var completed = 0
        val now = System.currentTimeMillis()

        cursor.use {
            val idIndex = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleIndex = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytesIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val dateIndex = it.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
            val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_URI)
            val localUriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val mediaIndex = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)

            if (idIndex < 0 || titleIndex < 0 || statusIndex < 0 || bytesIndex < 0 || totalBytesIndex < 0) {
                summary.text = "Downloads unavailable"
                addEmpty("Details unavailable.")
                return
            }

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
                val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else 0

                // Speed calculation
                var speedString = ""
                if (status == DownloadManager.STATUS_RUNNING) {
                    val prev = speedTracker[id]
                    if (prev != null) {
                        val timeDiff = (now - prev.second) / 1000.0
                        if (timeDiff > 0.4) {
                            val byteDiff = bytes - prev.first
                            if (byteDiff > 0) {
                                val speedBps = (byteDiff / timeDiff).toLong()
                                speedString = " • " + formatBytes(speedBps) + "/s"
                            }
                        }
                    }
                    speedTracker[id] = Pair(bytes, now)
                } else {
                    speedTracker.remove(id)
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL) completed++
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) active++

                addDownloadCard(
                    id, title, status, bytes, totalBytes, timestamp,
                    sourceUri, localUri, mediaType, reason, speedString
                )
            }
        }

        summary.text = "$total items • $active active • $completed done"
        if (total == 0) addEmpty("No downloads found.")
    }

    private fun getFileCategoryIcon(fileName: String, mimeType: String?): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp") || mimeType?.startsWith("video/") == true -> "🎬"
            ext in listOf("mp3", "wav", "m4a", "flac", "ogg", "aac") || mimeType?.startsWith("audio/") == true -> "🎵"
            ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") || mimeType?.startsWith("image/") == true -> "🖼️"
            ext in listOf("zip", "rar", "7z", "tar", "gz", "apk", "bin") -> "📦"
            ext in listOf("pdf", "doc", "docx", "txt", "epub") -> "📄"
            else -> "📁"
        }
    }

    private fun addDownloadCard(
        id: Long,
        title: String,
        status: Int,
        bytes: Long,
        totalBytes: Long,
        timestamp: Long,
        sourceUri: String?,
        localUri: String?,
        mediaType: String?,
        reason: Int,
        speedString: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panel(Color.parseColor("#121722"), 16, Color.parseColor("#1E2738"))
            alpha = 0f
            translationY = dp(6).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(180L).start()
        }

        // Top Row: Icon + Title + Status Pill
        val topRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val iconBadge = TextView(this).apply {
            text = getFileCategoryIcon(title, mediaType)
            textSize = 15f
            gravity = Gravity.CENTER
            background = panel(Color.parseColor("#1E293B"), 10)
        }
        topRow.addView(iconBadge, LinearLayout.LayoutParams(dp(36), dp(36)))

        val titleView = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#F8FAFC"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setSingleLine(true)
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(titleView)

        val statusBadge = TextView(this).apply {
            text = statusLabel(status)
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setTextColor(statusColor(status))
            background = panel(statusBgColor(status), 8)
        }
        topRow.addView(statusBadge)
        card.addView(topRow)

        // Progress Details / Sub-Info
        val progressPercent = if (totalBytes > 0L) ((bytes * 100) / totalBytes).toInt() else 0
        val sizeText = if (totalBytes > 0L) {
            "${formatBytes(bytes)} / ${formatBytes(totalBytes)} ($progressPercent%)$speedString"
        } else {
            "${formatBytes(bytes)}$speedString"
        }

        val dateText = if (timestamp > 0L) " • " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp)) else ""
        val reasonText = downloadReasonLabel(status, reason)
        val subInfo = TextView(this).apply {
            text = "$sizeText$reasonText$dateText"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(8), 0, dp(8))
        }
        card.addView(subInfo)

        // Progress Bar
        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
            val bgDrawable = panel(Color.parseColor("#1B2333"), 4)
            val progressDrawable = panel(Color.parseColor("#38BDF8"), 4)
            val clipProgress = android.graphics.drawable.ClipDrawable(progressDrawable, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(bgDrawable, clipProgress)).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }

            val pBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = totalBytes <= 0L
                max = 100
                progressDrawable = layerDrawable
                setProgressBarSmooth(this, progressPercent)
            }
            card.addView(pBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)).apply {
                bottomMargin = dp(10)
            })
        }

        // Action Buttons Row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            val openBtn = Button(this).apply {
                text = "Open"
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.WHITE)
                background = panel(Color.parseColor("#2563EB"), 8)
                attachButtonAnim(this)
                setOnClickListener { openDownloadedFile(id, localUri, mediaType) }
            }
            btnRow.addView(openBtn, LinearLayout.LayoutParams(dp(68), dp(32)).apply {
                marginEnd = dp(8)
            })

            val shareBtn = Button(this).apply {
                text = "Share"
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.WHITE)
                background = panel(Color.parseColor("#0F766E"), 8)
                attachButtonAnim(this)
                setOnClickListener { shareDownloadedFile(id, mediaType) }
            }
            btnRow.addView(shareBtn, LinearLayout.LayoutParams(dp(68), dp(32)).apply {
                marginEnd = dp(8)
            })
        }

        if (status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_PAUSED
        ) {
            val cancelBtn = Button(this).apply {
                text = "Cancel"
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.parseColor("#F87171"))
                background = panel(Color.parseColor("#1E293B"), 8, Color.parseColor("#334155"))
                attachButtonAnim(this)
                setOnClickListener {
                    card.animate().alpha(0f).scaleY(0.85f).setDuration(120L).withEndAction {
                        downloadManager.remove(id)
                        speedTracker.remove(id)
                        refreshDownloads()
                        Toast.makeText(
                            this@DownloadManagerActivity,
                            "Download cancelled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.start()
                }
            }
            btnRow.addView(cancelBtn, LinearLayout.LayoutParams(dp(72), dp(32)).apply {
                marginEnd = dp(8)
            })
        }

        if (status == DownloadManager.STATUS_FAILED) {
            val retryBtn = Button(this).apply {
                text = "Retry"
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.WHITE)
                background = panel(Color.parseColor("#2563EB"), 8)
                attachButtonAnim(this)
                setOnClickListener {
                    if (sourceUri.isNullOrBlank()) {
                        Toast.makeText(
                            this@DownloadManagerActivity,
                            "Original download URL unavailable",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        DownloadHelper(this@DownloadManagerActivity)
                            .downloadFile(
                                sourceUri,
                                null,
                                title,
                                mediaType
                            )
                        downloadManager.remove(id)
                        refreshDownloads()
                    }
                }
            }
            btnRow.addView(retryBtn, LinearLayout.LayoutParams(dp(68), dp(32)).apply {
                marginEnd = dp(8)
            })
        }

        if (!sourceUri.isNullOrBlank()) {
            val copyLinkBtn = Button(this).apply {
                text = "Link"
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                background = panel(Color.parseColor("#1E293B"), 8, Color.parseColor("#334155"))
                attachButtonAnim(this)
                setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", sourceUri))
                    Toast.makeText(this@DownloadManagerActivity, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            btnRow.addView(copyLinkBtn, LinearLayout.LayoutParams(dp(58), dp(32)).apply {
                marginEnd = dp(8)
            })
        }

        val deleteBtn = Button(this).apply {
            text = "Delete"
            isAllCaps = false
            textSize = 11f
            setTextColor(Color.parseColor("#EF4444"))
            background = panel(Color.parseColor("#1E293B"), 8, Color.parseColor("#334155"))
            attachButtonAnim(this)
            setOnClickListener {
                card.animate().alpha(0f).scaleY(0.85f).setDuration(120L).withEndAction {
                    downloadManager.remove(id)
                    refreshDownloads()
                }.start()
            }
        }
        btnRow.addView(deleteBtn, LinearLayout.LayoutParams(dp(68), dp(32)))
        card.addView(btnRow)

        listContainer.addView(
            card,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun addEmpty(message: String) {
        val emptyView = TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, dp(48), 0, dp(48))
        }
        listContainer.addView(emptyView)
    }

    private fun shareDownloadedFile(
        downloadId: Long,
        mediaType: String?
    ) {
        val downloadedUri = runCatching {
            downloadManager.getUriForDownloadedFile(downloadId)
        }.getOrNull()

        if (downloadedUri == null) {
            Toast.makeText(
                this,
                "Downloaded file URI unavailable",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val resolvedMime = runCatching {
            contentResolver.getType(downloadedUri)
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: mediaType?.takeIf { it.isNotBlank() }
            ?: "*/*"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = resolvedMime
            putExtra(Intent.EXTRA_STREAM, downloadedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(
                "Downloaded file",
                downloadedUri
            )
        }

        runCatching {
            startActivity(Intent.createChooser(intent, "Share file"))
        }.onFailure {
            Toast.makeText(
                this,
                "No app found to share this file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openDownloadedFile(
        downloadId: Long,
        localUri: String?,
        mediaType: String?
    ) {
        val downloadedUri = runCatching {
            downloadManager.getUriForDownloadedFile(downloadId)
        }.getOrNull()

        if (downloadedUri == null) {
            Toast.makeText(
                this,
                "Downloaded file URI unavailable",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val uriMime = runCatching {
            contentResolver.getType(downloadedUri)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val extensionMime = localUri
            ?.takeIf { it.isNotBlank() }
            ?.let {
                MimeTypeMap.getFileExtensionFromUrl(it)
                    ?.takeIf { ext -> ext.isNotBlank() }
                    ?.let { ext ->
                        MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(ext.lowercase())
                    }
            }

        val resolvedMime = uriMime
            ?: extensionMime
            ?: mediaType?.takeIf { it.isNotBlank() }
            ?: "*/*"

        fun launch(mime: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(downloadedUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri(
                    "Downloaded file",
                    downloadedUri
                )
            }

            return runCatching {
                if (intent.resolveActivity(packageManager) == null) {
                    false
                } else {
                    startActivity(intent)
                    true
                }
            }.getOrDefault(false)
        }

        if (!launch(resolvedMime) && resolvedMime != "*/*" && !launch("*/*")) {
            Toast.makeText(
                this,
                "No app found to open this file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmClearCompleted() {
        AlertDialog.Builder(this)
            .setTitle("Clear Completed")
            .setMessage("Remove all finished downloads from the list?")
            .setPositiveButton("Clear") { _, _ ->
                val cursor = runCatching { downloadManager.query(DownloadManager.Query()) }.getOrNull() ?: return@setPositiveButton
                val idsToRemove = mutableListOf<Long>()
                cursor.use {
                    val idIdx = it.getColumnIndex(DownloadManager.COLUMN_ID)
                    val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (idIdx >= 0 && statusIdx >= 0) {
                        while (it.moveToNext()) {
                            val st = it.getInt(statusIdx)
                            if (st == DownloadManager.STATUS_SUCCESSFUL || st == DownloadManager.STATUS_FAILED) {
                                idsToRemove.add(it.getLong(idIdx))
                            }
                        }
                    }
                }
                if (idsToRemove.isNotEmpty()) {
                    downloadManager.remove(*idsToRemove.toLongArray())
                }
                refreshDownloads()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadReasonLabel(status: Int, reason: Int): String {
        if (status != DownloadManager.STATUS_FAILED &&
            status != DownloadManager.STATUS_PAUSED
        ) return ""

        val label = when {
            status == DownloadManager.STATUS_PAUSED &&
                reason == DownloadManager.PAUSED_WAITING_TO_RETRY ->
                "Waiting to retry"

            status == DownloadManager.STATUS_PAUSED &&
                reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK ->
                "Waiting for network"

            status == DownloadManager.STATUS_PAUSED &&
                reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
                "Waiting for Wi-Fi"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                "Insufficient storage"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
                "File already exists"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_CANNOT_RESUME ->
                "Cannot resume"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                "HTTP error"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_HTTP_DATA_ERROR ->
                "HTTP data error"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                "Too many redirects"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                "Storage device unavailable"

            status == DownloadManager.STATUS_FAILED &&
                reason == DownloadManager.ERROR_FILE_ERROR ->
                "File error"

            else -> if (status == DownloadManager.STATUS_FAILED)
                "Download failed"
            else
                "Paused"
        }

        return " • $label"
    }

    private fun statusLabel(status: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "Pending"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_PAUSED -> "Paused"
        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
        DownloadManager.STATUS_FAILED -> "Failed"
        else -> "Unknown"
    }

    private fun statusColor(status: Int): Int = when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> Color.parseColor("#4ADE80")
        DownloadManager.STATUS_RUNNING -> Color.parseColor("#60A5FA")
        DownloadManager.STATUS_PAUSED -> Color.parseColor("#FBBF24")
        DownloadManager.STATUS_FAILED -> Color.parseColor("#F87171")
        else -> Color.parseColor("#94A3B8")
    }

    private fun statusBgColor(status: Int): Int = when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> Color.parseColor("#064E3B")
        DownloadManager.STATUS_RUNNING -> Color.parseColor("#1E3A5F")
        DownloadManager.STATUS_PAUSED -> Color.parseColor("#451A03")
        DownloadManager.STATUS_FAILED -> Color.parseColor("#450A0A")
        else -> Color.parseColor("#1E293B")
    }

    private fun setProgressBarSmooth(progressBar: ProgressBar, progress: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(progress, true)
        } else {
            progressBar.progress = progress
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
