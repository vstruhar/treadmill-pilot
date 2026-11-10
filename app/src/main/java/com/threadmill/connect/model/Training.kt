package com.threadmill.connect.model

enum class Stage {
    WarmUp,
    Training,
    Recover,
    CoolDown,
    ;

    val label: String
        get() = when (this) {
            WarmUp -> "Warm up"
            Training -> "Training"
            Recover -> "Recover"
            CoolDown -> "Cool down"
        }

    val spoken: String
        get() = label
}

enum class RunOutcome {
    Completed,
    Stopped,
    ;

    val label: String
        get() = when (this) {
            Completed -> "Completed"
            Stopped -> "Stopped"
        }
}

data class WorkoutInterval(
    val id: String,
    val stage: Stage,
    val durationSec: Int,
    val speedKmh: Double,
)

data class Workout(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val intervals: List<WorkoutInterval>,
) {
    val durationSec: Int
        get() = intervals.sumOf { it.durationSec }
}

data class IntervalPlan(
    val stage: Stage,
    val durationMs: Long,
    val speedKmh: Double,
)

fun WorkoutInterval.toPlan(): IntervalPlan = IntervalPlan(
    stage = stage,
    durationMs = durationSec * 1000L,
    speedKmh = speedKmh,
)

data class RunIntervalStats(
    val stage: Stage,
    val plannedDurationSec: Int,
    val plannedSpeedKmh: Double,
    val actualDurationSec: Int,
    val distanceMeters: Double,
)

data class SpeedSample(
    val offsetSec: Int,
    val speedKmh: Double,
)

data class RunSummary(
    val id: String,
    val workoutName: String,
    val startedAt: Long,
    val outcome: RunOutcome,
    val durationSec: Int,
    val distanceMeters: Double,
)

data class RunDetail(
    val id: String,
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

data class Totals(
    val runs: Int,
    val durationSec: Int,
    val distanceMeters: Double,
)

data class WeekDistance(
    val weekStartEpochDay: Long,
    val distanceMeters: Double,
)
