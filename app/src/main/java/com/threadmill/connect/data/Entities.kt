package com.threadmill.connect.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "intervals",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class IntervalEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val position: Int,
    val stage: String,
    val durationSec: Int,
    val speedKmh: Double,
)

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val workoutId: String?,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long,
    val outcome: String,
    val durationSec: Int,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val energyKcal: Int?,
)

@Entity(
    tableName = "run_intervals",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class RunIntervalEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val position: Int,
    val stage: String,
    val plannedDurationSec: Int,
    val plannedSpeedKmh: Double,
    val actualDurationSec: Int,
    val distanceMeters: Double,
)

@Entity(
    tableName = "speed_samples",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class SpeedSampleEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val offsetSec: Int,
    val speedKmh: Double,
)

data class WorkoutWithIntervals(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val intervals: List<IntervalEntity>,
)

data class RunWithDetails(
    @Embedded val run: RunEntity,
    @Relation(parentColumn = "id", entityColumn = "runId")
    val intervals: List<RunIntervalEntity>,
    @Relation(parentColumn = "id", entityColumn = "runId")
    val samples: List<SpeedSampleEntity>,
)
