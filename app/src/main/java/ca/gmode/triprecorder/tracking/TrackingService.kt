package ca.gmode.triprecorder.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ca.gmode.triprecorder.MainActivity
import ca.gmode.triprecorder.R
import ca.gmode.triprecorder.auto.ReturnDwellWorker
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.PhoneSnapshot
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.data.SensorSnapshot
import ca.gmode.triprecorder.diagnostics.AppLogStore
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import ca.gmode.triprecorder.sync.SyncScheduler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackingService : LifecycleService() {
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var repository: RecordingRepository
    private lateinit var sensors: SensorCollector
    private lateinit var locationManager: LocationManager
    private lateinit var liveTelemetry: LiveTelemetryStore
    private lateinit var diagnostics: TrackingDiagnosticStore
    private lateinit var automaticSettings: AutoRecordingSettings
    private lateinit var automaticState: AutoRecordingStateStore
    private var currentTripId: String? = null
    private var satelliteCount: Int? = null
    private var tracking = false
    private var locationRequest: LocationRequest? = null
    private var recordingConfig = AutoRecordingConfig()
    private var automaticTrip = false
    private var stationaryPaused = false
    private var stationaryPauseTracker: StationaryAutoPauseTracker? = null
    private var lastFixElapsedRealtimeMs: Long? = null
    private var gpsRetryCount = 0
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gpsWatchdog = Runnable { checkGpsHealth() }
    private val locationProcessingMutex = Mutex()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::recordLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (tracking && !stationaryPaused && !availability.isLocationAvailable && lastFixElapsedRealtimeMs == null) {
                diagnostics.updateStatus("GPS provider is not returning a location yet", gpsRetryCount)
                updateNotification("GPS unavailable — automatic retry is active")
            }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AppLogStore(this).append(
                "gps",
                "permission_missing",
                "Recording service restart was cancelled because precise location permission is missing",
            )
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Preparing GPS…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        repository = RecordingRepository(AppDatabase.get(this).tripDao())
        sensors = SensorCollector(this)
        locationManager = getSystemService(LocationManager::class.java)
        liveTelemetry = LiveTelemetryStore(this)
        diagnostics = TrackingDiagnosticStore(this)
        automaticSettings = AutoRecordingSettings(this)
        automaticState = AutoRecordingStateStore(this)
        initialized = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!initialized) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> beginTracking(intent.getStringExtra(EXTRA_TRIP_ID))
            ACTION_STOP -> stopTripAndService()
            else -> lifecycleScope.launch { beginTracking(repository.activeTrip()?.id) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingResources()
        super.onDestroy()
    }

    private fun beginTracking(tripId: String?) {
        if (tripId.isNullOrBlank() || tracking) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            diagnostics.reset("Precise location permission is missing — recording could not start")
            updateNotification("Precise location permission is required")
            stopSelf()
            return
        }
        currentTripId = tripId
        tracking = true
        runCatching {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        }
        recordingConfig = automaticSettings.read()
        automaticTrip = automaticState.activeAutoTripId == tripId
        stationaryPauseTracker = if (
            automaticTrip && recordingConfig.stationaryTrimEnabled && recordingConfig.stationaryAutoPauseEnabled
        ) {
            StationaryAutoPauseTracker(
                radiusMeters = recordingConfig.stationaryRadiusMeters.toDouble(),
                pauseMillis = recordingConfig.stationaryPauseMinutes * 60_000L,
                stationarySpeedMps = recordingConfig.stationarySpeedKmh / 3.6,
            )
        } else {
            null
        }
        val restoredPause = automaticState.stationaryAutoPause?.takeIf { it.tripId == tripId }
        if (restoredPause != null && stationaryPauseTracker != null) {
            beginStationaryPause(restoredPause.latitude, restoredPause.longitude, restored = true)
        } else {
            automaticState.stationaryAutoPause?.takeIf { it.tripId == tripId }?.let {
                automaticState.clearStationaryAutoPause()
            }
            beginHighAccuracyTracking("Waiting for a GPS fix")
        }
    }

    private fun beginHighAccuracyTracking(message: String) {
        stationaryPaused = false
        sensors.start()
        val intervalMs = recordingConfig.locationIntervalSeconds * 1_000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(1_000L))
            .setMinUpdateDistanceMeters(recordingConfig.minimumDistanceMeters.toFloat())
            .setMaxUpdateDelayMillis((intervalMs * 2).coerceAtLeast(10_000L))
            .build()
        locationRequest = request
        lastFixElapsedRealtimeMs = null
        gpsRetryCount = 0
        diagnostics.reset("Starting high-accuracy GPS")
        requestLocationUpdates(message)
    }

    private fun recordLocation(location: Location) {
        lastFixElapsedRealtimeMs = SystemClock.elapsedRealtime()
        gpsRetryCount = 0
        diagnostics.markFix(location.accuracy.takeIf { location.hasAccuracy() })
        scheduleGpsWatchdog()
        lifecycleScope.launch {
            locationProcessingMutex.withLock {
                if (stationaryPaused) {
                    handlePausedLocation(location)
                } else {
                    recordMovingLocation(location)
                }
            }
        }
    }

    private suspend fun recordMovingLocation(location: Location) {
        val point = recordPoint(location, sensors.snapshotAndReset()) ?: return
        val speedKmh = (point.speedMps ?: 0.0) * 3.6
        val accuracy = point.accuracyMeters?.let { " ±${it.toInt()} m" }.orEmpty()
        updateNotification("${"%.0f".format(speedKmh)} km/h$accuracy • saved on phone")
        val tracker = stationaryPauseTracker ?: return
        if (tracker.observe(location.stationarySample())) {
            beginStationaryPause(location.latitude, location.longitude)
        }
    }

    private suspend fun handlePausedLocation(location: Location) {
        val pause = automaticState.stationaryAutoPause
        val tripId = currentTripId
        if (pause == null || pause.tripId != tripId) {
            automaticState.clearStationaryAutoPause()
            beginHighAccuracyTracking("Pause state cleared — resuming high-accuracy GPS")
            recordMovingLocation(location)
            return
        }
        val shouldResume = StationaryAutoResumePolicy.shouldResume(
            sample = location.stationarySample(),
            pausedLatitude = pause.latitude,
            pausedLongitude = pause.longitude,
            stationarySpeedMps = recordingConfig.stationarySpeedKmh / 3.6,
            stationaryRadiusMeters = recordingConfig.stationaryRadiusMeters.toDouble(),
            minimumMovementMeters = recordingConfig.minimumDistanceMeters.toDouble(),
        )
        if (!shouldResume) {
            updateNotification("Trip paused — watching for movement")
            return
        }
        recordPauseEndAnchor(location, pause.latitude, pause.longitude)
        automaticState.clearStationaryAutoPause()
        stationaryPauseTracker?.reset()
        automaticState.updateStatus("Movement detected — automatic trip resumed")
        beginHighAccuracyTracking("Movement detected — reacquiring high-accuracy GPS")
        recordMovingLocation(location)
        SyncScheduler.enqueue(this)
    }

    private suspend fun recordPauseEndAnchor(location: Location, latitude: Double, longitude: Double) {
        val anchor = Location(location).apply {
            this.latitude = latitude
            this.longitude = longitude
            time = (location.time - PAUSE_END_ANCHOR_OFFSET_MS).coerceAtLeast(1L)
            speed = 0f
            accuracy = location.accuracy.takeIf { location.hasAccuracy() } ?: recordingConfig.stationaryRadiusMeters.toFloat()
        }
        recordPoint(anchor, EMPTY_SENSOR_SNAPSHOT)
    }

    private suspend fun recordPoint(location: Location, sensorSnapshot: SensorSnapshot) = currentTripId?.let { tripId ->
        repository.recordLocation(
            tripId = tripId,
            location = location,
            sensors = sensorSnapshot,
            phone = phoneSnapshot(),
        )?.also { point ->
            liveTelemetry.update(point, sensors.orientation())
            SyncScheduler.enqueue(this)
        }
    }

    private fun Location.stationarySample() = StationaryLocationSample(
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        latitude = latitude,
        longitude = longitude,
        speedMps = speed.takeIf { hasSpeed() }?.toDouble(),
        accuracyMeters = accuracy.takeIf { hasAccuracy() }?.toDouble(),
    )

    private fun beginStationaryPause(latitude: Double, longitude: Double, restored: Boolean = false) {
        val tripId = currentTripId ?: return
        stationaryPaused = true
        sensors.stop()
        sensors.snapshotAndReset()
        mainHandler.removeCallbacks(gpsWatchdog)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, PAUSED_LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(PAUSED_MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(recordingConfig.minimumDistanceMeters.coerceAtLeast(10).toFloat())
            .setMaxUpdateDelayMillis(PAUSED_MAX_UPDATE_DELAY_MS)
            .build()
        lastFixElapsedRealtimeMs = null
        gpsRetryCount = 0
        if (!restored) {
            automaticState.beginStationaryAutoPause(tripId, latitude, longitude)
        }
        stationaryPauseTracker?.reset()
        val message = if (restored) {
            "Stationary pause restored — watching for movement"
        } else {
            "Stationary for ${recordingConfig.stationaryPauseMinutes} minutes — automatic trip paused"
        }
        automaticState.updateStatus(message)
        diagnostics.reset("Trip paused — low-power movement watch active")
        requestLocationUpdates("Trip paused — watching for movement")
    }

    private fun requestLocationUpdates(waitingMessage: String) {
        if (!tracking) return
        val request = locationRequest ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            diagnostics.updateStatus("Precise location permission was removed", gpsRetryCount)
            updateNotification("GPS permission removed — open GMODE settings")
            scheduleGpsWatchdog(GpsRecoveryPolicy.REQUEST_FAILURE_RETRY_MS)
            return
        }
        if (!locationManager.isLocationEnabled) {
            diagnostics.updateStatus("Phone location is off — waiting and retrying", gpsRetryCount)
            updateNotification("Phone location is off — retrying automatically")
            scheduleGpsWatchdog(GpsRecoveryPolicy.REQUEST_FAILURE_RETRY_MS)
            return
        }
        fusedLocation.removeLocationUpdates(locationCallback).addOnCompleteListener {
            if (!tracking) return@addOnCompleteListener
            fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    diagnostics.updateStatus(waitingMessage, gpsRetryCount)
                    updateNotification(waitingMessage)
                    if (!stationaryPaused) scheduleGpsWatchdog()
                }
                .addOnFailureListener { error ->
                    gpsRetryCount += 1
                    val message = "GPS request failed — retry $gpsRetryCount: ${error.message ?: error.javaClass.simpleName}"
                    diagnostics.updateStatus(message, gpsRetryCount)
                    updateNotification("GPS request failed — retrying automatically")
                    scheduleGpsWatchdog(GpsRecoveryPolicy.REQUEST_FAILURE_RETRY_MS)
                }
        }
    }

    private fun checkGpsHealth() {
        if (!tracking) return
        if (stationaryPaused) {
            requestLocationUpdates("Trip paused — movement watch retrying")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (GpsRecoveryPolicy.fixIsStale(now, lastFixElapsedRealtimeMs)) {
            gpsRetryCount += 1
            val message = if (locationManager.isLocationEnabled) {
                "No GPS fix after 45 seconds — retry $gpsRetryCount"
            } else {
                "Phone location is off — retry $gpsRetryCount"
            }
            diagnostics.updateStatus(message, gpsRetryCount)
            updateNotification("$message • recording remains active")
            requestLocationUpdates(message)
        } else {
            scheduleGpsWatchdog()
        }
    }

    private fun scheduleGpsWatchdog(delayMs: Long? = null) {
        mainHandler.removeCallbacks(gpsWatchdog)
        if (!tracking) return
        if (stationaryPaused) {
            delayMs?.let { mainHandler.postDelayed(gpsWatchdog, it) }
            return
        }
        val now = SystemClock.elapsedRealtime()
        val computedDelay = if (GpsRecoveryPolicy.fixIsStale(now, lastFixElapsedRealtimeMs)) {
            GpsRecoveryPolicy.FIX_TIMEOUT_MS
        } else {
            GpsRecoveryPolicy.nextWatchdogDelayMs(now, lastFixElapsedRealtimeMs)
        }
        mainHandler.postDelayed(gpsWatchdog, delayMs ?: computedDelay)
    }

    private fun stopTripAndService() {
        lifecycleScope.launch {
            val stoppedTripId = currentTripId
            repository.stopTrip()
            AutoRecordingStateStore(this@TrackingService).let { state ->
                if (state.activeAutoTripId == stoppedTripId) {
                    state.activeAutoTripId = null
                    ReturnDwellWorker.cancel(this@TrackingService)
                    state.updateStatus("Automatic trip stopped manually — waiting for the next departure")
                } else if (state.returnDwellTripId == stoppedTripId) {
                    state.clearReturnDwell()
                    ReturnDwellWorker.cancel(this@TrackingService)
                    state.updateStatus("Manual trip stopped — waiting for the next departure")
                }
            }
            SyncScheduler.enqueue(this@TrackingService)
            stopTrackingResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTrackingResources() {
        mainHandler.removeCallbacks(gpsWatchdog)
        if (!tracking) return
        tracking = false
        fusedLocation.removeLocationUpdates(locationCallback)
        sensors.stop()
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        currentTripId = null
        locationRequest = null
        lastFixElapsedRealtimeMs = null
        automaticTrip = false
        stationaryPaused = false
        stationaryPauseTracker = null
        diagnostics.updateStatus("Trip stopped — GPS standby", 0)
    }

    private fun phoneSnapshot(): PhoneSnapshot {
        val batteryManager = getSystemService(BatteryManager::class.java)
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
            ?.toDouble()
        val charging = batteryManager.isCharging
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val networkType = when {
            capabilities == null -> "offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return PhoneSnapshot(batteryPercent, charging, networkType, satelliteCount)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when GPS and telemetry are being recorded"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop trip", stopIntent).build())
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    companion object {
        private const val CHANNEL_ID = "gmode_trip_recording"
        private const val NOTIFICATION_ID = 2101
        private const val ACTION_START = "ca.gmode.triprecorder.START"
        private const val ACTION_STOP = "ca.gmode.triprecorder.STOP"
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val PAUSED_LOCATION_INTERVAL_MS = 15_000L
        private const val PAUSED_MIN_UPDATE_INTERVAL_MS = 10_000L
        private const val PAUSED_MAX_UPDATE_DELAY_MS = 60_000L
        private const val PAUSE_END_ANCHOR_OFFSET_MS = 1_000L
        private val EMPTY_SENSOR_SNAPSHOT = SensorSnapshot(
            pressureHpa = null,
            accelerationRmsMs2 = null,
            accelerationPeakMs2 = null,
            gyroscopePeakRadS = null,
        )

        fun start(context: Context, tripId: String) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TRIP_ID, tripId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }

        fun stopImmediately(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
