package ca.gmode.triprecorder.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gmode.triprecorder.sync.SyncStatusStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLogStoreInstrumentedTest {
    @Test
    fun eventLogIsBoundedAndPreservesNewestEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_diagnostic_log", Context.MODE_PRIVATE).edit().clear().commit()
        val store = AppLogStore(context)

        repeat(105) { index -> store.append("test", "event", "message $index") }

        val entries = store.recent(100)
        assertEquals(100, entries.size)
        assertTrue(entries.first().message.endsWith("5"))
        assertTrue(entries.last().message.endsWith("104"))
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
    }

    @Test
    fun identicalEventsCanBeCoalescedWithoutHidingDifferentEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_diagnostic_log", Context.MODE_PRIVATE).edit().clear().commit()
        val store = AppLogStore(context)

        assertTrue(store.appendCoalesced("sync", "Up to date", "Complete", 60_000L))
        store.append("gps", "retry", "Waiting for a fix")
        assertFalse(store.appendCoalesced("sync", "Up to date", "Complete", 60_000L))

        val entries = store.recent()
        assertEquals(2, entries.size)
        assertEquals(listOf("sync", "gps"), entries.map { it.category })
    }

    @Test
    fun routineSyncCyclesLogOneSuccessButRecoveryRemainsVisible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_diagnostic_log", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sync_status", Context.MODE_PRIVATE).edit().clear().commit()
        val status = SyncStatusStore(context)

        repeat(20) {
            status.update("Synchronizing", "Uploading locally saved trip data.")
            status.update("Up to date", "All recorded points and diagnostics are stored in Home Assistant.")
        }
        assertEquals(1, AppLogStore(context).recent().count { it.category == "sync" })

        status.update("Waiting for connection", "Home Assistant is unreachable.")
        status.update("Synchronizing", "Uploading locally saved trip data.")
        status.update("Up to date", "All recorded points and diagnostics are stored in Home Assistant.")

        val syncEvents = AppLogStore(context).recent().filter { it.category == "sync" }
        assertEquals(listOf("Up to date", "Waiting for connection", "Up to date"), syncEvents.map { it.state })
    }
}
