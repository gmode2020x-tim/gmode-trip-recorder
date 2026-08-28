package ca.gmode.triprecorder.tracking

import android.content.Context

data class TrackingDiagnostic(
    val status: String = "GPS standby",
    val updatedAtEpochMillis: Long = 0L,
    val lastFixAtEpochMillis: Long? = null,
    val retryCount: Int = 0,
)

class TrackingDiagnosticStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): TrackingDiagnostic = TrackingDiagnostic(
        status = preferences.getString(KEY_STATUS, "GPS standby") ?: "GPS standby",
        updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L),
        lastFixAtEpochMillis = preferences.getLong(KEY_LAST_FIX_AT, 0L).takeIf { it > 0L },
        retryCount = preferences.getInt(KEY_RETRY_COUNT, 0).coerceAtLeast(0),
    )

    fun updateStatus(message: String, retryCount: Int = read().retryCount) {
        preferences.edit()
            .putString(KEY_STATUS, message.take(240))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .putInt(KEY_RETRY_COUNT, retryCount.coerceAtLeast(0))
            .apply()
    }

    fun markFix(accuracyMeters: Float?) {
        val accuracy = accuracyMeters?.let { " ±${it.toInt()} m" }.orEmpty()
        preferences.edit()
            .putString(KEY_STATUS, "GPS fix received$accuracy")
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_FIX_AT, System.currentTimeMillis())
            .putInt(KEY_RETRY_COUNT, 0)
            .apply()
    }

    fun reset(message: String) {
        preferences.edit()
            .putString(KEY_STATUS, message.take(240))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .remove(KEY_LAST_FIX_AT)
            .putInt(KEY_RETRY_COUNT, 0)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "tracking_diagnostics"
        const val KEY_STATUS = "status"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_LAST_FIX_AT = "last_fix_at"
        const val KEY_RETRY_COUNT = "retry_count"
    }
}
