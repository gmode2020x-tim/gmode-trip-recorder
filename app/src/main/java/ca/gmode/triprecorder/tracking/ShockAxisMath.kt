package ca.gmode.triprecorder.tracking

import kotlin.math.sqrt

/** Vehicle-relative linear acceleration for the required back-facing-forward phone mount. */
data class VehicleShockVector(
    val forwardMs2: Double,
    val rightMs2: Double,
    val upMs2: Double,
) {
    val magnitudeMs2: Double
        get() = sqrt(forwardMs2 * forwardMs2 + rightMs2 * rightMs2 + upMs2 * upMs2)
}

object ShockAxisMath {
    /**
     * Converts Android device coordinates into vehicle coordinates after accounting for the
     * display rotation. The phone screen faces the occupants and its back always faces forward.
     */
    fun toVehicleAxes(
        rawXMs2: Double,
        rawYMs2: Double,
        rawZMs2: Double,
        displayRotation: Int,
    ): VehicleShockVector {
        val (screenX, screenY) = when (displayRotation) {
            ROTATION_90 -> rawYMs2 to -rawXMs2
            ROTATION_180 -> -rawXMs2 to -rawYMs2
            ROTATION_270 -> -rawYMs2 to rawXMs2
            else -> rawXMs2 to rawYMs2
        }
        return VehicleShockVector(
            forwardMs2 = -rawZMs2,
            rightMs2 = screenX,
            upMs2 = screenY,
        )
    }

    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3
}
