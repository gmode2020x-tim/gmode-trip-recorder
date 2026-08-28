package ca.gmode.triprecorder.sync

import android.content.Context

data class RemoteControlState(
    val revision: Int = 0,
    val notice: String = "",
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val lastAppliedRevision: Int = 0,
    val lastCommandId: String = "",
    val updatedAt: String = "",
) {
    fun updateAvailable(currentVersion: String): Boolean =
        downloadUrl.isNotBlank() && VersionOrder.isNewer(latestVersion, currentVersion)
}

class RemoteControlStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): RemoteControlState = RemoteControlState(
        revision = preferences.getInt(KEY_REVISION, 0),
        notice = preferences.getString(KEY_NOTICE, "") ?: "",
        latestVersion = preferences.getString(KEY_LATEST_VERSION, "") ?: "",
        downloadUrl = preferences.getString(KEY_DOWNLOAD_URL, "") ?: "",
        sha256 = preferences.getString(KEY_SHA256, "") ?: "",
        lastAppliedRevision = preferences.getInt(KEY_LAST_APPLIED_REVISION, 0),
        lastCommandId = preferences.getString(KEY_LAST_COMMAND_ID, "") ?: "",
        updatedAt = preferences.getString(KEY_UPDATED_AT, "") ?: "",
    )

    fun saveMetadata(
        revision: Int,
        notice: String,
        latestVersion: String,
        downloadUrl: String,
        sha256: String,
        updatedAt: String,
    ) {
        preferences.edit()
            .putInt(KEY_REVISION, revision.coerceAtLeast(0))
            .putString(KEY_NOTICE, notice.take(500))
            .putString(KEY_LATEST_VERSION, latestVersion.take(32))
            .putString(KEY_DOWNLOAD_URL, downloadUrl.take(500))
            .putString(KEY_SHA256, sha256.take(64).lowercase())
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
    }

    fun markApplied(revision: Int) {
        preferences.edit().putInt(KEY_LAST_APPLIED_REVISION, revision.coerceAtLeast(0)).apply()
    }

    fun markCommand(commandId: String) {
        preferences.edit().putString(KEY_LAST_COMMAND_ID, commandId.take(128)).apply()
    }

    private companion object {
        const val PREFERENCES = "ha_remote_control"
        const val KEY_REVISION = "revision"
        const val KEY_NOTICE = "notice"
        const val KEY_LATEST_VERSION = "latest_version"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_LAST_APPLIED_REVISION = "last_applied_revision"
        const val KEY_LAST_COMMAND_ID = "last_command_id"
        const val KEY_UPDATED_AT = "updated_at"
    }
}

object VersionOrder {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val count = maxOf(candidateParts.size, currentParts.size, 3)
        for (index in 0 until count) {
            val next = candidateParts.getOrElse(index) { 0 }
            val installed = currentParts.getOrElse(index) { 0 }
            if (next != installed) return next > installed
        }
        return false
    }
}
