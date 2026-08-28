package ca.gmode.triprecorder.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Live GNSS/location telemetry while the app is visible; never persists trip points. */
class ForegroundLocationMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val fusedLocation: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager: LocationManager = appContext.getSystemService(LocationManager::class.java)

    @Volatile
    private var latest = LiveTelemetry()
    private var started = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.lastOrNull()?.let(::updateLocation)
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            latest = latest.copy(
                satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) },
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    fun snapshot(): LiveTelemetry = latest

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (started || !hasFineLocation()) return started
        latest = LiveTelemetry()
        started = true
        runCatching {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(LOCATION_INTERVAL_MS)
            .build()
        fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        return true
    }

    fun stop() {
        if (!started) return
        started = false
        fusedLocation.removeLocationUpdates(locationCallback)
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
    }

    private fun updateLocation(location: Location) {
        latest = latest.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKph = location.speed.takeIf { location.hasSpeed() }?.times(3.6),
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            bearingDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LOCATION_INTERVAL_MS = 5_000L
        const val MIN_LOCATION_INTERVAL_MS = 2_000L
    }
}
