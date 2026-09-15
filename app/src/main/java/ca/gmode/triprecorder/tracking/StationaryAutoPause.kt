package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.distanceMeters
import kotlin.math.max
import kotlin.math.min

data class StationaryLocationSample(
    val elapsedRealtimeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double?,
    val accuracyMeters: Double?,
)

class StationaryAutoPauseTracker(
    private val radiusMeters: Double,
    private val pauseMillis: Long,
    private val stationarySpeedMps: Double,
) {
    private var candidateStartedAtMs: Long? = null
    private var anchorLatitude: Double? = null
    private var anchorLongitude: Double? = null

    fun observe(sample: StationaryLocationSample): Boolean {
        if (sample.accuracyMeters != null && sample.accuracyMeters > radiusMeters) {
            reset()
            return false
        }
        val slow = sample.speedMps == null || sample.speedMps <= stationarySpeedMps
        if (!slow) {
            reset()
            return false
        }
        val started = candidateStartedAtMs
        val latitude = anchorLatitude
        val longitude = anchorLongitude
        if (started == null || latitude == null || longitude == null) {
            candidateStartedAtMs = sample.elapsedRealtimeMs
            anchorLatitude = sample.latitude
            anchorLongitude = sample.longitude
            return false
        }
        if (distanceMeters(latitude, longitude, sample.latitude, sample.longitude) > radiusMeters) {
            candidateStartedAtMs = sample.elapsedRealtimeMs
            anchorLatitude = sample.latitude
            anchorLongitude = sample.longitude
            return false
        }
        return sample.elapsedRealtimeMs - started >= pauseMillis
    }

    fun reset() {
        candidateStartedAtMs = null
        anchorLatitude = null
        anchorLongitude = null
    }
}

class StationaryAutoResumeTracker(
    private val pausedLatitude: Double,
    private val pausedLongitude: Double,
    stationarySpeedMps: Double,
    private val stationaryRadiusMeters: Double,
    private val minimumMovementMeters: Double,
    private val confirmationMillis: Long = 30_000L,
    private val accuracyLimitMeters: Double = 30.0,
) {
    private val minimumResumeSpeedMps = max(stationarySpeedMps, 5.0 / 3.6)
    private var candidateStartedAtMs: Long? = null
    private var candidateLatitude: Double? = null
    private var candidateLongitude: Double? = null

    fun observe(sample: StationaryLocationSample): Boolean {
        val accuracy = sample.accuracyMeters
        if (accuracy == null || accuracy > accuracyLimitMeters) {
            reset()
            return false
        }
        val distanceFromPause = distanceMeters(
            pausedLatitude,
            pausedLongitude,
            sample.latitude,
            sample.longitude,
        )
        val movingDistance = max(
            15.0,
            min(50.0, max(minimumMovementMeters * 2.0, stationaryRadiusMeters / 3.0)),
        )
        val movingBySpeed = (sample.speedMps ?: 0.0) >= minimumResumeSpeedMps
        if (distanceFromPause < stationaryRadiusMeters && !(movingBySpeed && distanceFromPause >= movingDistance)) {
            reset()
            return false
        }

        val started = candidateStartedAtMs
        val latitude = candidateLatitude
        val longitude = candidateLongitude
        if (started == null || latitude == null || longitude == null) {
            candidateStartedAtMs = sample.elapsedRealtimeMs
            candidateLatitude = sample.latitude
            candidateLongitude = sample.longitude
            return false
        }
        val confirmedProgress = distanceMeters(latitude, longitude, sample.latitude, sample.longitude)
        val requiredProgress = max(10.0, minimumMovementMeters * 2.0)
        return sample.elapsedRealtimeMs - started >= confirmationMillis && confirmedProgress >= requiredProgress
    }

    fun reset() {
        candidateStartedAtMs = null
        candidateLatitude = null
        candidateLongitude = null
    }
}
