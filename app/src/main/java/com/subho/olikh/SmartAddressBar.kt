package com.subho.olikh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class SmartAddressBar(
    private val context: Context,
    private val editText: EditText,
    private val suggestionsProvider: (String) -> List<Suggestion>,
    private val onSuggestionSelected: (Suggestion) -> Unit
) {
    data class Suggestion(
        val title: String,
        val value: String,
        val kind: String
    )

    private var popup: PopupWindow? = null
    private var internalChange = false

    fun attach() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                if (internalChange || !editText.hasFocus()) return
                val query = s?.toString()?.trim().orEmpty()
                if (query.isBlank()) {
                    dismiss()
                } else {
                    showSuggestions(suggestionsProvider(query))
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        editText.setOnFocusChangeListener { _, focused ->
            if (!focused) dismiss()
            else editText.post { maybeShowClipboardSuggestion() }
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    private fun maybeShowClipboardSuggestion() {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return

        val text = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            .orEmpty()

        val useful = text.startsWith("http://", true) ||
            text.startsWith("https://", true) ||
            (!text.contains(" ") && text.contains("."))

        if (useful) {
            showSuggestions(
                listOf(Suggestion("Clipboard", text, "Paste & go"))
            )
        }
    }

    private fun showSuggestions(items: List<Suggestion>) {
        if (items.isEmpty()) {
            dismiss()
            return
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(24, 29, 37), 18)
        }

        items.take(8).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                setOnClickListener {
                    dismiss()
                    onSuggestionSelected(item)
                }
            }

            row.addView(TextView(context).apply {
                text = "${item.kind}  •  ${item.title}"
                textSize = 12f
                setTextColor(Color.rgb(174, 184, 198))
                maxLines = 1
            })

            row.addView(TextView(context).apply {
                text = item.value
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 1
                setPadding(0, dp(3), 0, 0)
            })

            root.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(60)
                )
            )
        }

        dismiss()

        popup = PopupWindow(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            elevation = dp(10).toFloat()
            setBackgroundDrawable(rounded(Color.TRANSPARENT, 18))
            showAsDropDown(editText, 0, dp(4))
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
}
