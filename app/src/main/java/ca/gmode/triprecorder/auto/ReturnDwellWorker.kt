package ca.gmode.triprecorder.auto

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import java.util.concurrent.TimeUnit

class ReturnDwellWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val state = AutoRecordingStateStore(applicationContext)
        if (state.activeAutoTripId == null) {
            state.clearReturnDwell()
            return Result.success()
        }
        val deadline = state.returnDwellDeadlineEpochMs ?: return Result.success()
        val remaining = deadline - System.currentTimeMillis()
        if (remaining > EARLY_TOLERANCE_MS) {
            scheduleAt(applicationContext, deadline)
            return Result.success()
        }
        AutoTripController(applicationContext).handleDwell()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "automatic_return_dwell_deadline"
        private const val EARLY_TOLERANCE_MS = 1_000L

        fun scheduleAt(
            context: Context,
            deadlineEpochMs: Long,
            nowEpochMs: Long = System.currentTimeMillis(),
        ) {
            val delay = (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ReturnDwellWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
