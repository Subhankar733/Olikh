package com.subho.olikh

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
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
    private val onReopenClosed: () -> Unit,
    private val onManageGroups: () -> Unit
) : Dialog(browserTabs.firstOrNull()?.webView?.context ?: error("No tabs")) {

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun panel(color: Int, radius: Int = 20, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun text(value: String, size: Float, color: Int) =
        TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(color)
        }

    private fun action(value: String, callback: () -> Unit): Button =
        Button(context).apply {
            text = value
            isAllCaps = false
            textSize = 11f
            setTextColor(Color.rgb(225, 230, 238))
            background = panel(Color.rgb(23, 27, 35), 14, Color.rgb(47, 55, 70))
            setOnClickListener {
                dismiss()
                callback()
            }
        }

    private fun preview(tab: BrowserTab): Bitmap? = runCatching {
        tab.favicon?.let { favicon ->
            Bitmap.createScaledBitmap(
                favicon,
                dp(48),
                dp(48),
                true
            )
        }
    }.getOrNull()

    private fun addCard(grid: GridLayout, index: Int, tab: BrowserTab) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            background = panel(
                if (index == activeIndex) Color.rgb(25, 31, 45) else Color.rgb(17, 21, 28),
                20,
                if (index == activeIndex) Color.rgb(102, 116, 238) else Color.rgb(42, 49, 62)
            )
            setOnClickListener {
                dismiss()
                onSelectTab(index)
            }
        }

        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(96)
            )
            scaleType = ImageView.ScaleType.CENTER
            setImageBitmap(preview(tab))
            background = panel(Color.rgb(10, 13, 18), 14)
        }
        card.addView(image)

        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, 0)
        }

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        info.addView(text(
            tab.title.ifBlank { "New tab" }.take(28),
            14f,
            Color.WHITE
        ))
        val currentUrl = tab.webView.url
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: tab.url

        info.addView(
            text(
                currentUrl.ifBlank { "about:blank" }.take(42),
                10f,
                Color.rgb(145, 153, 168)
            )
        )

        row.addView(info)

        val close = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.rgb(205, 212, 222))
            background = panel(Color.rgb(29, 34, 43), 12)
            contentDescription = "Close tab"
            setOnClickListener {
                dismiss()
                onCloseTab(index)
            }
        }
        row.addView(close)

        card.addView(row)

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(index % 2, 1f)
            rowSpec = GridLayout.spec(index / 2)
            setMargins(dp(5), dp(5), dp(5), dp(5))
        }
        grid.addView(card, params)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            background = panel(Color.rgb(9, 12, 17), 26, Color.rgb(38, 45, 58))
        }

        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(text("Open spaces", 26f, Color.WHITE))
        titleBox.addView(text(
            "${browserTabs.size} active • tap a card to enter",
            11f,
            Color.rgb(137, 146, 162)
        ))
        header.addView(titleBox)

        val plus = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(Color.WHITE)
            background = panel(Color.rgb(89, 103, 232), 17)
            contentDescription = "New tab"
            setOnClickListener {
                dismiss()
                onNewTab()
            }
        }
        header.addView(plus)
        root.addView(header)

        val actions = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        listOf(
            "Close others" to onCloseOthers,
            "Close all" to onCloseAll,
            "Reopen" to onReopenClosed,
            "Groups" to onManageGroups
        ).forEach { (label, callback) ->
            actions.addView(
                action(label, callback),
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }
        root.addView(actions)

        val scroll = ScrollView(context)
        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
        }

        browserTabs.forEachIndexed { index, tab ->
            addCard(grid, index, tab)
        }

        scroll.addView(grid)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val done = action("Done") { }
        root.addView(
            done,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                setMargins(0, dp(10), 0, 0)
            }
        )

        setContentView(root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.96f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.88f).toInt()
        )
    }
}
