package ca.gmode.triprecorder.tracking

object GpsRecoveryPolicy {
    const val FIX_TIMEOUT_MS = 45_000L
    const val REQUEST_FAILURE_RETRY_MS = 15_000L

    fun fixIsStale(nowElapsedRealtimeMs: Long, lastFixElapsedRealtimeMs: Long?): Boolean =
        lastFixElapsedRealtimeMs == null ||
            nowElapsedRealtimeMs - lastFixElapsedRealtimeMs >= FIX_TIMEOUT_MS

    fun nextWatchdogDelayMs(nowElapsedRealtimeMs: Long, lastFixElapsedRealtimeMs: Long?): Long {
        if (lastFixElapsedRealtimeMs == null) return FIX_TIMEOUT_MS
        val age = (nowElapsedRealtimeMs - lastFixElapsedRealtimeMs).coerceAtLeast(0L)
        return (FIX_TIMEOUT_MS - age).coerceAtLeast(1_000L)
    }
}
