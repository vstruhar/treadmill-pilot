package com.threadmill.connect.run

import com.threadmill.connect.model.IntervalPlan
import com.threadmill.connect.model.Stage

enum class RunPhase {
    Idle,
    Preparing,
    Countdown,
    Running,
    Paused,
    Reconnecting,
    Finished,
}

data class RunUiState(
    val phase: RunPhase = RunPhase.Idle,
    val workoutId: String? = null,
    val workoutName: String = "",
    val message: String? = null,
    val index: Int = 0,
    val count: Int = 0,
    val stage: Stage? = null,
    val intervalRemainingMs: Long = 0,
    val totalRemainingMs: Long = 0,
    val elapsedMs: Long = 0,
    val countdownMs: Long = 0,
    val targetSpeedKmh: Double = 0.0,
    val liveSpeedKmh: Double? = null,
    val heartRate: Int? = null,
    val nextStage: Stage? = null,
    val nextSpeedKmh: Double? = null,
    val distanceMeters: Double = 0.0,
    val energyKcal: Int? = null,
    val intervals: List<IntervalPlan> = emptyList(),
    val savedRunId: String? = null,
) {
    val active: Boolean
        get() = phase == RunPhase.Preparing ||
            phase == RunPhase.Countdown ||
            phase == RunPhase.Running ||
            phase == RunPhase.Paused ||
            phase == RunPhase.Reconnecting
}
