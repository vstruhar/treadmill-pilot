package com.threadmill.connect.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.Stage

@Serializable
data class BackupFile(
    val version: Int = 1,
    val workouts: List<BackupWorkout> = emptyList(),
    val runs: List<BackupRun> = emptyList(),
)

@Serializable
data class BackupWorkout(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val intervals: List<BackupInterval>,
)

@Serializable
data class BackupInterval(
    val id: String,
    val stage: String,
    val durationSec: Int,
    val speedKmh: Double,
)

@Serializable
data class BackupRun(
    val id: String,
    val workoutId: String? = null,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long,
    val outcome: String,
    val durationSec: Int,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val energyKcal: Int? = null,
    val intervals: List<BackupRunInterval> = emptyList(),
    val samples: List<BackupSample> = emptyList(),
)

@Serializable
data class BackupRunInterval(
    val stage: String,
    val plannedDurationSec: Int,
    val plannedSpeedKmh: Double,
    val actualDurationSec: Int,
    val distanceMeters: Double,
)

@Serializable
data class BackupSample(
    val offsetSec: Int,
    val speedKmh: Double,
)

data class RestoreCounts(val workouts: Int, val runs: Int)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("That file is empty.")
        val file = try {
            json.decodeFromString(BackupFile.serializer(), trimmed)
        } catch (_: SerializationException) {
            throw IllegalArgumentException("That file is not a treadmill backup.")
        }
        if (file.version != 1) {
            throw IllegalArgumentException("This backup is from a newer version of the app.")
        }
        file.workouts.forEach { workout ->
            workout.intervals.forEach { Stage.valueOf(it.stage) }
        }
        file.runs.forEach { run ->
            RunOutcome.valueOf(run.outcome)
            run.intervals.forEach { Stage.valueOf(it.stage) }
        }
        return file
    }
}

fun BackupFile.excluding(existingWorkoutIds: Set<String>, existingRunIds: Set<String>): BackupFile = copy(
    workouts = workouts.filter { it.id !in existingWorkoutIds },
    runs = runs.filter { it.id !in existingRunIds },
)
