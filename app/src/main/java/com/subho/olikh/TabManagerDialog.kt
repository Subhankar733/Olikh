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
import kotlin.math.max

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

    private fun rounded(
        color: Int,
        radius: Int = 16,
        stroke: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }
    }

    private fun previewBitmap(tab: BrowserTab): Bitmap? {
        return runCatching {
            val width = dp(170).coerceAtLeast(150)
            val height = dp(105).coerceAtLeast(90)
            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(10, 13, 18))
            tab.webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun text(
        value: String,
        size: Float,
        color: Int
    ): TextView {
        return TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(color)
        }
    }

    private fun actionButton(
        label: String,
        callback: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setTextColor(Color.rgb(218, 224, 234))
            background = rounded(
                Color.rgb(22, 27, 36),
                14,
                Color.rgb(48, 57, 72)
            )
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                dismiss()
                callback()
            }
        }
    }

    private fun addTabCard(
        grid: GridLayout,
        index: Int,
        tab: BrowserTab,
        columnCount: Int
    ) {
        val active = index == activeIndex
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(
                if (active) Color.rgb(25, 30, 42)
                else Color.rgb(16, 20, 27),
                18,
                if (active) Color.rgb(116, 101, 255)
                else Color.rgb(40, 48, 61)
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
                        tab.title.replace("\n", " ")
                            .trim()
                            .ifBlank { "New Tab" }
                    )
                    .setItems(
                        arrayOf(
                            "Switch to tab",
                            "Duplicate tab",
                            "Close tab"
                        )
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

            installPressAnimation(this)
        }

        val previewFrame = FrameLayout(context).apply {
            background = rounded(Color.rgb(9, 12, 17), 13)
            clipToOutline = true
        }

        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(9, 12, 17))
        }

        previewBitmap(tab)?.let(preview::setImageBitmap)

        previewFrame.addView(
            preview,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(105)
            )
        )

        if (active) {
            val badge = text("ACTIVE", 8.5f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(101, 88, 235), 99)
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }
            previewFrame.addView(
                badge,
                FrameLayout.LayoutParams(
                    dp(55),
                    dp(23)
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = dp(7)
                    topMargin = dp(7)
                }
            )
        }

        card.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(105)
            )
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), 0, dp(1))
        }

        val titleBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleBox.addView(
            text(
                tab.title.replace("\n", " ")
                    .trim()
                    .ifBlank { "New Tab" },
                13f,
                Color.rgb(242, 245, 249)
            ).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )

        titleBox.addView(
            text(
                tab.url.ifBlank { "about:blank" },
                9.5f,
                Color.rgb(133, 143, 158)
            ).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            }
        )

        info.addView(
            titleBox,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        val close = TextView(context).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(214, 220, 229))
            background = rounded(Color.TRANSPARENT, 99)
            setOnClickListener {
                dismiss()
                onCloseTab(index)
            }
        }

        info.addView(
            close,
            LinearLayout.LayoutParams(dp(40), dp(40))
        )

        card.addView(
            info,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(170)
            columnSpec = GridLayout.spec(index % columnCount, 1f)
            rowSpec = GridLayout.spec(index / columnCount)
            setMargins(dp(5), dp(5), dp(5), dp(5))
        }

        grid.addView(card, params)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.78f)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(9, 12, 17), 24, Color.rgb(37, 45, 58))
            setPadding(dp(14), dp(14), dp(14), dp(12))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headingBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        headingBox.addView(
            text("Tabs", 25f, Color.WHITE)
        )

        headingBox.addView(
            text(
                "${browserTabs.size} open  •  tap to switch",
                11f,
                Color.rgb(132, 143, 158)
            ).apply {
                setPadding(0, dp(3), 0, 0)
            }
        )

        header.addView(
            headingBox,
            LinearLayout.LayoutParams(0, dp(56), 1f)
        )

        val newTab = Button(context).apply {
            text = "+"
            textSize = 23f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(101, 88, 235), 16)
            setOnClickListener {
                dismiss()
                onNewTab()
            }
        }

        header.addView(
            newTab,
            LinearLayout.LayoutParams(dp(54), dp(48))
        )

        root.addView(header)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }

        val closeOthers = actionButton("Close others", onCloseOthers)
        val closeAll = actionButton("Close all", onCloseAll)
        val reopen = actionButton("Reopen", onReopenClosed)

        actions.addView(
            closeOthers,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(4)
            }
        )
        actions.addView(
            closeAll,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        )
        actions.addView(
            reopen,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(4)
            }
        )

        root.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        val normalTabs = browserTabs.mapIndexed { index, tab ->
            index to tab
        }.filter { !it.second.incognito }

        val privateTabs = browserTabs.mapIndexed { index, tab ->
            index to tab
        }.filter { it.second.incognito }

        val combined = normalTabs + privateTabs

        combined.forEach { (index, tab) ->
            addTabCard(grid, index, tab, 2)
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(
                grid,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val done = Button(context).apply {
            text = "Done"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.rgb(232, 236, 242))
            background = rounded(
                Color.rgb(19, 24, 32),
                15,
                Color.rgb(45, 54, 68)
            )
            setOnClickListener { dismiss() }
        }

        root.addView(
            done,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(8)
            }
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
            scaleX = 0.98f
            scaleY = 0.98f
            translationY = dp(18).toFloat()
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .start()
        }
    }

    private fun installPressAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.985f)
                        .scaleY(0.985f)
                        .alpha(0.9f)
                        .setDuration(60L)
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(100L)
                        .start()
                }
            }
            false
        }
    }
}
