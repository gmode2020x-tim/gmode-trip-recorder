package ca.gmode.triprecorder

import ca.gmode.triprecorder.settings.AutoRecordingConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityMessagesTest {
    @Test
    fun trimmingConfirmationDistinguishesStationaryAndHomeRadii() {
        val config = AutoRecordingConfig(
            enabled = true,
            homeLatitude = 43.0,
            homeLongitude = -80.0,
            homeRadiusMeters = 250,
            stationaryRadiusMeters = 150,
        )

        assertEquals(
            "Trimming saved — stationary radius: 150 m. Auto-start home radius: 250 m.",
            trimmingSettingsSavedMessage(config),
        )
    }

    @Test
    fun trimmingConfirmationOmitsHomeRadiusWhenHomeDetectionIsOff() {
        val config = AutoRecordingConfig(
            enabled = false,
            stationaryRadiusMeters = 150,
            stopManualTripsAtHome = false,
        )

        assertEquals(
            "Trimming saved — stationary radius: 150 m.",
            trimmingSettingsSavedMessage(config),
        )
    }

    @Test
    fun trimmingConfirmationKeepsHomeDetectionFailureVisible() {
        val config = AutoRecordingConfig(
            enabled = true,
            homeRadiusMeters = 250,
            stationaryRadiusMeters = 150,
        )

        assertEquals(
            "Trimming saved — stationary radius: 150 m. Auto-start home radius: 250 m. " +
                "Home detection: Precise location permission is required",
            trimmingSettingsSavedMessage(config, "Precise location permission is required"),
        )
    }
}
