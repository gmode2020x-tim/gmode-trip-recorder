package ca.gmode.triprecorder.auto

import android.content.Context
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import ca.gmode.triprecorder.sync.SyncScheduler
import ca.gmode.triprecorder.tracking.TrackingService
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AutoTripController(
    private val context: Context,
    private val repository: RecordingRepository = RecordingRepository(AppDatabase.get(context).tripDao()),
    private val settings: AutoRecordingSettings = AutoRecordingSettings(context),
    private val state: AutoRecordingStateStore = AutoRecordingStateStore(context),
    private val startTracking: (Context, String) -> Unit = TrackingService::start,
    private val stopTracking: (Context) -> Unit = TrackingService::stopImmediately,
    private val enqueueSync: (Context) -> Unit = SyncScheduler::enqueue,
    private val scheduleReturnCheck: (Context, Long) -> Unit = ReturnDwellWorker::scheduleAt,
    private val cancelReturnCheck: (Context) -> Unit = ReturnDwellWorker::cancel,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun handleExit(): Boolean {
        state.clearReturnDwell()
        cancelReturnCheck(context)
        val config = settings.read()
        if (!config.enabled || !config.hasHomeLocation) return false
        repository.activeTrip()?.let {
            state.updateStatus("Home exit detected — ${it.title} is already recording")
            return false
        }
        val timestamp = ZonedDateTime.now().format(TRIP_TIME_FORMAT)
        val trip = repository.startTrip("Automatic $timestamp", config.tripType)
        return try {
            startTracking(context, trip.id)
            state.activeAutoTripId = trip.id
            state.updateStatus("Away from home — automatic trip is recording")
            enqueueSync(context)
            true
        } catch (error: Exception) {
            repository.stopTrip()
            state.activeAutoTripId = null
            state.updateStatus("Automatic start failed: ${error.message ?: error.javaClass.simpleName}")
            false
        }
    }

    fun handleEnter() {
        val dwell = settings.read().returnDwellMinutes
        if (state.activeAutoTripId == null) {
            state.clearReturnDwell()
            cancelReturnCheck(context)
            state.updateStatus("At home — waiting for the next departure")
            return
        }
        val deadline = state.beginReturnDwell(dwell, nowEpochMs()) ?: return
        scheduleReturnCheck(context, deadline)
        val remainingMinutes = ((deadline - nowEpochMs()).coerceAtLeast(0L) + 59_999L) / 60_000L
        state.updateStatus("Home detected — stops in $remainingMinutes minutes if still inside the zone")
    }

    suspend fun handleDwell(): Boolean {
        val autoTripId = state.activeAutoTripId ?: run {
            state.clearReturnDwell()
            state.updateStatus("At home — waiting for the next departure")
            return false
        }
        val deadline = state.returnDwellDeadlineEpochMs
        if (deadline != null && nowEpochMs() + DEADLINE_TOLERANCE_MS < deadline) {
            scheduleReturnCheck(context, deadline)
            return false
        }
        val active = repository.activeTrip()
        if (active?.id != autoTripId) {
            state.activeAutoTripId = null
            state.updateStatus("At home — no automatic trip is active")
            return false
        }
        repository.stopTrip()
        stopTracking(context)
        state.activeAutoTripId = null
        state.updateStatus("Returned home — automatic trip stopped and queued for sync")
        enqueueSync(context)
        return true
    }

    private companion object {
        val TRIP_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        const val DEADLINE_TOLERANCE_MS = 1_000L
    }
}
