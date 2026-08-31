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

object StationaryAutoResumePolicy {
    fun shouldResume(
        sample: StationaryLocationSample,
        pausedLatitude: Double,
        pausedLongitude: Double,
        stationarySpeedMps: Double,
        stationaryRadiusMeters: Double,
        minimumMovementMeters: Double,
    ): Boolean {
        val accuracyLimit = max(50.0, stationaryRadiusMeters / 2.0)
        if (sample.accuracyMeters != null && sample.accuracyMeters > accuracyLimit) return false
        val distance = distanceMeters(
            pausedLatitude,
            pausedLongitude,
            sample.latitude,
            sample.longitude,
        )
        val movingDistance = max(15.0, min(50.0, max(minimumMovementMeters * 2.0, stationaryRadiusMeters / 3.0)))
        val movingBySpeed = sample.speedMps != null && sample.speedMps > stationarySpeedMps
        return distance >= stationaryRadiusMeters || movingBySpeed && distance >= movingDistance
    }
}
