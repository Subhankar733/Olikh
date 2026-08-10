package com.subho.olikh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TabGroupStore(context: Context) {
    data class Group(val id: String, val name: String)

    private val prefs = context.getSharedPreferences(
        "olikh_tab_groups", Context.MODE_PRIVATE
    )

    private fun readGroups(): MutableList<Group> {
        val raw = prefs.getString("groups", null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    if (id.isNotBlank() && name.isNotBlank()) add(Group(id, name))
                }
            }.toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun writeGroups(groups: List<Group>) {
        val array = JSONArray()
        groups.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name))
        }
        prefs.edit().putString("groups", array.toString()).apply()
    }

    private fun readMemberships(): MutableMap<String, String> {
        val raw = prefs.getString("memberships", null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val groupId = obj.optString(key).trim()
                    if (key.isNotBlank() && groupId.isNotBlank()) put(key, groupId)
                }
            }.toMutableMap()
        }.getOrElse { mutableMapOf() }
    }

    private fun writeMemberships(memberships: Map<String, String>) {
        val obj = JSONObject()
        memberships.forEach { (url, groupId) -> obj.put(url, groupId) }
        prefs.edit().putString("memberships", obj.toString()).apply()
    }

    fun getGroups(): List<Group> = readGroups()

    fun create(name: String): Group? {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return null
        val groups = readGroups()
        if (groups.any { it.name.equals(clean, ignoreCase = true) }) return null
        val group = Group("group_${System.currentTimeMillis()}_${groups.size}", clean)
        groups += group
        writeGroups(groups)
        return group
    }

    fun rename(id: String, name: String): Boolean {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return false
        val groups = readGroups()
        if (groups.any { it.id != id && it.name.equals(clean, true) }) return false
        val index = groups.indexOfFirst { it.id == id }
        if (index < 0) return false
        groups[index] = groups[index].copy(name = clean)
        writeGroups(groups)
        return true
    }

    fun delete(id: String) {
        writeGroups(readGroups().filterNot { it.id == id })
        val memberships = readMemberships()
        memberships.entries.removeIf { it.value == id }
        writeMemberships(memberships)
    }

    fun groupFor(url: String?): String? =
        cleanKey(url)?.let { readMemberships()[it] }

    fun assign(url: String?, groupId: String): Boolean {
        val key = cleanKey(url) ?: return false
        if (readGroups().none { it.id == groupId }) return false
        val memberships = readMemberships()
        memberships[key] = groupId
        writeMemberships(memberships)
        return true
    }

    fun remove(url: String?) {
        val key = cleanKey(url) ?: return
        val memberships = readMemberships()
        memberships.remove(key)
        writeMemberships(memberships)
    }

    private fun cleanKey(url: String?): String? =
        url?.trim()?.takeIf {
            it.startsWith("https://", true) || it.startsWith("http://", true)
        }
}
