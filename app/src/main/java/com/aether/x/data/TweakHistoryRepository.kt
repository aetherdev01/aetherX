package com.aether.x.data

import android.content.Context
import com.aether.x.R
import com.aether.x.core.locale.str
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight, bounded local history for changes initiated by AetherX. */
data class TweakHistoryEntry(
    val title: String,
    val detail: String,
    val timestamp: Long,
    val success: Boolean = true,
)

class TweakHistoryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aetherx_tweak_history", Context.MODE_PRIVATE)

    @Synchronized
    fun record(title: String, detail: String, success: Boolean = true) {
        val items = read().toMutableList()
        items.add(0, TweakHistoryEntry(title, detail, System.currentTimeMillis(), success))
        val array = JSONArray()
        items.take(MAX_ENTRIES).forEach { item ->
            array.put(JSONObject().apply {
                put("title", item.title)
                put("detail", item.detail)
                put("timestamp", item.timestamp)
                put("success", item.success)
            })
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    @Synchronized
    fun read(): List<TweakHistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    TweakHistoryEntry(
                        title = item.optString("title", str(R.string.tweak_history_default_title)),
                        detail = item.optString("detail", ""),
                        timestamp = item.optLong("timestamp", 0L),
                        success = item.optBoolean("success", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private companion object {
        const val KEY_ENTRIES = "entries_json"
        const val MAX_ENTRIES = 40
    }
}
