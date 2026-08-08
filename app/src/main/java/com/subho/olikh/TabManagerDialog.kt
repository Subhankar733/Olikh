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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.FrameLayout
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

    private fun roundedBackground(
        color: Int,
        radius: Int = 18,
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
            val width = dp(250).coerceAtLeast(180)
            val height = dp(135).coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(18, 22, 29))
            tab.webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun addSectionTitle(
        root: LinearLayout,
        text: String,
        count: Int,
        incognito: Boolean = false
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(16), dp(2), dp(8))
        }

        val title = TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(
                if (incognito) Color.rgb(198, 170, 255)
                else Color.rgb(210, 216, 226)
            )
        }

        val badge = TextView(context).apply {
            this.text = count.toString()
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(
                if (incognito) Color.rgb(73, 51, 105)
                else Color.rgb(39, 48, 62),
                99
            )
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }

        row.addView(
            title,
            LinearLayout.LayoutParams(0, dp(34), 1f)
        )
        row.addView(badge)

        root.addView(row)
    }

    private fun addTabCard(
        root: LinearLayout,
        index: Int,
        tab: BrowserTab
    ) {
        val active = index == activeIndex

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                if (active) Color.rgb(31, 39, 52)
                else Color.rgb(20, 24, 31),
                20,
                if (active) Color.rgb(99, 89, 220) else Color.rgb(42, 49, 61)
            )
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))

            setOnClickListener {
                dismiss()
                onSelectTab(index)
            }

            setOnLongClickListener {
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(
                        tab.title.replace("\n", " ")
                            .trim()
                            .ifBlank { "Tab" }
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
            background = roundedBackground(Color.rgb(12, 15, 20), 15)
            clipToOutline = true
        }

        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(12, 15, 20))
        }

        previewBitmap(tab)?.let {
            preview.setImageBitmap(it)
        }

        previewFrame.addView(
            preview,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(150)
            )
        )

        val overlay = TextView(context).apply {
            text = if (active) "ACTIVE" else ""
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(99, 89, 220), 99)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }

        if (active) {
            previewFrame.addView(
                overlay,
                FrameLayout.LayoutParams(
                    dp(62),
                    dp(26)
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = dp(8)
                    leftMargin = dp(8)
                }
            )
        }

        card.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(150)
            )
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), 0, dp(2))
        }

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(context).apply {
            text = tab.title.replace("\n", " ")
                .trim()
                .ifBlank { "New Tab" }
            textSize = 15f
            setTextColor(Color.WHITE)
            maxLines = 1
        }

        val url = TextView(context).apply {
            text = tab.url.ifBlank { "New Tab" }
            textSize = 10f
            setTextColor(Color.rgb(145, 154, 168))
            maxLines = 1
            setPadding(0, dp(3), 0, 0)
        }

        textBox.addView(title)
        textBox.addView(url)

        info.addView(
            textBox,
            LinearLayout.LayoutParams(0, dp(50), 1f)
        )

        val close = Button(context).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.TRANSPARENT, 99)
            setOnClickListener {
                dismiss()
                onCloseTab(index)
            }
        }

        info.addView(
            close,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        card.addView(
            info,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.72f)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.rgb(8, 11, 16), 0)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headingBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val heading = TextView(context).apply {
            text = "OLIKH Tabs"
            textSize = 25f
            setTextColor(Color.WHITE)
        }

        val subtitle = TextView(context).apply {
            text = "${browserTabs.size} tabs  •  swipe-free preview"
            textSize = 11f
            setTextColor(Color.rgb(135, 145, 160))
            setPadding(0, dp(3), 0, 0)
        }

        headingBox.addView(heading)
        headingBox.addView(subtitle)

        header.addView(
            headingBox,
            LinearLayout.LayoutParams(0, dp(60), 1f)
        )

        val newTab = Button(context).apply {
            text = "+"
            textSize = 25f
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(78, 66, 175), 16)
            setOnClickListener {
                dismiss()
                onNewTab()
            }
        }

        header.addView(
            newTab,
            LinearLayout.LayoutParams(dp(58), dp(52))
        )

        root.addView(header)

        val actionsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }

        fun actionButton(label: String, callback: () -> Unit): Button {
            return Button(context).apply {
                text = label
                textSize = 10f
                setTextColor(Color.rgb(224, 229, 237))
                background = roundedBackground(
                    Color.rgb(22, 27, 35),
                    99,
                    Color.rgb(45, 54, 67)
                )
                setOnClickListener {
                    dismiss()
                    callback()
                }
                actions.addView(
                    this,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(42)
                    ).apply {
                        rightMargin = dp(7)
                    }
                )
            }
        }

        actionButton("Close others", onCloseOthers)
        actionButton("Close all", onCloseAll)
        actionButton("Reopen closed", onReopenClosed)

        actionsScroll.addView(actions)
        root.addView(
            actionsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(20))
        }

        val normalTabs = browserTabs.mapIndexed { index, tab ->
            index to tab
        }.filter { !it.second.incognito }

        val privateTabs = browserTabs.mapIndexed { index, tab ->
            index to tab
        }.filter { it.second.incognito }

        if (normalTabs.isNotEmpty()) {
            addSectionTitle(
                list,
                "Normal tabs",
                normalTabs.size
            )
            normalTabs.forEach { (index, tab) ->
                addTabCard(list, index, tab)
            }
        }

        if (privateTabs.isNotEmpty()) {
            addSectionTitle(
                list,
                "Private / Incognito",
                privateTabs.size,
                true
            )
            privateTabs.forEach { (index, tab) ->
                addTabCard(list, index, tab)
            }
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(
                list,
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
            text = "DONE"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = roundedBackground(
                Color.rgb(25, 30, 39),
                16,
                Color.rgb(46, 55, 68)
            )
            setOnClickListener { dismiss() }
        }

        root.addView(
            done,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
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
            scaleX = 0.97f
            scaleY = 0.97f
            translationY = dp(24).toFloat()
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(220L)
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
                        .alpha(0.88f)
                        .setDuration(70L)
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(120L)
                        .start()
                }
            }
            false
        }
    }
}
