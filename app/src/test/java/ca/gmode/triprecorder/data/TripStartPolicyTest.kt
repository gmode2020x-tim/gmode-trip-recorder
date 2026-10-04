package ca.gmode.triprecorder.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStartPolicyTest {
    @Test
    fun sameTypeKeepsTheCurrentTrip() {
        assertFalse(tripTypesDiffer("off_road", "Off road"))
        assertFalse(tripTypesDiffer("street", " street "))
    }

    @Test
    fun selectingSxsTypeClosesAStreetTripBeforeStarting() {
        assertTrue(tripTypesDiffer("street", "off_road"))
    }
}
