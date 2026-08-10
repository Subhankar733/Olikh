package com.subho.olikh

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class TabGroupDialog(
    private val browserTabs: List<BrowserTab>,
    private val activeIndex: Int,
    private val store: TabGroupStore,
    private val onSelectTab: (Int) -> Unit,
    private val onChanged: () -> Unit
) : Dialog(browserTabs.firstOrNull()?.webView?.context ?: error("No tabs")) {

    private fun dp(v: Int) =
        (v * context.resources.displayMetrics.density).toInt()

    private fun button(label: String, action: () -> Unit) =
        Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun currentTab(): BrowserTab? =
        browserTabs.getOrNull(activeIndex)

    private fun currentUrl(): String? =
        currentTab()?.webView?.url ?: currentTab()?.url

    private fun isPrivateTab(): Boolean =
        currentTab()?.incognito == true

    private fun currentGroupName(): String {
        if (isPrivateTab()) return "Private tab"
        val id = store.groupFor(currentUrl()) ?: return "Not in a group"
        return store.getGroups().firstOrNull { it.id == id }?.name
            ?: "Not in a group"
    }

    private fun blockPrivateGroups(): Boolean {
        if (!isPrivateTab()) return false
        AlertDialog.Builder(context)
            .setTitle("Private tab")
            .setMessage("Private tabs cannot be added to tab groups.")
            .setPositiveButton("OK", null)
            .show()
        return true
    }

    private fun tabTitle(tab: BrowserTab, index: Int): String {
        val title = tab.title.trim()
        if (title.isNotBlank()) return title.take(80)
        val url = (tab.webView.url ?: tab.url).trim()
        if (url.isNotBlank()) return url.take(80)
        return "Tab ${index + 1}"
    }

    private fun tabUrl(tab: BrowserTab): String =
        (tab.webView.url ?: tab.url).trim()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(14, 17, 23))
        }

        store.cleanupDanglingMemberships()

        root.addView(TextView(context).apply {
            text = "Tab Groups"
            textSize = 24f
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(context).apply {
            text = "Current: ${currentGroupName()}"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(6), 0, dp(8))
        })

        val search = EditText(context).apply {
            hint = "Search tabs or groups"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        searchRow.addView(
            search,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        searchRow.addView(
            button("Clear") {
                search.setText("")
                search.requestFocus()
            },
            LinearLayout.LayoutParams(
                dp(76),
                dp(44)
            ).apply {
                setMargins(dp(6), 0, 0, 0)
            }
        )

        root.addView(searchRow)

        val resultCount = TextView(context).apply {
            text = "0 groups • 0 tabs"
            textSize = 10f
            setTextColor(Color.rgb(150, 158, 172))
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(resultCount)

        root.addView(button("Create group") {
            if (blockPrivateGroups()) return@button
            val input = EditText(context).apply {
                hint = "Group name"
                setSingleLine(true)
            }
            AlertDialog.Builder(context)
                .setTitle("Create tab group")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create") { _, _ ->
                    val group = store.create(input.text.toString())
                    if (group != null) {
                        store.assign(currentUrl(), group.id)
                        onChanged()
                        dismiss()
                    }
                }
                .show()
        })

        root.addView(button("Add current tab to group") {
            if (blockPrivateGroups()) return@button
            val groups = store.getGroups()
            if (groups.isEmpty()) {
                AlertDialog.Builder(context)
                    .setMessage("Create a group first.")
                    .setPositiveButton("OK", null)
                    .show()
                return@button
            }
            AlertDialog.Builder(context)
                .setTitle("Choose group")
                .setItems(groups.map { it.name }.toTypedArray()) { _, which ->
                    store.assign(currentUrl(), groups[which].id)
                    onChanged()
                    dismiss()
                }
                .show()
        })

        root.addView(button("Remove current tab from group") {
            if (blockPrivateGroups()) return@button
            store.remove(currentUrl())
            onChanged()
            dismiss()
        })

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(list)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        fun render(filterRaw: String) {
            val filter = filterRaw.trim().lowercase()
            list.removeAllViews()

            val groups = store.getGroups()
                .sortedBy { it.name.lowercase() }
            val groupById = groups.associateBy { it.id }

            list.addView(TextView(context).apply {
                text = "Groups"
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, dp(10), 0, dp(8))
            })

            var shownGroups = 0
            groups.forEach { group ->
                val count = browserTabs.count { tab ->
                    !tab.incognito &&
                        store.groupFor(tab.webView.url ?: tab.url) == group.id
                }
                if (filter.isNotBlank() &&
                    !group.name.lowercase().contains(filter) &&
                    count == 0
                ) return@forEach

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                row.addView(TextView(context).apply {
                    text = "${group.name}  ($count)"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })

                row.addView(button("Open") {
                    val index = browserTabs.indexOfFirst { tab ->
                        !tab.incognito &&
                            store.groupFor(tab.webView.url ?: tab.url) == group.id
                    }
                    if (index >= 0) {
                        dismiss()
                        onSelectTab(index)
                    }
                })

                row.addView(button("Rename") {
                    val input = EditText(context).apply {
                        setText(group.name)
                        setSingleLine(true)
                    }
                    AlertDialog.Builder(context)
                        .setTitle("Rename group")
                        .setView(input)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save") { _, _ ->
                            if (store.rename(group.id, input.text.toString())) {
                                onChanged()
                                dismiss()
                            }
                        }
                        .show()
                })

                row.addView(button("Delete") {
                    AlertDialog.Builder(context)
                        .setTitle("Delete group?")
                        .setMessage("Tabs stay open; only the group is removed.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            store.delete(group.id)
                            onChanged()
                            dismiss()
                        }
                        .show()
                })

                list.addView(row)
                shownGroups++
            }

            if (shownGroups == 0) {
                list.addView(TextView(context).apply {
                    text = if (filter.isBlank()) "No groups yet." else "No matching groups."
                    textSize = 13f
                    setTextColor(Color.LTGRAY)
                    setPadding(0, dp(8), 0, dp(12))
                })
            }

            list.addView(TextView(context).apply {
                text = "Tabs"
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, dp(18), 0, dp(8))
            })

            var shownTabs = 0

            val orderedTabs = browserTabs
                .mapIndexed { index, tab -> index to tab }
                .sortedWith(
                    compareByDescending<Pair<Int, BrowserTab>> {
                        it.second === currentTab()
                    }.thenByDescending {
                        it.second.lastAccessed
                    }
                )

            orderedTabs.forEach { (index, tab) ->
                val title = tabTitle(tab, index)
                val url = tabUrl(tab)
                val groupName = if (tab.incognito) {
                    "Private tab"
                } else {
                    groupById[store.groupFor(url)]?.name ?: "Ungrouped"
                }

                val haystack = "$title $url $groupName".lowercase()
                if (filter.isNotBlank() && !haystack.contains(filter)) return@forEach

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setBackgroundColor(
                        if (index == activeIndex) Color.rgb(35, 42, 52)
                        else Color.rgb(20, 24, 31)
                    )
                    setOnClickListener {
                        dismiss()
                        onSelectTab(index)
                    }
                }

                row.addView(TextView(context).apply {
                    text = if (index == activeIndex) "● $title" else title
                    textSize = 14f
                    setTextColor(Color.WHITE)
                })

                row.addView(TextView(context).apply {
                    text = "$groupName${if (url.isBlank()) "" else "  •  ${url.take(120)}"}"
                    textSize = 11f
                    setTextColor(Color.LTGRAY)
                    setPadding(0, dp(3), 0, 0)
                })

                list.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(6)
                    }
                )
                shownTabs++
            }

            resultCount.text = "${shownGroups} group${if (shownGroups == 1) "" else "s"} • ${shownTabs} tab${if (shownTabs == 1) "" else "s"}"

            if (shownTabs == 0) {
                list.addView(TextView(context).apply {
                    text = if (filter.isBlank()) "No tabs." else "No matching tabs."
                    textSize = 13f
                    setTextColor(Color.LTGRAY)
                    setPadding(0, dp(8), 0, dp(12))
                })
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                render(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        root.addView(button("Close") { dismiss() })

        setContentView(root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.88f).toInt()
        )

        render("")
    }
}
