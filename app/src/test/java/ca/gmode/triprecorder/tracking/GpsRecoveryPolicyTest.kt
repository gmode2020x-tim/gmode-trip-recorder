package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRecoveryPolicyTest {
    @Test
    fun missingOrExpiredFixTriggersRecovery() {
        assertTrue(GpsRecoveryPolicy.fixIsStale(50_000L, null))
        assertTrue(GpsRecoveryPolicy.fixIsStale(50_000L, 5_000L))
        assertFalse(GpsRecoveryPolicy.fixIsStale(50_000L, 6_000L))
    }

    @Test
    fun watchdogWaitsOnlyForRemainingFreshFixWindow() {
        assertEquals(45_000L, GpsRecoveryPolicy.nextWatchdogDelayMs(50_000L, null))
        assertEquals(30_000L, GpsRecoveryPolicy.nextWatchdogDelayMs(50_000L, 35_000L))
        assertEquals(1_000L, GpsRecoveryPolicy.nextWatchdogDelayMs(50_000L, 1_000L))
    }
}
