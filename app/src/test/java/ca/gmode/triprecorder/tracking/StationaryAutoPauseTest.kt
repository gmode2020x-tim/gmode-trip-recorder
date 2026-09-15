package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationaryAutoPauseTest {
    @Test
    fun pausesAfterConfiguredSlowStationaryPeriod() {
        val tracker = StationaryAutoPauseTracker(150.0, 3 * 60_000L, 1.0 / 3.6)

        assertFalse(tracker.observe(sample(0, 43.0, -80.0, 0.0)))
        assertFalse(tracker.observe(sample(120_000, 43.0001, -80.0, 0.0)))
        assertTrue(tracker.observe(sample(180_000, 43.0001, -80.0001, 0.0)))
    }

    @Test
    fun movementOrPoorAccuracyPreventsPause() {
        val tracker = StationaryAutoPauseTracker(150.0, 3 * 60_000L, 1.0 / 3.6)

        assertFalse(tracker.observe(sample(0, 43.0, -80.0, 0.0)))
        assertFalse(tracker.observe(sample(200_000, 43.0, -80.0, 4.0)))
        assertFalse(tracker.observe(sample(400_000, 43.0, -80.0, 0.0, accuracy = 200.0)))
    }

    @Test
    fun resumeRequiresAccurateSustainedMovement() {
        val tracker = resumeTracker()

        assertFalse(tracker.observe(sample(0, 43.0005, -80.0, 4.0, accuracy = 100.0)))
        assertFalse(tracker.observe(sample(15_000, 43.0005, -80.0, 4.0)))
        assertFalse(tracker.observe(sample(30_000, 43.00055, -80.0, 4.0)))
        assertTrue(tracker.observe(sample(45_000, 43.00065, -80.0, 4.0)))
    }

    @Test
    fun staticAccurateJumpDoesNotResume() {
        val tracker = resumeTracker()

        assertFalse(tracker.observe(sample(0, 43.0015, -80.0, 0.0)))
        assertFalse(tracker.observe(sample(30_000, 43.0015, -80.0, 0.0)))
        assertFalse(tracker.observe(sample(60_000, 43.0015, -80.0, 0.0)))
    }

    @Test
    fun missingAccuracyResetsResumeConfirmation() {
        val tracker = resumeTracker()

        assertFalse(tracker.observe(sample(0, 43.0005, -80.0, 4.0)))
        assertFalse(tracker.observe(sample(15_000, 43.00055, -80.0, 4.0, accuracy = null)))
        assertFalse(tracker.observe(sample(45_000, 43.00065, -80.0, 4.0)))
    }

    private fun resumeTracker() = StationaryAutoResumeTracker(
        pausedLatitude = 43.0,
        pausedLongitude = -80.0,
        stationarySpeedMps = 1.0 / 3.6,
        stationaryRadiusMeters = 150.0,
        minimumMovementMeters = 5.0,
    )

    private fun sample(
        elapsedMs: Long,
        latitude: Double,
        longitude: Double,
        speedMps: Double?,
        accuracy: Double? = 3.0,
    ) = StationaryLocationSample(
        elapsedRealtimeMs = elapsedMs,
        latitude = latitude,
        longitude = longitude,
        speedMps = speedMps,
        accuracyMeters = accuracy,
    )
}
