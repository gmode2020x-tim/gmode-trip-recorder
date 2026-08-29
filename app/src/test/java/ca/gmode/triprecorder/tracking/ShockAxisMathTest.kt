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

    @Test
    fun standardGravityConvertsToOneGWithoutChangingDirection() {
        val acceleration = VehicleShockVector(
            forwardMs2 = GForceMath.STANDARD_GRAVITY_MS2,
            rightMs2 = -GForceMath.STANDARD_GRAVITY_MS2 / 2.0,
            upMs2 = 0.0,
        )

        val gForce = GForceMath.fromLinearAcceleration(acceleration)

        assertEquals(1.0, gForce.forwardG, 0.000001)
        assertEquals(-0.5, gForce.rightG, 0.000001)
        assertEquals(0.0, gForce.upG, 0.000001)
        assertEquals(kotlin.math.sqrt(1.25), gForce.magnitudeG, 0.000001)
    }

    @Test
    fun displayProgressUsesSignedThreeGScaleAndClampsOnlyTheDrawing() {
        assertEquals(-1.0, GForceMath.axisProgress(-4.0), 0.0)
        assertEquals(-0.5, GForceMath.axisProgress(-1.5), 0.0)
        assertEquals(0.0, GForceMath.axisProgress(0.0), 0.0)
        assertEquals(0.5, GForceMath.axisProgress(1.5), 0.0)
        assertEquals(1.0, GForceMath.axisProgress(4.0), 0.0)
    }
}
