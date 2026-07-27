package com.subho.olikh

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class TabManagerDialog(
    private val browserTabs: List<BrowserTab>,
    private val activeIndex: Int,
    private val onSelectTab: (Int) -> Unit,
    private val onCloseTab: (Int) -> Unit,
    private val onNewTab: () -> Unit
) : Dialog(browserTabs.firstOrNull()?.webView?.context ?: error("No tabs")) {

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0f)
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 13, 16))
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(context).apply {
            text = "OLIKH Tabs"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            heading,
            LinearLayout.LayoutParams(
                0,
                dp(56),
                1f
            )
        )

        val newTab = Button(context).apply {
            text = "+"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(26, 29, 34))

            setOnClickListener {
                dismiss()
                onNewTab()
            }
        }

        header.addView(
            newTab,
            LinearLayout.LayoutParams(
                dp(56),
                dp(48)
            )
        )

        root.addView(header)

        val count = TextView(context).apply {
            text = "${browserTabs.size} ${if (browserTabs.size == 1) "tab" else "tabs"}"
            textSize = 14f
            setTextColor(Color.rgb(150, 156, 166))
            setPadding(0, 0, 0, dp(14))
        }

        root.addView(count)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        browserTabs.forEachIndexed { index, tab ->

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                val background =
                    if (index == activeIndex)
                        Color.rgb(38, 43, 52)
                    else
                        Color.rgb(24, 27, 32)

                setBackgroundColor(background)
                setPadding(dp(18), dp(12), dp(8), dp(12))

                isClickable = true
                isFocusable = true

                setOnClickListener {
                    dismiss()
                    onSelectTab(index)
                }
            }

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val title = TextView(context).apply {
                text = tab.title
                    .replace("\n", " ")
                    .trim()
                    .ifBlank { "New Tab" }

                textSize = 17f
                setTextColor(Color.WHITE)
                maxLines = 1
            }

            val url = TextView(context).apply {
                text = tab.url.ifBlank { "New Tab" }
                textSize = 12f
                setTextColor(Color.rgb(150, 156, 166))
                maxLines = 1
                setPadding(0, dp(4), 0, 0)
            }

            info.addView(title)
            info.addView(url)

            card.addView(
                info,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val close = Button(context).apply {
                text = "×"
                textSize = 22f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)

                setOnClickListener {
                    dismiss()
                    onCloseTab(index)
                }
            }

            card.addView(
                close,
                LinearLayout.LayoutParams(
                    dp(52),
                    dp(52)
                )
            )

            list.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }

        val scroll = ScrollView(context).apply {
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
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(26, 29, 34))

            setOnClickListener {
                dismiss()
            }
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
}
