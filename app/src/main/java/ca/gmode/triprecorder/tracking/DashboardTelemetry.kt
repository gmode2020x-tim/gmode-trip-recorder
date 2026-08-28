package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.SensorSnapshot

object DashboardTelemetry {
    fun merge(
        stored: LiveTelemetry,
        activeTripId: String?,
        foregroundLocation: LiveTelemetry? = null,
        sensors: SensorSnapshot?,
        orientation: OrientationSnapshot,
        batteryPercent: Int?,
    ): LiveTelemetry {
        val storedIsCurrent = activeTripId != null && stored.tripId == activeTripId
        val base = if (storedIsCurrent) stored else foregroundLocation ?: LiveTelemetry()
        return base.copy(
            satelliteCount = foregroundLocation?.satelliteCount ?: base.satelliteCount,
            gnssSatellites = foregroundLocation?.gnssSatellites.orEmpty().ifEmpty { base.gnssSatellites },
            pressureHpa = sensors?.pressureHpa ?: base.pressureHpa.takeIf { storedIsCurrent },
            accelerationPeakMs2 = sensors?.accelerationPeakMs2
                ?: base.accelerationPeakMs2.takeIf { storedIsCurrent },
            batteryPercent = batteryPercent?.toDouble() ?: base.batteryPercent.takeIf { storedIsCurrent },
            pitchDegrees = orientation.pitchDegrees ?: base.pitchDegrees.takeIf { storedIsCurrent },
            rollDegrees = orientation.rollDegrees ?: base.rollDegrees.takeIf { storedIsCurrent },
            magneticHeadingDegrees = orientation.magneticHeadingDegrees,
        )
    }
}
