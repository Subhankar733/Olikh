package com.subho.olikh

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class TabManagerDialog(
    private val browserTabs: List<BrowserTab>,
    private val activeIndex: Int,
    private val onSelectTab: (Int) -> Unit,
    private val onCloseTab: (Int) -> Unit,
    private val onDuplicateTab: (Int) -> Unit,
    private val onNewTab: () -> Unit,
    private val onCloseAll: () -> Unit,
    private val onCloseOthers: () -> Unit,
    private val onReopenClosed: () -> Unit
) : Dialog(browserTabs.firstOrNull()?.webView?.context ?: error("No tabs")) {

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int = 18, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }
    }

    private fun label(value: String, size: Float, color: Int): TextView {
        return TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
        }
    }

    private fun preview(tab: BrowserTab): Bitmap? = runCatching {
        val bitmap = Bitmap.createBitmap(
            dp(170).coerceAtLeast(150),
            dp(108).coerceAtLeast(90),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(12, 15, 21))
        tab.webView.draw(canvas)
        bitmap
    }.getOrNull()

    private fun action(textValue: String, callback: () -> Unit): Button {
        return Button(context).apply {
            text = textValue
            textSize = 10.5f
            isAllCaps = false
            setTextColor(Color.rgb(215, 222, 234))
            background = bg(Color.rgb(18, 23, 32), 14, Color.rgb(46, 56, 72))
            setOnClickListener {
                dismiss()
                callback()
            }
        }
    }

    private fun addCard(grid: GridLayout, index: Int, tab: BrowserTab) {
        val active = index == activeIndex

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = bg(
                if (active) Color.rgb(24, 28, 42) else Color.rgb(15, 19, 26),
                20,
                if (active) Color.rgb(112, 96, 255) else Color.rgb(43, 52, 67)
            )
            isClickable = true
            isFocusable = true

            setOnClickListener {
                dismiss()
                onSelectTab(index)
            }

            setOnLongClickListener {
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(
                        tab.title.replace("\n", " ").trim()
                            .ifBlank { "New Tab" }
                    )
                    .setItems(
                        arrayOf("Switch to tab", "Duplicate tab", "Close tab")
                    ) { dialog, which ->
                        dialog.dismiss()
                        dismiss()
                        when (which) {
                            0 -> onSelectTab(index)
                            1 -> onDuplicateTab(index)
                            2 -> onCloseTab(index)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate()
                        .scaleX(.985f).scaleY(.985f).alpha(.9f)
                        .setDuration(55).start()
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> v.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(90).start()
                }
                false
            }
        }

        val previewFrame = FrameLayout(context).apply {
            background = bg(Color.rgb(9, 12, 17), 15)
            clipToOutline = true
        }

        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(9, 12, 17))
        }
        preview(tab)?.let(image::setImageBitmap)

        previewFrame.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(108)
            )
        )

        if (active) {
            previewFrame.addView(
                label("ACTIVE", 8.5f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = bg(Color.rgb(102, 87, 238), 99)
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                },
                FrameLayout.LayoutParams(dp(58), dp(23)).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = dp(7)
                    topMargin = dp(7)
                }
            )
        }

        card.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(108)
            )
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), 0, 0)
        }

        val title = label(
            tab.title.replace("\n", " ").trim().ifBlank { "New Tab" },
            13f,
            Color.rgb(242, 245, 249)
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val url = label(
            tab.url.ifBlank { "about:blank" },
            9.5f,
            Color.rgb(130, 141, 157)
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(title)
        textBox.addView(url)

        info.addView(
            textBox,
            LinearLayout.LayoutParams(0, dp(43), 1f)
        )

        val close = label("×", 22f, Color.rgb(218, 224, 232)).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                dismiss()
                onCloseTab(index)
            }
        }
        info.addView(close, LinearLayout.LayoutParams(dp(40), dp(40)))

        card.addView(
            info,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        grid.addView(
            card,
            GridLayout.LayoutParams().apply {
                width = 0
                height = dp(175)
                columnSpec = GridLayout.spec(index % 2, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(.84f)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.rgb(7, 10, 15), 28, Color.rgb(35, 44, 59))
            setPadding(dp(16), dp(15), dp(16), dp(13))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBox.addView(label("Your tabs", 26f, Color.WHITE))
        titleBox.addView(
            label(
                "${browserTabs.size} open  •  ${browserTabs.count { it.incognito }} private",
                11f,
                Color.rgb(132, 143, 158)
            ).apply { setPadding(0, dp(3), 0, 0) }
        )

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        header.addView(
            Button(context).apply {
                text = "+"
                textSize = 23f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = bg(Color.rgb(103, 88, 239), 17)
                setOnClickListener {
                    dismiss()
                    onNewTab()
                }
            },
            LinearLayout.LayoutParams(dp(55), dp(49))
        )
        root.addView(header)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }

        listOf(
            action("Close others", onCloseOthers),
            action("Close all", onCloseAll),
            action("Reopen", onReopenClosed)
        ).forEachIndexed { i, button ->
            actions.addView(
                button,
                LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    when (i) {
                        0 -> marginEnd = dp(4)
                        1 -> {
                            marginStart = dp(4)
                            marginEnd = dp(4)
                        }
                        else -> marginStart = dp(4)
                    }
                }
            )
        }
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))

        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        browserTabs.forEachIndexed { index, tab ->
            addCard(grid, index, tab)
        }

        root.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(grid)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        root.addView(
            Button(context).apply {
                text = "Done"
                textSize = 13f
                isAllCaps = false
                setTextColor(Color.rgb(232, 236, 242))
                background = bg(Color.rgb(17, 22, 30), 16, Color.rgb(45, 55, 70))
                setOnClickListener { dismiss() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(49)
            ).apply { topMargin = dp(8) }
        )

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setGravity(Gravity.CENTER)
        }
    }

    override fun show() {
        super.show()
        window?.decorView?.apply {
            alpha = 0f
            scaleX = .985f
            scaleY = .985f
            translationY = dp(14).toFloat()
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180)
                .start()
        }
    }
}
