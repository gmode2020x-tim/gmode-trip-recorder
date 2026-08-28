package ca.gmode.triprecorder.tracking

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

data class GpsSkyOffset(val x: Float, val y: Float)

enum class GnssSignalQuality { WEAK, FAIR, STRONG }

object GpsSkyMath {
    /** Projects a satellite onto a north-up sky plot. Zenith is the centre; horizon is the rim. */
    fun project(azimuthDegrees: Float, elevationDegrees: Float, radius: Float): GpsSkyOffset {
        val elevation = elevationDegrees.coerceIn(0f, 90f)
        val distance = radius * (90f - elevation) / 90f
        val azimuthRadians = Math.toRadians(azimuthDegrees.toDouble())
        return GpsSkyOffset(
            x = (sin(azimuthRadians) * distance).toFloat(),
            y = (-cos(azimuthRadians) * distance).toFloat(),
        )
    }

    fun signalQuality(cn0DbHz: Float): GnssSignalQuality = when {
        cn0DbHz >= 35f -> GnssSignalQuality.STRONG
        cn0DbHz >= 25f -> GnssSignalQuality.FAIR
        else -> GnssSignalQuality.WEAK
    }

    /** Log scaling keeps both precise and poor fixes visible inside the compact cockpit dial. */
    fun accuracyRadius(accuracyMeters: Double?, maximumRadius: Float): Float {
        if (accuracyMeters == null || !accuracyMeters.isFinite()) return 0f
        val normalized = (ln(1.0 + accuracyMeters.coerceIn(0.0, 100.0)) / ln(101.0)).toFloat()
        return maximumRadius * (0.18f + 0.82f * normalized)
    }
}
