package com.threadmill.connect.run

import com.threadmill.connect.ble.LiveMetrics
import com.threadmill.connect.model.IntervalPlan
import com.threadmill.connect.model.RunIntervalStats
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.SpeedSample

data class RunDraft(
    val workoutId: String?,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long,
    val outcome: RunOutcome,
    val durationSec: Int,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val energyKcal: Int?,
    val intervals: List<RunIntervalStats>,
    val samples: List<SpeedSample>,
)

/**
 * Turns treadmill notifications into a Run. Distance prefers the treadmill's
 * own counter. When that never arrives, distance is integrated from speed.
 */
class RunRecorder(
    private val workoutId: String?,
    private val workoutName: String,
    intervals: List<IntervalPlan>,
) {
    private val rows = intervals.map {
        Row(
            stage = it.stage,
            plannedDurationSec = (it.durationMs / 1000L).toInt(),
            plannedSpeedKmh = it.speedKmh,
        )
    }.toMutableList()
    private val samples = mutableListOf<SpeedSample>()
    private var originDistance: Int? = null
    private var lastDistance: Int? = null
    private var originCalories: Int? = null
    private var lastCalories: Int? = null
    private var integratedMeters = 0.0
    private var lastSpeed: Double? = null
    private var lastIntegratedAt: Long? = null
    private var maxSpeed = 0.0
    private var sampleSum = 0.0
    private var sampleCount = 0
    private var lastSampleSec = -1
    private var openIndex = 0
    private var openElapsedMs = 0L
    private var openDistance = 0.0

    fun mark(elapsedMs: Long, intervalIndex: Int) {
        if (intervalIndex == openIndex) return
        close(openIndex, elapsedMs)
        openIndex = intervalIndex.coerceIn(0, rows.lastIndex)
        openElapsedMs = elapsedMs
        openDistance = distanceMeters()
    }

    fun onMetrics(metrics: LiveMetrics, elapsedMs: Long, intervalIndex: Int) {
        mark(elapsedMs, intervalIndex)
        if (metrics.distanceMeters != null) {
            if (originDistance == null) {
                originDistance = metrics.distanceMeters
                openDistance = 0.0
            }
            lastDistance = metrics.distanceMeters
        }
        if (metrics.calories != null) {
            if (originCalories == null) originCalories = metrics.calories
            lastCalories = metrics.calories
        }
        val speed = metrics.speedKmh
        if (speed != null) {
            val previousAt = lastIntegratedAt
            val previousSpeed = lastSpeed
            if (previousAt != null && previousSpeed != null) {
                val dt = (elapsedMs - previousAt) / 1000.0
                if (dt in 0.0..5.0) integratedMeters += previousSpeed / 3.6 * dt
            }
            lastSpeed = speed
            lastIntegratedAt = elapsedMs
            if (speed > maxSpeed) maxSpeed = speed
            val second = (elapsedMs / 1000L).toInt()
            if (second > lastSampleSec) {
                lastSampleSec = second
                samples += SpeedSample(second, speed)
                sampleSum += speed
                sampleCount += 1
            }
        }
    }

    fun distanceMeters(): Double {
        val origin = originDistance
        val last = lastDistance
        if (origin != null && last != null && last >= origin) return (last - origin).toDouble()
        return integratedMeters
    }

    fun snapshot(startedAt: Long, endedAt: Long, elapsedMs: Long, outcome: RunOutcome): RunDraft {
        close(openIndex, elapsedMs)
        val distance = distanceMeters()
        val durationSec = (elapsedMs / 1000L).toInt()
        val average = when {
            durationSec > 0 && distance > 0.0 -> distance / durationSec * 3.6
            sampleCount > 0 -> sampleSum / sampleCount
            else -> 0.0
        }
        val energy = originCalories?.let { origin -> lastCalories?.let { (it - origin).coerceAtLeast(0) } }
        return RunDraft(
            workoutId = workoutId,
            workoutName = workoutName,
            startedAt = startedAt,
            endedAt = endedAt,
            outcome = outcome,
            durationSec = durationSec,
            distanceMeters = distance,
            averageSpeedKmh = average,
            maxSpeedKmh = maxSpeed,
            energyKcal = energy,
            intervals = rows.map {
                RunIntervalStats(
                    stage = it.stage,
                    plannedDurationSec = it.plannedDurationSec,
                    plannedSpeedKmh = it.plannedSpeedKmh,
                    actualDurationSec = it.actualDurationSec,
                    distanceMeters = it.distanceMeters,
                )
            },
            samples = samples.toList(),
        )
    }

    private fun close(index: Int, elapsedMs: Long) {
        if (index !in rows.indices) return
        val row = rows[index]
        rows[index] = row.copy(
            actualDurationSec = ((elapsedMs - openElapsedMs) / 1000L).toInt().coerceAtLeast(0),
            distanceMeters = (distanceMeters() - openDistance).coerceAtLeast(0.0),
        )
    }

    private data class Row(
        val stage: com.threadmill.connect.model.Stage,
        val plannedDurationSec: Int,
        val plannedSpeedKmh: Double,
        val actualDurationSec: Int = 0,
        val distanceMeters: Double = 0.0,
    )
}
