package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.SensorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTelemetryTest {
    @Test
    fun foregroundLocationRemainsLiveBeforeATripStarts() {
        val foreground = LiveTelemetry(
            latitude = 43.041,
            longitude = -80.872,
            speedKph = 4.2,
            altitudeMeters = 271.0,
            bearingDegrees = 135.0,
            accuracyMeters = 6.0,
            satelliteCount = 11,
        )
        val merged = DashboardTelemetry.merge(
            stored = LiveTelemetry(tripId = "old", latitude = 1.0, satelliteCount = 1),
            activeTripId = null,
            foregroundLocation = foreground,
            sensors = null,
            orientation = OrientationSnapshot(null, null),
            batteryPercent = null,
        )

        assertEquals(43.041, merged.latitude!!, 0.001)
        assertEquals(-80.872, merged.longitude!!, 0.001)
        assertEquals(4.2, merged.speedKph!!, 0.001)
        assertEquals(271.0, merged.altitudeMeters!!, 0.001)
        assertEquals(135.0, merged.bearingDegrees!!, 0.001)
        assertEquals(6.0, merged.accuracyMeters!!, 0.001)
        assertEquals(11, merged.satelliteCount)
    }

    @Test
    fun foregroundSatelliteGeometryRemainsAvailableDuringATrip() {
        val satellites = listOf(
            GnssSatelliteObservation(7, 1, 90f, 45f, 38f, true),
            GnssSatelliteObservation(12, 6, 210f, 20f, 24f, false),
        )
        val merged = DashboardTelemetry.merge(
            stored = LiveTelemetry(tripId = "active", satelliteCount = 1),
            activeTripId = "active",
            foregroundLocation = LiveTelemetry(satelliteCount = 1, gnssSatellites = satellites),
            sensors = null,
            orientation = OrientationSnapshot(null, null),
            batteryPercent = null,
        )

        assertEquals(satellites, merged.gnssSatellites)
        assertEquals(1, merged.satelliteCount)
    }

    @Test
    fun foregroundPhoneSensorsRemainLiveBeforeATripStarts() {
        val merged = DashboardTelemetry.merge(
            stored = LiveTelemetry(tripId = "old", pitchDegrees = 99.0, pressureHpa = 800.0),
            activeTripId = null,
            sensors = SensorSnapshot(
                pressureHpa = 1007.5,
                accelerationRmsMs2 = 0.2,
                accelerationPeakMs2 = 1.4,
                gyroscopePeakRadS = 0.1,
                accelerationPeakXMs2 = 0.4,
                accelerationPeakYMs2 = -0.8,
                accelerationPeakZMs2 = 1.1,
            ),
            orientation = OrientationSnapshot(pitchDegrees = 6.5, rollDegrees = -3.0, magneticHeadingDegrees = 318.0),
            batteryPercent = 74,
        )

        assertEquals(6.5, merged.pitchDegrees!!, 0.001)
        assertEquals(-3.0, merged.rollDegrees!!, 0.001)
        assertEquals(1007.5, merged.pressureHpa!!, 0.001)
        assertEquals(1.4, merged.accelerationPeakMs2!!, 0.001)
        assertEquals(0.4, merged.accelerationPeakXMs2!!, 0.001)
        assertEquals(-0.8, merged.accelerationPeakYMs2!!, 0.001)
        assertEquals(1.1, merged.accelerationPeakZMs2!!, 0.001)
        assertEquals(74.0, merged.batteryPercent!!, 0.001)
        assertEquals(318.0, merged.magneticHeadingDegrees!!, 0.001)
    }

    @Test
    fun oldTripSensorValuesAreNotShownAsCurrentWhenPhoneHasNoSample() {
        val merged = DashboardTelemetry.merge(
            stored = LiveTelemetry(
                tripId = "old",
                pitchDegrees = 12.0,
                rollDegrees = -8.0,
                pressureHpa = 998.0,
                accelerationPeakMs2 = 4.0,
            ),
            activeTripId = null,
            sensors = null,
            orientation = OrientationSnapshot(null, null),
            batteryPercent = null,
        )

        assertNull(merged.pitchDegrees)
        assertNull(merged.rollDegrees)
        assertNull(merged.pressureHpa)
        assertNull(merged.accelerationPeakMs2)
    }

    @Test
    fun activeTripValuesBridgeShortGapsBetweenForegroundSamples() {
        val stored = LiveTelemetry(
            tripId = "active",
            pitchDegrees = 2.0,
            rollDegrees = 3.0,
            pressureHpa = 1001.0,
        )
        val merged = DashboardTelemetry.merge(
            stored = stored,
            activeTripId = "active",
            sensors = null,
            orientation = OrientationSnapshot(null, null),
            batteryPercent = null,
        )

        assertEquals(2.0, merged.pitchDegrees!!, 0.001)
        assertEquals(3.0, merged.rollDegrees!!, 0.001)
        assertEquals(1001.0, merged.pressureHpa!!, 0.001)
    }
}
