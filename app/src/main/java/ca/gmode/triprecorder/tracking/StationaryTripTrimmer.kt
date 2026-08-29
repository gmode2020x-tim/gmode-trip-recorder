package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.PointEntity
import ca.gmode.triprecorder.data.distanceMeters
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import java.time.Instant

data class StationaryPeriod(
    val startAt: String,
    val endAt: String,
    val durationMillis: Long,
    val startIndex: Int,
    val endIndex: Int,
)

data class StationaryTrimResult(
    val segments: List<List<PointEntity>>,
    val stationaryPeriods: List<StationaryPeriod>,
    val distanceMeters: Double,
    val movingDurationMillis: Long,
    val rawDurationMillis: Long,
) {
    val points: List<PointEntity> = segments.flatten()
    val trimmed: Boolean = stationaryPeriods.isNotEmpty()
}

object StationaryTripTrimmer {
    fun trim(points: List<PointEntity>, config: AutoRecordingConfig): StationaryTrimResult {
        val ordered = points.sortedBy { it.sequence }
        val rawDuration = durationBetween(ordered.firstOrNull(), ordered.lastOrNull())
        if (!config.stationaryTrimEnabled || ordered.size < 3) {
            return result(listOf(ordered), emptyList(), rawDuration)
        }

        val pauseMillis = config.stationaryPauseMinutes * 60_000L
        val splitMillis = config.stationarySplitMinutes * 60_000L
        val speedThresholdMps = config.stationarySpeedKmh / 3.6
        val periods = findPeriods(
            ordered,
            config.stationaryRadiusMeters.toDouble(),
            pauseMillis,
            speedThresholdMps,
        )
        if (periods.isEmpty()) return result(listOf(ordered), emptyList(), rawDuration)

        val segments = mutableListOf<MutableList<PointEntity>>()
        var current = mutableListOf<PointEntity>()
        var cursor = 0
        periods.forEach { period ->
            while (cursor <= period.startIndex && cursor < ordered.size) {
                current += ordered[cursor++]
            }
            if (period.startIndex == 0) current.clear()
            cursor = maxOf(cursor, period.endIndex)
            val departure = ordered[period.endIndex]
            if (period.durationMillis >= splitMillis && current.isNotEmpty()) {
                segments += current
                current = mutableListOf()
            }
            if (period.endIndex < ordered.lastIndex) current += departure
            cursor = period.endIndex + 1
        }
        while (cursor < ordered.size) current += ordered[cursor++]
        if (current.isNotEmpty()) segments += current

        val usableSegments = segments.filter { it.size >= 2 }.ifEmpty {
            listOf(listOf(ordered.first(), ordered.last()))
        }
        return result(usableSegments, periods, rawDuration)
    }

    private fun findPeriods(
        points: List<PointEntity>,
        radiusMeters: Double,
        pauseMillis: Long,
        speedThresholdMps: Double,
    ): List<StationaryPeriod> {
        val periods = mutableListOf<StationaryPeriod>()
        var start = 0
        while (start < points.lastIndex) {
            var end = start
            var centerLatitude = points[start].latitude
            var centerLongitude = points[start].longitude
            var lowSpeedCount = if (isSlow(points[start], speedThresholdMps)) 1 else 0
            while (end < points.lastIndex) {
                val candidate = points[end + 1]
                if (distanceMeters(centerLatitude, centerLongitude, candidate.latitude, candidate.longitude) > radiusMeters) break
                end += 1
                val count = end - start + 1
                centerLatitude += (candidate.latitude - centerLatitude) / count
                centerLongitude += (candidate.longitude - centerLongitude) / count
                if (isSlow(candidate, speedThresholdMps)) lowSpeedCount += 1
            }

            val duration = durationBetween(points[start], points[end])
            val slowFraction = lowSpeedCount.toDouble() / (end - start + 1).coerceAtLeast(1)
            if (duration >= pauseMillis && slowFraction >= MINIMUM_SLOW_FRACTION) {
                var expandedStart = start
                var expandedEnd = end
                while (expandedStart > 0 && distanceMeters(
                        centerLatitude,
                        centerLongitude,
                        points[expandedStart - 1].latitude,
                        points[expandedStart - 1].longitude,
                    ) <= radiusMeters
                ) {
                    expandedStart -= 1
                }
                while (expandedEnd < points.lastIndex && distanceMeters(
                        centerLatitude,
                        centerLongitude,
                        points[expandedEnd + 1].latitude,
                        points[expandedEnd + 1].longitude,
                    ) <= radiusMeters
                ) {
                    expandedEnd += 1
                }
                val nextPeriod = StationaryPeriod(
                    startAt = points[expandedStart].recordedAt,
                    endAt = points[expandedEnd].recordedAt,
                    durationMillis = durationBetween(points[expandedStart], points[expandedEnd]),
                    startIndex = expandedStart,
                    endIndex = expandedEnd,
                )
                val previous = periods.lastOrNull()
                if (previous != null && nextPeriod.startIndex <= previous.endIndex) {
                    val mergedEnd = maxOf(previous.endIndex, nextPeriod.endIndex)
                    periods[periods.lastIndex] = previous.copy(
                        endAt = points[mergedEnd].recordedAt,
                        durationMillis = durationBetween(points[previous.startIndex], points[mergedEnd]),
                        endIndex = mergedEnd,
                    )
                } else {
                    periods += nextPeriod
                }
                start = expandedEnd + 1
            } else {
                start += 1
            }
        }
        return periods
    }

    private fun result(
        segments: List<List<PointEntity>>,
        periods: List<StationaryPeriod>,
        rawDuration: Long,
    ): StationaryTrimResult {
        val distance = segments.sumOf { segment ->
            segment.zipWithNext().sumOf { (first, second) ->
                distanceMeters(first.latitude, first.longitude, second.latitude, second.longitude)
            }
        }
        val stationaryDuration = periods.sumOf { it.durationMillis }.coerceAtMost(rawDuration)
        return StationaryTrimResult(
            segments = segments,
            stationaryPeriods = periods,
            distanceMeters = distance,
            movingDurationMillis = (rawDuration - stationaryDuration).coerceAtLeast(0L),
            rawDurationMillis = rawDuration,
        )
    }

    private fun durationBetween(first: PointEntity?, last: PointEntity?): Long {
        if (first == null || last == null) return 0L
        return runCatching {
            (Instant.parse(last.recordedAt).toEpochMilli() - Instant.parse(first.recordedAt).toEpochMilli()).coerceAtLeast(0L)
        }.getOrDefault(0L)
    }

    private fun isSlow(point: PointEntity, thresholdMps: Double): Boolean =
        point.speedMps?.takeIf { it.isFinite() }?.let { it <= thresholdMps } ?: true

    private const val MINIMUM_SLOW_FRACTION = 0.6
}
