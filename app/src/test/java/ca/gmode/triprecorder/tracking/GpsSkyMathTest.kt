package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsSkyMathTest {
    @Test
    fun northHorizonProjectsToTopAndZenithProjectsToCenter() {
        val north = GpsSkyMath.project(0f, 0f, 100f)
        val zenith = GpsSkyMath.project(240f, 90f, 100f)

        assertEquals(0f, north.x, 0.001f)
        assertEquals(-100f, north.y, 0.001f)
        assertEquals(0f, zenith.x, 0.001f)
        assertEquals(0f, zenith.y, 0.001f)
    }

    @Test
    fun eastSatelliteProjectsRightAndSignalBandsAreStable() {
        val east = GpsSkyMath.project(90f, 45f, 100f)

        assertEquals(50f, east.x, 0.001f)
        assertEquals(0f, east.y, 0.001f)
        assertEquals(GnssSignalQuality.WEAK, GpsSkyMath.signalQuality(24.9f))
        assertEquals(GnssSignalQuality.FAIR, GpsSkyMath.signalQuality(25f))
        assertEquals(GnssSignalQuality.STRONG, GpsSkyMath.signalQuality(35f))
    }

    @Test
    fun accuracyRingGrowsForLessCertainFixesAndStaysBounded() {
        val precise = GpsSkyMath.accuracyRadius(3.0, 50f)
        val poor = GpsSkyMath.accuracyRadius(80.0, 50f)

        assertTrue(precise > 0f)
        assertTrue(poor > precise)
        assertTrue(poor <= 50f)
        assertEquals(0f, GpsSkyMath.accuracyRadius(null, 50f), 0f)
    }
}
