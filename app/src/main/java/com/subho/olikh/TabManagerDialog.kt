package com.subho.olikh

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

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
    private var searchQuery = ""

    private val bg = Color.parseColor("#070A0E")
    private val line = Color.parseColor("#202832")
    private val primary = Color.parseColor("#F3F6FA")
    private val secondary = Color.parseColor("#7F8B99")
    private val accent = Color.parseColor("#67E8F9")

    private lateinit var list: LinearLayout
    private lateinit var filters: LinearLayout
    private lateinit var countText: TextView

    private fun dp(v: Int) =
        (v * context.resources.displayMetrics.density).toInt()

    private fun box(color: Int, radius: Int = 0, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            if (radius > 0) cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun txt(
        value: String,
        size: Float,
        color: Int = primary,
        bold: Boolean = false
    ) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun tabUrl(tab: BrowserTab): String =
        tab.webView.url?.trim()?.takeIf { it.isNotBlank() } ?: tab.url.trim()

    private fun domain(value: String): String =
        value.removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .ifBlank { "about:blank" }

    private fun groupName(tab: BrowserTab): String {
        if (tab.incognito) return "PRIVATE"
        val id = groupStore.groupFor(tabUrl(tab)) ?: return "UNGROUPED"
        return groupStore.getGroups()
            .firstOrNull { it.id == id }
            ?.name?.uppercase() ?: "UNGROUPED"
    }

    private fun icon(tab: BrowserTab) = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        val bmp = runCatching {
            tab.favicon?.let {
                Bitmap.createScaledBitmap(it, dp(34), dp(34), true)
            }
        }.getOrNull()

        if (bmp != null) {
            setImageBitmap(bmp)
            background = box(Color.WHITE, 10)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        } else {
            background = box(Color.parseColor("#151C24"), 10, line)
            setImageResource(R.drawable.ic_search)
            setColorFilter(Color.parseColor("#8A98A8"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun menu(index: Int, tab: BrowserTab) =
        txt("⋯", 22f, secondary).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(
                        tab.title.replace("\n", " ")
                            .trim().ifBlank { "Tab" }
                    )
                    .setItems(
                        arrayOf("Switch to tab", "Duplicate tab", "Close tab")
                    ) { dialog, which ->
                        dialog.dismiss()
                        when (which) {
                            0 -> { dismiss(); onSelectTab(index) }
                            1 -> { dismiss(); onDuplicateTab(index) }
                            2 -> { dismiss(); onCloseTab(index) }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

    private fun row(index: Int, tab: BrowserTab): View {
        val active = index == activeIndex

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(12))
            background = if (active)
                box(Color.parseColor("#0D171B"))
            else box(Color.TRANSPARENT)

            setOnClickListener {
                dismiss()
                onSelectTab(index)
            }
        }

        root.addView(
            View(context).apply {
                background = box(if (active) accent else Color.TRANSPARENT, 2)
            },
            LinearLayout.LayoutParams(dp(3), dp(42)).apply {
                marginEnd = dp(10)
            }
        )

        root.addView(
            txt(String.format("%02d", index + 1), 10f,
                if (active) accent else secondary, true).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(28), dp(38)).apply {
                marginEnd = dp(10)
            }
        )

        root.addView(
            icon(tab),
            LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginEnd = dp(12)
            }
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        info.addView(
            txt(
                tab.title.replace("\n", " ").trim()
                    .ifBlank { "New Tab" }.take(60),
                14f, primary, active
            ).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )

        info.addView(
            txt(domain(tabUrl(tab)), 10f, secondary).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding(0, dp(4), 0, 0)
            }
        )

        info.addView(
            txt(groupName(tab), 8f,
                if (tab.incognito) Color.parseColor("#FBBF24")
                else Color.parseColor("#7F8B99"), true).apply {
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = box(
                    if (tab.incognito) Color.parseColor("#241B09")
                    else Color.parseColor("#121920"), 5
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        )

        root.addView(info)
        root.addView(menu(index, tab),
            LinearLayout.LayoutParams(dp(42), dp(42)))

        return root
    }

    private fun addFilter(title: String, key: String?) {
        val selected = selectedFilter == key
        val item = txt(title, 10f,
            if (selected) Color.BLACK else secondary, selected).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = box(
                if (selected) accent else Color.parseColor("#111820"),
                14,
                if (selected) accent else line
            )
            setOnClickListener {
                selectedFilter = key
                rebuildFilters()
                render()
            }
        }

        filters.addView(item,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)
            ).apply { marginEnd = dp(6) })
    }

    private fun rebuildFilters() {
        filters.removeAllViews()
        addFilter("ALL ${browserTabs.size}", null)

        groupStore.getGroups().forEach { group ->
            val count = browserTabs.count {
                !it.incognito && groupStore.groupFor(tabUrl(it)) == group.id
            }
            if (count > 0) addFilter("${group.name.uppercase()} $count", group.id)
        }

        val privateCount = browserTabs.count { it.incognito }
        if (privateCount > 0) addFilter("PRIVATE $privateCount", "__private__")

        val ungrouped = browserTabs.count {
            !it.incognito && groupStore.groupFor(tabUrl(it)) == null
        }
        if (ungrouped > 0) addFilter("UNGROUPED $ungrouped", "__ungrouped__")
    }

    private fun render() {
        list.removeAllViews()
        var shown = 0

        browserTabs.forEachIndexed { index, tab ->
            val groupId = if (tab.incognito) "__private__"
            else groupStore.groupFor(tabUrl(tab)) ?: "__ungrouped__"

            val filterMatch =
                selectedFilter == null || selectedFilter == groupId

            val q = searchQuery.trim()
            val searchMatch =
                q.isBlank() ||
                tab.title.contains(q, true) ||
                tabUrl(tab).contains(q, true)

            if (!filterMatch || !searchMatch) return@forEachIndexed

            if (shown > 0) {
                list.addView(
                    View(context).apply { background = box(line) },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    )
                )
            }

            list.addView(row(index, tab))
            shown++
        }

        countText.text =
            if (shown == browserTabs.size) "$shown OPEN"
            else "$shown / ${browserTabs.size} SHOWN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
            background = box(bg)
        }

        root.addView(
            View(context).apply { background = box(Color.parseColor("#303B47"), 2) },
            LinearLayout.LayoutParams(dp(38), dp(3)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(17)
            }
        )

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        titleBox.addView(txt("TAB SPACE", 22f, primary, true))

        countText = txt("${browserTabs.size} OPEN", 9f, secondary, true)
        titleBox.addView(countText)
        header.addView(titleBox)

        val plus = txt("+", 28f, primary).apply {
            gravity = Gravity.CENTER
            background = box(Color.parseColor("#111820"), 14, line)
            setOnClickListener { dismiss(); onNewTab() }
        }

        header.addView(plus, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val search = EditText(context).apply {
            hint = "Search tabs"
            textSize = 12f
            setSingleLine(true)
            setTextColor(primary)
            setHintTextColor(Color.parseColor("#596674"))
            background = box(Color.parseColor("#0C1117"), 10, line)
            setPadding(dp(12), 0, dp(12), 0)
        }

        root.addView(search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(8)
            })

        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        filters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        filterScroll.addView(filters)
        root.addView(filterScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)
            ))

        val section = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(3))
        }

        section.addView(txt("OPEN TABS", 9f, secondary, true))
        section.addView(
            View(context).apply { background = box(line) },
            LinearLayout.LayoutParams(0, dp(1), 1f).apply {
                marginStart = dp(10)
            }
        )
        root.addView(section)

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)
        root.addView(scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))

        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        fun command(label: String, callback: () -> Unit) =
            txt(label, 9f, secondary, true).apply {
                gravity = Gravity.CENTER
                background = box(Color.parseColor("#0C1117"), 9, line)
                setOnClickListener { dismiss(); callback() }
            }

        listOf(
            "REOPEN" to onReopenClosed,
            "GROUPS" to onManageGroups,
            "OTHERS" to onCloseOthers,
            "ALL" to onCloseAll
        ).forEach { (label, callback) ->
            bottom.addView(
                command(label, callback),
                LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    marginEnd = dp(5)
                }
            )
        }

        root.addView(bottom)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                searchQuery = s?.toString().orEmpty()
                render()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        setContentView(root)

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.96f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.86f).toInt()
        )

        rebuildFilters()
        render()
    }
}
