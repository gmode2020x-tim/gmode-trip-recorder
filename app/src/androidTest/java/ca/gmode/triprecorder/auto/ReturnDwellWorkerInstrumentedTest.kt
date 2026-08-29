package ca.gmode.triprecorder.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReturnDwellWorkerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("auto_recording_state", Context.MODE_PRIVATE).edit().clear().commit()
        database = AppDatabase.get(context)
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    @Test
    fun expiredPersistedDeadlineStopsTripWithoutAnotherGpsCallback() = runBlocking {
        val repository = RecordingRepository(database.tripDao())
        val trip = repository.startTrip("Automatic deadline test", "street")
        val state = AutoRecordingStateStore(context)
        state.activeAutoTripId = trip.id
        state.beginReturnDwell(1, System.currentTimeMillis() - 60_001L)

        val result = TestListenableWorkerBuilder<ReturnDwellWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
        assertNull(repository.activeTrip())
        assertNull(state.activeAutoTripId)
        assertNull(state.returnDwellDeadlineEpochMs)
        assertTrue(state.status().contains("Returned home"))
    }
}
