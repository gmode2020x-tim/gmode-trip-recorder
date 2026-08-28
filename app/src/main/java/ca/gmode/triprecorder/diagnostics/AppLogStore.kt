package ca.gmode.triprecorder.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class AppLogEntry(
    val id: String,
    val at: String,
    val category: String,
    val state: String,
    val message: String,
)

class AppLogStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun append(category: String, state: String, message: String) = synchronized(LOCK) {
        val entries = recent(MAX_ENTRIES).toMutableList()
        entries += AppLogEntry(
            id = UUID.randomUUID().toString(),
            at = Instant.now().toString(),
            category = category.trim().take(32).ifBlank { "app" },
            state = state.trim().take(48).ifBlank { "info" },
            message = message.trim().take(240),
        )
        val array = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("at", entry.at)
                    .put("category", entry.category)
                    .put("state", entry.state)
                    .put("message", entry.message),
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun recent(limit: Int = MAX_ENTRIES): List<AppLogEntry> = synchronized(LOCK) {
        val array = runCatching { JSONArray(preferences.getString(KEY_ENTRIES, "[]")) }.getOrElse { JSONArray() }
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val id = value.optString("id")
                val at = value.optString("at")
                if (id.isBlank() || at.isBlank()) continue
                add(
                    AppLogEntry(
                        id = id,
                        at = at,
                        category = value.optString("category", "app"),
                        state = value.optString("state", "info"),
                        message = value.optString("message"),
                    ),
                )
            }
        }.takeLast(limit.coerceIn(1, MAX_ENTRIES))
    }

    private companion object {
        const val PREFERENCES = "app_diagnostic_log"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 100
        val LOCK = Any()
    }
}
