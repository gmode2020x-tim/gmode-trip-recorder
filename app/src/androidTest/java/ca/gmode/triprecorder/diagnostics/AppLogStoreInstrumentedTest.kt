package ca.gmode.triprecorder.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
}
