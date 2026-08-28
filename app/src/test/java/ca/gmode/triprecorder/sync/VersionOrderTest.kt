package ca.gmode.triprecorder.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionOrderTest {
    @Test
    fun semanticVersionsCompareByNumericComponent() {
        assertTrue(VersionOrder.isNewer("2.1.0", "2.0.1"))
        assertTrue(VersionOrder.isNewer("v2.10.0", "2.9.9"))
        assertFalse(VersionOrder.isNewer("2.0.1", "2.0.1"))
        assertFalse(VersionOrder.isNewer("1.13.0", "2.0.1"))
    }
}
