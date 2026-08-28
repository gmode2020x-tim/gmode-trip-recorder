package ca.gmode.triprecorder.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingDiagnosticStoreInstrumentedTest {
    @Test
    fun retryAndFixStateRemainVisibleAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tracking_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()

        val store = TrackingDiagnosticStore(context)
        store.reset("Starting high-accuracy GPS")
        assertNull(store.read().lastFixAtEpochMillis)

        store.updateStatus("No GPS fix after 45 seconds — retry 1", retryCount = 1)
        assertEquals(1, TrackingDiagnosticStore(context).read().retryCount)

        store.markFix(accuracyMeters = 3.8f)
        val fixed = TrackingDiagnosticStore(context).read()
        assertEquals(0, fixed.retryCount)
        assertEquals("GPS fix received ±3 m", fixed.status)
        assertNotNull(fixed.lastFixAtEpochMillis)
    }
}
