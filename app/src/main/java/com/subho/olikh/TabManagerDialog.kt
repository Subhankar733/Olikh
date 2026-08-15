package com.subho.olikh

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
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

    private val groupStore by lazy { TabGroupStore(context) }
    private var selectedFilter: String? = null
    private var searchQuery: String = ""

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun panel(color: Int, radius: Int = 16, strokeColor: Int? = null, strokeWidth: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            strokeColor?.let { setStroke(dp(strokeWidth), it) }
        }

    private fun text(value: String, size: Float, color: Int, isBold: Boolean = false) =
        TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (isBold) setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun action(value: String, callback: () -> Unit): Button =
        Button(context).apply {
            text = value
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            background = panel(Color.parseColor("#1E293B"), 12, Color.parseColor("#334155"))
            setOnClickListener {
                dismiss()
                callback()
            }
        }

    private fun preview(tab: BrowserTab): Bitmap? = runCatching {
        tab.favicon?.let { favicon ->
            Bitmap.createScaledBitmap(favicon, dp(32), dp(32), true)
        }
    }.getOrNull()

    private fun tabUrl(tab: BrowserTab): String =
        tab.webView.url?.trim()?.takeIf { it.isNotBlank() } ?: tab.url.trim()

    private fun groupNameFor(tab: BrowserTab): String {
        if (tab.incognito) return "Private"
        val groupId = groupStore.groupFor(tabUrl(tab)) ?: return "Ungrouped"
        return groupStore.getGroups().firstOrNull { it.id == groupId }?.name ?: "Ungrouped"
    }

    private fun addCard(
        grid: GridLayout,
        index: Int,
        tab: BrowserTab,
        groupName: String,
        gridIndex: Int
    ) {
        val isActive = index == activeIndex
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = panel(
                if (isActive) Color.parseColor("#1E293B") else Color.parseColor("#111827"),
                16,
                if (isActive) Color.parseColor("#3B82F6") else Color.parseColor("#1F2937"),
                if (isActive) 2 else 1
            )
            setOnClickListener {
                dismiss()
                onSelectTab(index)
            }
        }

        // Header inside card (Favicon + Title + Close Button)
        val cardHeader = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            val bmp = preview(tab)
            if (bmp != null) {
                setImageBitmap(bmp)
            } else {
                setImageResource(R.drawable.ic_search)
                setColorFilter(Color.parseColor("#94A3B8"))
            }
        }
        cardHeader.addView(icon)

        val titleView = text(tab.title.ifBlank { "New Tab" }.take(22), 12f, Color.parseColor("#F8FAFC"), true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setSingleLine(true)
        }
        cardHeader.addView(titleView)

        val closeBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#94A3B8"))
            background = panel(Color.parseColor("#1E293B"), 8)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = "Close tab"
            setOnClickListener {
                dismiss()
                onCloseTab(index)
            }
        }
        cardHeader.addView(closeBtn)
        card.addView(cardHeader)

        // URL display
        val urlView = text(tabUrl(tab).ifBlank { "about:blank" }.take(36), 10f, Color.parseColor("#64748B")).apply {
            setSingleLine(true)
            setPadding(0, dp(4), 0, dp(6))
        }
        card.addView(urlView)

        // Bottom footer inside card (Badge + Duplicate)
        val cardFooter = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val badge = TextView(context).apply {
            text = groupName
            textSize = 9f
            setTextColor(if (tab.incognito) Color.parseColor("#FBBF24") else Color.parseColor("#60A5FA"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = panel(if (tab.incognito) Color.parseColor("#451A03") else Color.parseColor("#1E3A5F"), 6)
        }
        cardFooter.addView(badge)

        val spacer = android.widget.Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        cardFooter.addView(spacer)

        val dupBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(26))
            text = "Copy"
            isAllCaps = false
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            background = panel(Color.parseColor("#0F172A"), 6, Color.parseColor("#334155"))
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                dismiss()
                onDuplicateTab(index)
            }
        }
        cardFooter.addView(dupBtn)
        card.addView(cardFooter)

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(gridIndex % 2, 1f)
            rowSpec = GridLayout.spec(gridIndex / 2)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        grid.addView(card, params)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = panel(Color.parseColor("#0F172A"), 24, Color.parseColor("#1E293B"))
        }

        // Header: Title + Active tabs count + Add Tab button
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val titleBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(text("Tabs", 20f, Color.parseColor("#F8FAFC"), true))
        titleBox.addView(text("${browserTabs.size} open tabs", 11f, Color.parseColor("#94A3B8")))
        header.addView(titleBox)

        val plus = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(Color.WHITE)
            background = panel(Color.parseColor("#2563EB"), 12)
            contentDescription = "New tab"
            setOnClickListener {
                dismiss()
                onNewTab()
            }
        }
        header.addView(plus)
        root.addView(header)

        // Search Bar
        val search = EditText(context).apply {
            hint = "Search open tabs..."
            setSingleLine(true)
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = panel(Color.parseColor("#1E293B"), 12, Color.parseColor("#334155"))
            setPadding(dp(12), 0, dp(12), 0)
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(12)
                bottomMargin = dp(6)
            }
        )

        // Filter Pills Scroll
        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
        }
        val filterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        filterScroll.addView(filterRow)
        root.addView(filterScroll)

        // Scrollable Grid for Tab Cards
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
        }
        val grid = GridLayout(context).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = false
        }
        scroll.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        )

        // Quick Bottom Action Buttons
        val actions = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        listOf(
            "Close Others" to onCloseOthers,
            "Close All" to onCloseAll,
            "Reopen" to onReopenClosed,
            "Groups" to onManageGroups
        ).forEach { (label, callback) ->
            val btn = action(label, callback).apply {
                textSize = 11f
            }
            actions.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            )
        }
        root.addView(actions)

        fun render() {
            grid.removeAllViews()
            var count = 0
            browserTabs.forEachIndexed { index, tab ->
                val gName = groupNameFor(tab)
                val matchesFilter = when (selectedFilter) {
                    null -> true
                    "__private__" -> tab.incognito
                    "__ungrouped__" -> !tab.incognito && groupStore.groupFor(tabUrl(tab)) == null
                    else -> !tab.incognito && groupStore.groupFor(tabUrl(tab)) == selectedFilter
                }
                val matchesSearch = searchQuery.isBlank() ||
                        tab.title.contains(searchQuery, ignoreCase = true) ||
                        tabUrl(tab).contains(searchQuery, ignoreCase = true)

                if (matchesFilter && matchesSearch) {
                    addCard(grid, index, tab, gName, count)
                    count++
                }
            }
        }

        fun updateFilters() {
            filterRow.removeAllViews()
            fun addPill(label: String, key: String?) {
                val isSelected = selectedFilter == key
                val pill = TextView(context).apply {
                    text = label
                    textSize = 11f
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                    background = panel(
                        if (isSelected) Color.parseColor("#2563EB") else Color.parseColor("#1E293B"),
                        16,
                        if (isSelected) Color.parseColor("#3B82F6") else Color.parseColor("#334155")
                    )
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        selectedFilter = key
                        updateFilters()
                        render()
                    }
                }
                filterRow.addView(
                    pill,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(dp(2), 0, dp(4), 0)
                    }
                )
            }

            addPill("All (${browserTabs.size})", null)
            groupStore.getGroups().forEach { group ->
                val c = browserTabs.count { !it.incognito && groupStore.groupFor(tabUrl(it)) == group.id }
                if (c > 0) addPill("${group.name} ($c)", group.id)
            }
            val pCount = browserTabs.count { it.incognito }
            if (pCount > 0) addPill("Private ($pCount)", "__private__")
            val uCount = browserTabs.count { !it.incognito && groupStore.groupFor(tabUrl(it)) == null }
            if (uCount > 0) addPill("Ungrouped ($uCount)", "__ungrouped__")
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                render()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setContentView(root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.82f).toInt()
        )

        updateFilters()
        render()
    }
}
