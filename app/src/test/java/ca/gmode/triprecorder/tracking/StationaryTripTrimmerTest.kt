package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.PointEntity
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StationaryTripTrimmerTest {
    @Test
    fun longStopBecomesTwoLegsWhileRawPointsRemainAvailable() {
        val points = listOf(
            point(0, 0, 43.0000, -80.0000, 12.0),
            point(1, 1, 43.0100, -80.0000, 12.0),
            point(2, 2, 43.0200, -80.0000, 0.2),
            point(3, 7, 43.0201, -80.0000, 0.0),
            point(4, 12, 43.0200, -80.0001, 0.1),
            point(5, 17, 43.0201, -80.0001, 0.0),
            point(6, 18, 43.0300, -80.0000, 12.0),
            point(7, 19, 43.0400, -80.0000, 12.0),
        )
        val result = StationaryTripTrimmer.trim(
            points,
            AutoRecordingConfig(
                stationaryTrimEnabled = true,
                stationaryRadiusMeters = 150,
                stationaryPauseMinutes = 3,
                stationarySplitMinutes = 15,
                stationarySpeedKmh = 5.4,
            ),
        )

        assertEquals(8, points.size)
        assertEquals(2, result.segments.size)
        assertEquals(1, result.stationaryPeriods.size)
        assertEquals(15 * 60_000L, result.stationaryPeriods.single().durationMillis)
        assertEquals(4 * 60_000L, result.movingDurationMillis)
        assertTrue(result.points.size < points.size)
        assertTrue(result.distanceMeters > 4_000)
    }

    @Test
    fun disabledTrimmingReturnsEveryPointInOneSegment() {
        val points = listOf(
            point(0, 0, 43.0, -80.0, 0.0),
            point(1, 10, 43.0, -80.0, 0.0),
            point(2, 20, 43.0, -80.0, 0.0),
        )

        val result = StationaryTripTrimmer.trim(
            points,
            AutoRecordingConfig(stationaryTrimEnabled = false),
        )

        assertEquals(listOf(points), result.segments)
        assertEquals(20 * 60_000L, result.movingDurationMillis)
        assertTrue(result.stationaryPeriods.isEmpty())
    }

    private fun point(
        sequence: Long,
        minute: Long,
        latitude: Double,
        longitude: Double,
        speedMps: Double,
    ) = PointEntity(
        id = "trip:$sequence",
        tripId = "trip",
        sequence = sequence,
        recordedAt = Instant.parse("2026-08-29T14:00:00Z").plusSeconds(minute * 60).toString(),
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5.0,
        altitudeMeters = 250.0,
        verticalAccuracyMeters = 2.0,
        speedMps = speedMps,
        bearingDegrees = 0.0,
        pressureHpa = null,
        accelerationRmsMs2 = null,
        accelerationPeakMs2 = null,
        gyroscopePeakRadS = null,
        batteryPercent = 80.0,
        isCharging = false,
        networkType = "offline",
        satelliteCount = 20,
    )
}
