package ca.gmode.triprecorder.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.gmode.triprecorder.BuildConfig
import ca.gmode.triprecorder.auto.AutoRecordingManager
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.diagnostics.AppLogStore
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import ca.gmode.triprecorder.settings.SecureSettings
import ca.gmode.triprecorder.tracking.TrackingDiagnosticStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

class UploadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val dao = AppDatabase.get(appContext).tripDao()
    private val settings = SecureSettings(appContext)
    private val statusStore = SyncStatusStore(appContext)
    private val remoteControl = RemoteControlStore(appContext)
    private val appLog = AppLogStore(appContext)
    private val automaticSettings = AutoRecordingSettings(appContext)
    private val automaticState = AutoRecordingStateStore(appContext)
    private val trackingDiagnostics = TrackingDiagnosticStore(appContext)
    private val networkClient = HomeAssistantNetworkClient(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = settings.token()
        val baseUrl = settings.baseUrl
        if (token.isBlank() || baseUrl.isBlank()) {
            statusStore.update("Setup required", "Save the Home Assistant URL and access token.")
            return@withContext Result.failure()
        }

        statusStore.update("Synchronizing", "Uploading locally saved trip data.")
        try {
            val client = networkClient.create(baseUrl)
            exchangeDiagnosticsAndControl(baseUrl, token, client)
            repeat(MAX_BATCHES_PER_RUN) {
                val trip = dao.getOldestDirtyTrip() ?: run {
                    statusStore.update("Up to date", "All recorded points and diagnostics are stored in Home Assistant.")
                    exchangeDiagnosticsAndControl(baseUrl, token, client)
                    return@withContext Result.success()
                }
                val points = dao.getPendingPoints(trip.id, BATCH_SIZE)
                val body = UploadPayloadFactory.build(settings.deviceId, trip, points)
                val request = Request.Builder()
                    .url("$baseUrl/api/gmode_trip_recorder/mobile/upload")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JSONObject(responseText).optString("error")
                        }.getOrNull().orEmpty().ifBlank { "HTTP ${response.code}" }
                        statusStore.update("Sync failed", message)
                        return@withContext if (response.code >= 500 || response.code == 408 || response.code == 429) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }

                    val acknowledged = JSONObject(responseText)
                        .optJSONArray("acknowledgedPointIds")
                        ?.let { array ->
                            buildList {
                                for (index in 0 until array.length()) add(array.getString(index))
                            }
                        }
                        .orEmpty()
                    if (acknowledged.isNotEmpty()) dao.markPointsSynced(acknowledged)
                    if (dao.getPendingPointCount(trip.id) == 0) {
                        dao.markTripSyncedIfUnchanged(trip.id, trip.updatedAtEpochMs)
                    }
                }
            }
            statusStore.update("Sync queued", "More locally saved points remain; synchronization will continue.")
            exchangeDiagnosticsAndControl(baseUrl, token, client)
            Result.retry()
        } catch (error: UploadHttpException) {
            statusStore.update("Sync failed", error.message ?: "Home Assistant rejected the request.")
            if (error.retryable) Result.retry() else Result.failure()
        } catch (error: LocalNetworkUnavailableException) {
            statusStore.update("Waiting for home Wi-Fi", error.message ?: "Home Wi-Fi is unavailable.")
            Result.retry()
        } catch (error: IOException) {
            statusStore.update("Waiting for connection", error.message ?: "Home Assistant is unreachable.")
            Result.retry()
        } catch (error: Exception) {
            statusStore.update("Sync failed", error.message ?: error.javaClass.simpleName)
            Result.retry()
        }
    }

    private suspend fun exchangeDiagnosticsAndControl(baseUrl: String, token: String, client: OkHttpClient) {
        val sync = statusStore.read()
        val gps = trackingDiagnostics.read()
        val auto = automaticSettings.read()
        val active = dao.getActiveTrip()
        val pending = dao.getTotalPendingPointCount()
        val power = applicationContext.getSystemService(PowerManager::class.java)
        val battery = applicationContext.getSystemService(BatteryManager::class.java)
        val location = applicationContext.getSystemService(LocationManager::class.java)
        val fineLocation = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val batteryUnrestricted = power.isIgnoringBatteryOptimizations(applicationContext.packageName)
        val overallStatus = when {
            !fineLocation || !backgroundLocation || !location.isLocationEnabled -> "attention"
            gps.retryCount > 0 -> "gps_retry"
            active != null -> "recording"
            else -> "ready"
        }
        val snapshot = JSONObject()
            .put("overallStatus", overallStatus)
            .put("syncState", sync.state)
            .put("syncMessage", sync.message)
            .put("gpsStatus", gps.status)
            .put("gpsRetryCount", gps.retryCount)
            .put("autoEnabled", auto.enabled)
            .put("autoStatus", automaticState.status())
            .put("pendingPoints", pending)
            .put("activeTripId", active?.id.orEmpty())
            .put("activeTripTitle", active?.title.orEmpty())
            .put("fineLocationGranted", fineLocation)
            .put("backgroundLocationGranted", backgroundLocation)
            .put("notificationsGranted", notifications)
            .put("batteryUnrestricted", batteryUnrestricted)
            .put("locationEnabled", location.isLocationEnabled)
            .put("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceAtLeast(0))
            .put("lastCommandId", remoteControl.read().lastCommandId)
        val logs = JSONArray().apply {
            appLog.recent(MAX_DIAGNOSTIC_LOGS).forEach { entry ->
                put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("at", entry.at)
                        .put("category", entry.category)
                        .put("state", entry.state)
                        .put("message", entry.message),
                )
            }
        }
        val body = JSONObject()
            .put("protocolVersion", 1)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("deviceId", settings.deviceId)
            .put("sentAt", Instant.now().toString())
            .put("acknowledgedCommandId", remoteControl.read().lastCommandId)
            .put("snapshot", snapshot)
            .put("logs", logs)
        val request = Request.Builder()
            .url("$baseUrl/api/gmode_trip_recorder/mobile/diagnostics")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                    .orEmpty().ifBlank { "Diagnostics HTTP ${response.code}" }
                throw UploadHttpException(message, response.code >= 500 || response.code == 408 || response.code == 429)
            }
            val payload = JSONObject(responseText)
            processControl(payload.optJSONObject("control") ?: JSONObject(), payload.optString("updatedAt"))
        }
    }

    private fun processControl(control: JSONObject, updatedAt: String) {
        val revision = control.optInt("revision", 0).coerceAtLeast(0)
        remoteControl.saveMetadata(
            revision = revision,
            notice = control.optString("notice"),
            latestVersion = control.optString("latestVersion"),
            downloadUrl = control.optString("downloadUrl"),
            sha256 = control.optString("sha256"),
            updatedAt = updatedAt,
        )
        val stored = remoteControl.read()
        if (revision > stored.lastAppliedRevision) {
            val values = control.optJSONObject("settings") ?: JSONObject()
            val current = automaticSettings.read()
            automaticSettings.save(
                current.copy(
                    homeRadiusMeters = values.optInt("homeRadiusMeters", current.homeRadiusMeters),
                    wifiDepartureDelayMinutes = values.optInt(
                        "wifiDepartureDelayMinutes",
                        current.wifiDepartureDelayMinutes,
                    ),
                    returnDwellMinutes = values.optInt("returnDwellMinutes", current.returnDwellMinutes),
                    locationIntervalSeconds = values.optInt("locationIntervalSeconds", current.locationIntervalSeconds),
                    minimumDistanceMeters = values.optInt("minimumDistanceMeters", current.minimumDistanceMeters),
                    tripType = values.optString("tripType", current.tripType),
                ),
            )
            remoteControl.markApplied(revision)
            AutoRecordingManager(applicationContext).refreshRegistration()
            appLog.append("control", "applied", "Applied Home Assistant control revision $revision")
        }
        val command = control.optJSONObject("command") ?: return
        val commandId = command.optString("id")
        if (commandId.isBlank() || commandId == remoteControl.read().lastCommandId) return
        when (command.optString("action")) {
            "rearm" -> AutoRecordingManager(applicationContext).refreshRegistration()
            "sync" -> Unit
            else -> return
        }
        remoteControl.markCommand(commandId)
        appLog.append("control", "acknowledged", "Handled HA command ${command.optString("action")}")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val BATCH_SIZE = 500
        private const val MAX_BATCHES_PER_RUN = 50
        private const val MAX_DIAGNOSTIC_LOGS = 100
    }
}

private class UploadHttpException(message: String, val retryable: Boolean) : Exception(message)
