package ca.gmode.triprecorder.sync

import android.content.Context
import ca.gmode.triprecorder.diagnostics.AppLogStore
import java.time.Instant

data class SyncStatus(val state: String, val message: String, val updatedAt: String)

class SyncStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sync_status", Context.MODE_PRIVATE)
    private val appLog = AppLogStore(context)

    fun update(state: String, message: String) {
        val previous = read()
        val normalizedMessage = message.take(240)
        val recoveryPending = preferences.getBoolean(KEY_RECOVERY_PENDING, false)
        preferences.edit()
            .putString("state", state)
            .putString("message", normalizedMessage)
            .putString("updated_at", Instant.now().toString())
            .apply()
        when {
            state == STATE_SYNCHRONIZING -> Unit
            state == STATE_UP_TO_DATE -> {
                if (recoveryPending) {
                    appLog.append("sync", state, normalizedMessage)
                } else {
                    appLog.appendCoalesced(
                        "sync",
                        state,
                        normalizedMessage,
                        ROUTINE_SUCCESS_INTERVAL_MS,
                    )
                }
                preferences.edit().putBoolean(KEY_RECOVERY_PENDING, false).apply()
            }
            previous.state != state || previous.message != normalizedMessage -> {
                appLog.appendCoalesced("sync", state, normalizedMessage, REPEATED_EVENT_INTERVAL_MS)
                if (state.isFailureState()) {
                    preferences.edit().putBoolean(KEY_RECOVERY_PENDING, true).apply()
                }
            }
        }
    }

    fun read(): SyncStatus = SyncStatus(
        state = preferences.getString("state", "Not synchronized yet") ?: "Not synchronized yet",
        message = preferences.getString("message", "") ?: "",
        updatedAt = preferences.getString("updated_at", "") ?: "",
    )

    private fun String.isFailureState(): Boolean {
        val normalized = lowercase()
        return normalized.contains("fail") ||
            normalized.contains("waiting") ||
            normalized.contains("required") ||
            normalized.contains("error")
    }

    private companion object {
        const val KEY_RECOVERY_PENDING = "recovery_pending"
        const val STATE_SYNCHRONIZING = "Synchronizing"
        const val STATE_UP_TO_DATE = "Up to date"
        const val ROUTINE_SUCCESS_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val REPEATED_EVENT_INTERVAL_MS = 15 * 60 * 1_000L
    }
}
