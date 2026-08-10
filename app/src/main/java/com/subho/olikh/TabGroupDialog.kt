package com.subho.olikh

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

    private fun currentUrl(): String? =
        browserTabs.getOrNull(activeIndex)?.webView?.url
            ?: browserTabs.getOrNull(activeIndex)?.url

    private fun currentGroupName(): String {
        val id = store.groupFor(currentUrl()) ?: return "Not in a group"
        return store.getGroups().firstOrNull { it.id == id }?.name
            ?: "Not in a group"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(14, 17, 23))
        }

        root.addView(TextView(context).apply {
            text = "Tab Groups"
            textSize = 24f
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(context).apply {
            text = "Current: ${currentGroupName()}"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(6), 0, dp(12))
        })

        root.addView(button("Create group") {
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
            store.remove(currentUrl())
            onChanged()
            dismiss()
        })

        store.getGroups().forEach { group ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val count = browserTabs.count {
                store.groupFor(it.webView.url ?: it.url) == group.id
            }

            row.addView(TextView(context).apply {
                text = "${group.name}  ($count)"
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            row.addView(button("Open") {
                val index = browserTabs.indexOfFirst {
                    store.groupFor(it.webView.url ?: it.url) == group.id
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

            root.addView(row)
        }

        root.addView(button("Close") { dismiss() })

        setContentView(root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
