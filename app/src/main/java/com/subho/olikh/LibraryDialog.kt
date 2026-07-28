package com.subho.olikh

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LibraryDialog(
    context: Context,
    private val history: List<HistoryEntry>,
    private val bookmarks: List<BookmarkEntry>,
    private val onOpenHistory: (HistoryEntry) -> Unit,
    private val onOpenBookmark: (BookmarkEntry) -> Unit,
    private val onClearHistory: () -> Unit,
    private val onClearBookmarks: () -> Unit
) : Dialog(context) {

    private val dp = context.resources.displayMetrics.density

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
            setPadding(0, d(4), 0, d(18))
        })

        val scroll = ScrollView(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        addSectionTitle(content, "HISTORY", history.size)

        if (history.isEmpty()) {
            addEmpty(content, "No browsing history yet")
        } else {
            history.take(50).forEach { entry ->
                addEntry(
                    content,
                    entry.title,
                    entry.url
                ) {
                    dismiss()
                    onOpenHistory(entry)
                }
            }
        }

        addAction(content, "Clear history") {
            onClearHistory()
            dismiss()
        }

        addSectionTitle(content, "BOOKMARKS", bookmarks.size)

        if (bookmarks.isEmpty()) {
            addEmpty(content, "No bookmarks saved yet")
        } else {
            bookmarks.forEach { entry ->
                addEntry(
                    content,
                    entry.title,
                    entry.url
                ) {
                    dismiss()
                    onOpenBookmark(entry)
                }
            }
        }

        addAction(content, "Clear bookmarks") {
            onClearBookmarks()
            dismiss()
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
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.94).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.82).toInt()
            )
            setGravity(Gravity.CENTER)
        }
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
        action: () -> Unit
    ) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(16), d(13), d(16), d(13))
            background = rounded("#191C21", 16f)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            installPressAnimation(this)
        }

        card.addView(TextView(context).apply {
            text = title.replace("\n", " ").trim().ifBlank { url }.take(70)
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

    private fun addEmpty(parent: LinearLayout, message: String) {
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

    private fun rounded(color: String, radius: Float) =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = d(radius.toInt()).toFloat()
        }

    private fun d(value: Int): Int =
        (value * dp).toInt()
}
