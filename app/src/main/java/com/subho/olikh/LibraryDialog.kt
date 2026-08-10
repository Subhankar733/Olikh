package com.subho.olikh

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class LibraryDialog(
    context: Context,
    private val history: List<HistoryEntry>,
    private val bookmarks: List<BookmarkEntry>,
    private val onOpenHistory: (HistoryEntry) -> Unit,
    private val onOpenBookmark: (BookmarkEntry) -> Unit,
    private val onDeleteHistory: (HistoryEntry) -> Unit,
    private val onDeleteBookmark: (BookmarkEntry) -> Unit,
    private val onClearHistory: () -> Unit,
    private val onClearBookmarks: () -> Unit
) : Dialog(context) {

    private val dp = context.resources.displayMetrics.density

    private lateinit var content: LinearLayout
    private lateinit var searchInput: EditText

    init {
        setTitle("Library")

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(18), d(20), d(18))
            background = rounded("#111317", 22f)
        }

        root.addView(TextView(context).apply {
            text = "Library"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(context).apply {
            text = "Your browsing history and saved pages"
            textSize = 14f
            setTextColor(Color.parseColor("#9298A1"))
            setPadding(0, d(4), 0, d(12))
        })

        searchInput = EditText(context).apply {
            hint = "Search history & bookmarks"
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#747B85"))
            setSingleLine(true)
            setPadding(d(14), 0, d(14), 0)
            background = rounded("#191C21", 16f)
        }

        root.addView(
            searchInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                d(48)
            ).apply {
                bottomMargin = d(8)
            }
        )

        val scroll = ScrollView(context)

        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val close = Button(context).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            background = rounded("#1C2026", 18f)
            setOnClickListener { dismiss() }
            installPressAnimation(this)
        }

        root.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                d(48)
            ).apply {
                topMargin = d(14)
            }
        )

        setContentView(root)

        window?.apply {
            setBackgroundDrawableResource(
                android.R.color.transparent
            )
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.94).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.82).toInt()
            )
            setGravity(Gravity.CENTER)
        }

        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    render(s?.toString().orEmpty())
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )

        render("")
    }

    private fun render(query: String) {
        content.removeAllViews()

        val cleanQuery = query.trim()

        val filteredHistory =
            if (cleanQuery.isBlank()) {
                history.take(50)
            } else {
                history.filter {
                    matches(
                        it.title,
                        it.url,
                        cleanQuery
                    )
                }.take(50)
            }

        val filteredBookmarks =
            if (cleanQuery.isBlank()) {
                bookmarks
            } else {
                bookmarks.filter {
                    matches(
                        it.title,
                        it.url,
                        cleanQuery
                    )
                }
            }

        addSectionTitle(
            content,
            "HISTORY",
            filteredHistory.size
        )

        if (filteredHistory.isEmpty()) {
            addEmpty(
                content,
                if (cleanQuery.isBlank()) {
                    "No browsing history yet"
                } else {
                    "No matching history"
                }
            )
        } else {
            filteredHistory.forEach { entry ->
                addEntry(
                    parent = content,
                    title = entry.title,
                    url = entry.url,
                    savedAt = entry.visitedAt,
                    action = {
                        dismiss()
                        onOpenHistory(entry)
                    },
                    deleteAction = {
                        confirmDelete(
                            title = "Delete history entry?",
                            message = entry.title
                                .replace("\n", " ")
                                .trim()
                                .ifBlank { entry.url },
                            action = {
                                onDeleteHistory(entry)
                                dismiss()
                            }
                        )
                    }
                )
            }
        }

        addAction(content, "Clear history") {
            onClearHistory()
            dismiss()
        }

        addSectionTitle(
            content,
            "BOOKMARKS",
            filteredBookmarks.size
        )

        if (filteredBookmarks.isEmpty()) {
            addEmpty(
                content,
                if (cleanQuery.isBlank()) {
                    "No bookmarks saved yet"
                } else {
                    "No matching bookmarks"
                }
            )
        } else {
            filteredBookmarks.forEach { entry ->
                addEntry(
                    parent = content,
                    title = entry.title,
                    url = entry.url,
                    savedAt = entry.savedAt,
                    action = {
                        dismiss()
                        onOpenBookmark(entry)
                    },
                    deleteAction = {
                        confirmDelete(
                            title = "Delete bookmark?",
                            message = entry.title
                                .replace("\n", " ")
                                .trim()
                                .ifBlank { entry.url },
                            action = {
                                onDeleteBookmark(entry)
                                dismiss()
                            }
                        )
                    }
                )
            }
        }

        addAction(content, "Clear bookmarks") {
            onClearBookmarks()
            dismiss()
        }
    }

    private fun matches(
        title: String,
        url: String,
        query: String
    ): Boolean {
        return title.contains(query, ignoreCase = true) ||
            url.contains(query, ignoreCase = true)
    }

    private fun confirmDelete(
        title: String,
        message: String,
        action: () -> Unit
    ) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message.take(160))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                action()
            }
            .show()
    }

    private fun addSectionTitle(
        parent: LinearLayout,
        title: String,
        count: Int
    ) {
        parent.addView(TextView(context).apply {
            text = "$title  ·  $count"
            textSize = 13f
            setTextColor(Color.parseColor("#AEB4BD"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, d(18), 0, d(8))
        })
    }


    private fun addEntry(
        parent: LinearLayout,
        title: String,
        url: String,
        savedAt: Long,
        action: () -> Unit,
        deleteAction: () -> Unit
    ) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(13), d(16), d(10))
            background = rounded("#191C21", 16f)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }

            setOnLongClickListener {
                deleteAction()
                true
            }

            installPressAnimation(this)
        }

        card.addView(TextView(context).apply {
            text = title
                .replace("
", " ")
                .trim()
                .ifBlank { url }
                .take(70)

            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        })

        card.addView(TextView(context).apply {
            text = url
            textSize = 12f
            setTextColor(Color.parseColor("#8E949D"))
            maxLines = 1
            setPadding(0, d(5), 0, 0)
        })

        if (savedAt > 0L) {
            card.addView(TextView(context).apply {
                text = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT
                ).format(Date(savedAt))
                textSize = 10f
                setTextColor(Color.parseColor("#6F7680"))
                setPadding(0, d(5), 0, d(7))
            })
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actions.addView(Button(context).apply {
            text = "OPEN"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = rounded("#252A32", 12f)
            setOnClickListener { action() }
            installPressAnimation(this)
        }, LinearLayout.LayoutParams(0, d(40), 1f).apply {
            rightMargin = d(5)
        })

        actions.addView(Button(context).apply {
            text = "COPY"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = rounded("#20252C", 12f)
            setOnClickListener {
                val clipboard =
                    context.getSystemService(
                        android.content.ClipboardManager::class.java
                    )
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "OLIKH URL",
                        url
                    )
                )
            }
            installPressAnimation(this)
        }, LinearLayout.LayoutParams(0, d(40), 1f).apply {
            rightMargin = d(5)
        })

        actions.addView(Button(context).apply {
            text = "DELETE"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = rounded("#2B2022", 12f)
            setOnClickListener { deleteAction() }
            installPressAnimation(this)
        }, LinearLayout.LayoutParams(0, d(40), 1f))

        card.addView(actions)

        parent.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = d(8)
            }
        )
    }

    private fun addEmpty(
        parent: LinearLayout,
        message: String
    ) {
        parent.addView(TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#777E88"))
            setPadding(d(4), d(12), d(4), d(16))
        })
    }

    private fun addAction(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        parent.addView(TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#C7CBD1"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(12), d(12), d(12), d(12))
            setOnClickListener { action() }
            installPressAnimation(this)
        })
    }

    override fun show() {
        super.show()

        window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.94).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.82).toInt()
            )
        }

        window?.decorView?.apply {
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
            translationY = d(18).toFloat()

            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240L)
                .start()
        }
    }

    private fun installPressAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .alpha(0.82f)
                        .setDuration(90L)
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(150L)
                        .start()
                }
            }

            false
        }
    }

    private fun rounded(
        color: String,
        radius: Float
    ) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = d(radius.toInt()).toFloat()
    }

    private fun d(value: Int): Int =
        (value * dp).toInt()
}
