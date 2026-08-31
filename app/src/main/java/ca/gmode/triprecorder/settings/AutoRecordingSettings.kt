package ca.gmode.triprecorder.settings

import android.content.Context
import ca.gmode.triprecorder.diagnostics.AppLogStore

data class AutoRecordingConfig(
    val enabled: Boolean = false,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val homeRadiusMeters: Int = DEFAULT_HOME_RADIUS_METERS,
    val homeWifiSsid: String? = null,
    val wifiDepartureDelayMinutes: Int = DEFAULT_WIFI_DEPARTURE_DELAY_MINUTES,
    val returnDwellMinutes: Int = DEFAULT_RETURN_DWELL_MINUTES,
    val locationIntervalSeconds: Int = DEFAULT_LOCATION_INTERVAL_SECONDS,
    val minimumDistanceMeters: Int = DEFAULT_MINIMUM_DISTANCE_METERS,
    val tripType: String = "street",
    val stationaryTrimEnabled: Boolean = true,
    val stationaryAutoPauseEnabled: Boolean = true,
    val stationaryRadiusMeters: Int = DEFAULT_STATIONARY_RADIUS_METERS,
    val stationaryPauseMinutes: Int = DEFAULT_STATIONARY_PAUSE_MINUTES,
    val stationarySplitMinutes: Int = DEFAULT_STATIONARY_SPLIT_MINUTES,
    val stationarySpeedKmh: Double = DEFAULT_STATIONARY_SPEED_KMH,
    val stopManualTripsAtHome: Boolean = false,
) {
    fun normalized(): AutoRecordingConfig = copy(
        homeLatitude = homeLatitude?.takeIf { it in -90.0..90.0 },
        homeLongitude = homeLongitude?.takeIf { it in -180.0..180.0 },
        homeRadiusMeters = homeRadiusMeters.coerceIn(100, 5_000),
        homeWifiSsid = homeWifiSsid?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }?.take(64),
        wifiDepartureDelayMinutes = wifiDepartureDelayMinutes.coerceIn(1, 30),
        returnDwellMinutes = returnDwellMinutes.coerceIn(1, 120),
        locationIntervalSeconds = locationIntervalSeconds.coerceIn(2, 300),
        minimumDistanceMeters = minimumDistanceMeters.coerceIn(1, 500),
        tripType = tripType.takeIf { it in TRIP_TYPES } ?: "street",
        stationaryRadiusMeters = stationaryRadiusMeters.coerceIn(25, 500),
        stationaryPauseMinutes = stationaryPauseMinutes.coerceIn(1, 30),
        stationarySplitMinutes = stationarySplitMinutes
            .coerceIn(5, 120)
            .coerceAtLeast(stationaryPauseMinutes.coerceIn(1, 30)),
        stationarySpeedKmh = stationarySpeedKmh.coerceIn(1.0, 20.0),
    )

    val hasHomeLocation: Boolean
        get() = homeLatitude != null && homeLongitude != null

    val hasHomeWifi: Boolean
        get() = !homeWifiSsid.isNullOrBlank()

    companion object {
        const val DEFAULT_HOME_RADIUS_METERS = 250
        const val DEFAULT_WIFI_DEPARTURE_DELAY_MINUTES = 2
        const val DEFAULT_RETURN_DWELL_MINUTES = 5
        const val DEFAULT_LOCATION_INTERVAL_SECONDS = 5
        const val DEFAULT_MINIMUM_DISTANCE_METERS = 5
        const val DEFAULT_STATIONARY_RADIUS_METERS = 150
        const val DEFAULT_STATIONARY_PAUSE_MINUTES = 3
        const val DEFAULT_STATIONARY_SPLIT_MINUTES = 15
        const val DEFAULT_STATIONARY_SPEED_KMH = 5.4
        val TRIP_TYPES = setOf("street", "off_road", "snow", "water")
    }
}

class AutoRecordingSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): AutoRecordingConfig = AutoRecordingConfig(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        homeLatitude = preferences.getNullableDouble(KEY_HOME_LATITUDE),
        homeLongitude = preferences.getNullableDouble(KEY_HOME_LONGITUDE),
        homeRadiusMeters = preferences.getInt(KEY_HOME_RADIUS, AutoRecordingConfig.DEFAULT_HOME_RADIUS_METERS),
        homeWifiSsid = preferences.getString(KEY_HOME_WIFI_SSID, null),
        wifiDepartureDelayMinutes = preferences.getInt(
            KEY_WIFI_DEPARTURE_DELAY,
            AutoRecordingConfig.DEFAULT_WIFI_DEPARTURE_DELAY_MINUTES,
        ),
        returnDwellMinutes = preferences.getInt(KEY_RETURN_DWELL, AutoRecordingConfig.DEFAULT_RETURN_DWELL_MINUTES),
        locationIntervalSeconds = preferences.getInt(KEY_LOCATION_INTERVAL, AutoRecordingConfig.DEFAULT_LOCATION_INTERVAL_SECONDS),
        minimumDistanceMeters = preferences.getInt(KEY_MINIMUM_DISTANCE, AutoRecordingConfig.DEFAULT_MINIMUM_DISTANCE_METERS),
        tripType = preferences.getString(KEY_TRIP_TYPE, "street") ?: "street",
        stationaryTrimEnabled = preferences.getBoolean(KEY_STATIONARY_TRIM_ENABLED, true),
        stationaryAutoPauseEnabled = preferences.getBoolean(KEY_STATIONARY_AUTO_PAUSE_ENABLED, true),
        stationaryRadiusMeters = preferences.getInt(
            KEY_STATIONARY_RADIUS,
            AutoRecordingConfig.DEFAULT_STATIONARY_RADIUS_METERS,
        ),
        stationaryPauseMinutes = preferences.getInt(
            KEY_STATIONARY_PAUSE,
            AutoRecordingConfig.DEFAULT_STATIONARY_PAUSE_MINUTES,
        ),
        stationarySplitMinutes = preferences.getInt(
            KEY_STATIONARY_SPLIT,
            AutoRecordingConfig.DEFAULT_STATIONARY_SPLIT_MINUTES,
        ),
        stationarySpeedKmh = java.lang.Double.longBitsToDouble(
            preferences.getLong(
                KEY_STATIONARY_SPEED,
                java.lang.Double.doubleToRawLongBits(AutoRecordingConfig.DEFAULT_STATIONARY_SPEED_KMH),
            ),
        ),
        stopManualTripsAtHome = preferences.getBoolean(KEY_STOP_MANUAL_AT_HOME, false),
    ).normalized()

    fun save(config: AutoRecordingConfig) {
        val normalized = config.normalized()
        preferences.edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putNullableDouble(KEY_HOME_LATITUDE, normalized.homeLatitude)
            .putNullableDouble(KEY_HOME_LONGITUDE, normalized.homeLongitude)
            .putInt(KEY_HOME_RADIUS, normalized.homeRadiusMeters)
            .putString(KEY_HOME_WIFI_SSID, normalized.homeWifiSsid)
            .putInt(KEY_WIFI_DEPARTURE_DELAY, normalized.wifiDepartureDelayMinutes)
            .putInt(KEY_RETURN_DWELL, normalized.returnDwellMinutes)
            .putInt(KEY_LOCATION_INTERVAL, normalized.locationIntervalSeconds)
            .putInt(KEY_MINIMUM_DISTANCE, normalized.minimumDistanceMeters)
            .putString(KEY_TRIP_TYPE, normalized.tripType)
            .putBoolean(KEY_STATIONARY_TRIM_ENABLED, normalized.stationaryTrimEnabled)
            .putBoolean(KEY_STATIONARY_AUTO_PAUSE_ENABLED, normalized.stationaryAutoPauseEnabled)
            .putInt(KEY_STATIONARY_RADIUS, normalized.stationaryRadiusMeters)
            .putInt(KEY_STATIONARY_PAUSE, normalized.stationaryPauseMinutes)
            .putInt(KEY_STATIONARY_SPLIT, normalized.stationarySplitMinutes)
            .putLong(KEY_STATIONARY_SPEED, java.lang.Double.doubleToRawLongBits(normalized.stationarySpeedKmh))
            .putBoolean(KEY_STOP_MANUAL_AT_HOME, normalized.stopManualTripsAtHome)
            .apply()
    }

    private fun android.content.SharedPreferences.getNullableDouble(key: String): Double? =
        if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, 0L)) else null

    private fun android.content.SharedPreferences.Editor.putNullableDouble(
        key: String,
        value: Double?,
    ): android.content.SharedPreferences.Editor = if (value == null) remove(key) else putLong(
        key,
        java.lang.Double.doubleToRawLongBits(value),
    )

    private companion object {
        const val PREFERENCES = "auto_recording_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_HOME_LATITUDE = "home_latitude"
        const val KEY_HOME_LONGITUDE = "home_longitude"
        const val KEY_HOME_RADIUS = "home_radius_meters"
        const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"
        const val KEY_WIFI_DEPARTURE_DELAY = "wifi_departure_delay_minutes"
        const val KEY_RETURN_DWELL = "return_dwell_minutes"
        const val KEY_LOCATION_INTERVAL = "location_interval_seconds"
        const val KEY_MINIMUM_DISTANCE = "minimum_distance_meters"
        const val KEY_TRIP_TYPE = "trip_type"
        const val KEY_STATIONARY_TRIM_ENABLED = "stationary_trim_enabled"
        const val KEY_STATIONARY_AUTO_PAUSE_ENABLED = "stationary_auto_pause_enabled"
        const val KEY_STATIONARY_RADIUS = "stationary_radius_meters"
        const val KEY_STATIONARY_PAUSE = "stationary_pause_minutes"
        const val KEY_STATIONARY_SPLIT = "stationary_split_minutes"
        const val KEY_STATIONARY_SPEED = "stationary_speed_kmh"
        const val KEY_STOP_MANUAL_AT_HOME = "stop_manual_trips_at_home"
    }
}

data class StationaryAutoPauseState(
    val tripId: String,
    val startedAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
)

class AutoRecordingStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val appLog = AppLogStore(context)

    var activeAutoTripId: String?
        get() = preferences.getString(KEY_ACTIVE_TRIP_ID, null)
        set(value) {
            preferences.edit().apply {
                val changed = preferences.getString(KEY_ACTIVE_TRIP_ID, null) != value
                if (value == null) remove(KEY_ACTIVE_TRIP_ID) else putString(KEY_ACTIVE_TRIP_ID, value)
                if (changed) {
                    remove(KEY_RETURN_DWELL_DEADLINE_EPOCH_MS)
                    remove(KEY_RETURN_DWELL_TRIP_ID)
                    remove(KEY_STATIONARY_PAUSE_TRIP_ID)
                    remove(KEY_STATIONARY_PAUSE_STARTED_AT_EPOCH_MS)
                    remove(KEY_STATIONARY_PAUSE_LATITUDE)
                    remove(KEY_STATIONARY_PAUSE_LONGITUDE)
                }
            }.apply()
        }

    val returnDwellDeadlineEpochMs: Long?
        get() = preferences.getLong(KEY_RETURN_DWELL_DEADLINE_EPOCH_MS, 0L).takeIf { it > 0L }

    var returnDwellTripId: String?
        get() = preferences.getString(KEY_RETURN_DWELL_TRIP_ID, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_RETURN_DWELL_TRIP_ID) else putString(KEY_RETURN_DWELL_TRIP_ID, value)
            }.apply()
        }

    fun beginReturnDwell(
        tripId: String,
        dwellMinutes: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Long? {
        if (tripId.isBlank()) return null
        if (returnDwellTripId == tripId) return returnDwellDeadlineEpochMs
        val deadline = nowEpochMs + dwellMinutes.coerceIn(1, 120) * 60_000L
        preferences.edit()
            .putString(KEY_RETURN_DWELL_TRIP_ID, tripId)
            .putLong(KEY_RETURN_DWELL_DEADLINE_EPOCH_MS, deadline)
            .apply()
        return deadline
    }

    fun clearReturnDwell() {
        preferences.edit()
            .remove(KEY_RETURN_DWELL_DEADLINE_EPOCH_MS)
            .remove(KEY_RETURN_DWELL_TRIP_ID)
            .apply()
    }

    val stationaryAutoPause: StationaryAutoPauseState?
        get() {
            val tripId = preferences.getString(KEY_STATIONARY_PAUSE_TRIP_ID, null)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val startedAt = preferences.getLong(KEY_STATIONARY_PAUSE_STARTED_AT_EPOCH_MS, 0L)
            if (startedAt <= 0L || !preferences.contains(KEY_STATIONARY_PAUSE_LATITUDE) ||
                !preferences.contains(KEY_STATIONARY_PAUSE_LONGITUDE)
            ) {
                return null
            }
            return StationaryAutoPauseState(
                tripId = tripId,
                startedAtEpochMs = startedAt,
                latitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_STATIONARY_PAUSE_LATITUDE, 0L)),
                longitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_STATIONARY_PAUSE_LONGITUDE, 0L)),
            )
        }

    fun beginStationaryAutoPause(
        tripId: String,
        latitude: Double,
        longitude: Double,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        if (tripId.isBlank() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        preferences.edit()
            .putString(KEY_STATIONARY_PAUSE_TRIP_ID, tripId)
            .putLong(KEY_STATIONARY_PAUSE_STARTED_AT_EPOCH_MS, nowEpochMs.coerceAtLeast(1L))
            .putLong(KEY_STATIONARY_PAUSE_LATITUDE, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_STATIONARY_PAUSE_LONGITUDE, java.lang.Double.doubleToRawLongBits(longitude))
            .apply()
    }

    fun clearStationaryAutoPause() {
        preferences.edit()
            .remove(KEY_STATIONARY_PAUSE_TRIP_ID)
            .remove(KEY_STATIONARY_PAUSE_STARTED_AT_EPOCH_MS)
            .remove(KEY_STATIONARY_PAUSE_LATITUDE)
            .remove(KEY_STATIONARY_PAUSE_LONGITUDE)
            .apply()
    }

    fun status(): String = preferences.getString(KEY_STATUS, "Automatic recording is off")
        ?: "Automatic recording is off"

    fun updateStatus(message: String) {
        val previous = status()
        preferences.edit().putString(KEY_STATUS, message.take(240)).apply()
        if (previous != message) appLog.append("automatic", "status", message)
    }

    private companion object {
        const val PREFERENCES = "auto_recording_state"
        const val KEY_ACTIVE_TRIP_ID = "active_auto_trip_id"
        const val KEY_RETURN_DWELL_DEADLINE_EPOCH_MS = "return_dwell_deadline_epoch_ms"
        const val KEY_RETURN_DWELL_TRIP_ID = "return_dwell_trip_id"
        const val KEY_STATIONARY_PAUSE_TRIP_ID = "stationary_pause_trip_id"
        const val KEY_STATIONARY_PAUSE_STARTED_AT_EPOCH_MS = "stationary_pause_started_at_epoch_ms"
        const val KEY_STATIONARY_PAUSE_LATITUDE = "stationary_pause_latitude"
        const val KEY_STATIONARY_PAUSE_LONGITUDE = "stationary_pause_longitude"
        const val KEY_STATUS = "status"
    }
}
