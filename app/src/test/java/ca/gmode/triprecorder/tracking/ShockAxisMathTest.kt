package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class ShockAxisMathTest {
    @Test
    fun portraitDeviceAxesMapToVehicleAxes() {
        val vector = ShockAxisMath.toVehicleAxes(1.0, 2.0, 3.0, ShockAxisMath.ROTATION_0)

        assertEquals(-3.0, vector.forwardMs2, 0.0)
        assertEquals(1.0, vector.rightMs2, 0.0)
        assertEquals(2.0, vector.upMs2, 0.0)
        assertEquals(kotlin.math.sqrt(14.0), vector.magnitudeMs2, 0.0001)
    }

    @Test
    fun landscapeClockwiseRemapsScreenAxesWithoutChangingMagnitude() {
        val vector = ShockAxisMath.toVehicleAxes(1.0, 2.0, 3.0, ShockAxisMath.ROTATION_90)

        assertEquals(-3.0, vector.forwardMs2, 0.0)
        assertEquals(2.0, vector.rightMs2, 0.0)
        assertEquals(-1.0, vector.upMs2, 0.0)
        assertEquals(kotlin.math.sqrt(14.0), vector.magnitudeMs2, 0.0001)
    }

    @Test
    fun landscapeCounterClockwiseRemapsScreenAxesWithoutChangingMagnitude() {
        val vector = ShockAxisMath.toVehicleAxes(1.0, 2.0, 3.0, ShockAxisMath.ROTATION_270)

        assertEquals(-3.0, vector.forwardMs2, 0.0)
        assertEquals(-2.0, vector.rightMs2, 0.0)
        assertEquals(1.0, vector.upMs2, 0.0)
        assertEquals(kotlin.math.sqrt(14.0), vector.magnitudeMs2, 0.0001)
    }

    @Test
    fun upsideDownDisplayRemapsScreenAxesWithoutChangingMagnitude() {
        val vector = ShockAxisMath.toVehicleAxes(1.0, 2.0, 3.0, ShockAxisMath.ROTATION_180)

        assertEquals(-3.0, vector.forwardMs2, 0.0)
        assertEquals(-1.0, vector.rightMs2, 0.0)
        assertEquals(-2.0, vector.upMs2, 0.0)
        assertEquals(kotlin.math.sqrt(14.0), vector.magnitudeMs2, 0.0001)
    }
}
