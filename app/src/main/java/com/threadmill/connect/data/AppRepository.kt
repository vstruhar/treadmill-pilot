package com.threadmill.connect.data

import androidx.room.withTransaction
import com.threadmill.connect.backup.BackupFile
import com.threadmill.connect.backup.BackupInterval
import com.threadmill.connect.backup.BackupRun
import com.threadmill.connect.backup.BackupRunInterval
import com.threadmill.connect.backup.BackupSample
import com.threadmill.connect.backup.BackupWorkout
import com.threadmill.connect.backup.RestoreCounts
import com.threadmill.connect.backup.excluding
import com.threadmill.connect.model.RunDetail
import com.threadmill.connect.model.RunIntervalStats
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.RunSummary
import com.threadmill.connect.model.SpeedSample
import com.threadmill.connect.model.Stage
import com.threadmill.connect.model.Totals
import com.threadmill.connect.model.Workout
import com.threadmill.connect.model.WorkoutInterval
import com.threadmill.connect.model.defaultWorkout
import com.threadmill.connect.run.RunDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AppRepository(private val database: AppDatabase) {
    private val workouts = database.workouts()
    private val runs = database.runs()

    fun observeWorkouts(): Flow<List<Workout>> = workouts.observeAll().map { rows -> rows.map { it.toWorkout() } }

    fun observeWorkout(id: String): Flow<Workout?> = workouts.observe(id).map { it?.toWorkout() }

    fun observeRuns(): Flow<List<RunSummary>> = runs.observeAll().map { rows ->
        rows.map { row ->
            RunSummary(
                id = row.run.id,
                workoutName = row.run.workoutName,
                startedAt = row.run.startedAt,
                outcome = RunOutcome.valueOf(row.run.outcome),
                durationSec = row.run.durationSec,
                distanceMeters = row.run.distanceMeters,
            )
        }
    }

    fun observeRun(id: String): Flow<RunDetail?> = runs.observe(id).map { it?.toDetail() }

    fun observeTotals(): Flow<Totals> = observeRuns().map { rows ->
        Totals(
            runs = rows.size,
            durationSec = rows.sumOf { it.durationSec },
            distanceMeters = rows.sumOf { it.distanceMeters },
        )
    }

    suspend fun ensureSeed() {
        if (workouts.count() == 0) saveWorkout(defaultWorkout())
    }

    suspend fun saveWorkout(workout: Workout) {
        database.withTransaction {
            workouts.insert(
                WorkoutEntity(
                    id = workout.id,
                    name = workout.name.trim(),
                    createdAt = workout.createdAt,
                    updatedAt = workout.updatedAt,
                ),
            )
            workouts.deleteIntervals(workout.id)
            workouts.insertIntervals(
                workout.intervals.mapIndexed { index, interval ->
                    IntervalEntity(
                        id = interval.id,
                        workoutId = workout.id,
                        position = index,
                        stage = interval.stage.name,
                        durationSec = interval.durationSec,
                        speedKmh = interval.speedKmh,
                    )
                },
            )
        }
    }

    suspend fun deleteWorkout(id: String) {
        workouts.delete(id)
    }

    suspend fun deleteRun(id: String) {
        runs.delete(id)
    }

    suspend fun saveRun(draft: RunDraft): String {
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            runs.insert(
                RunEntity(
                    id = id,
                    workoutId = draft.workoutId,
                    workoutName = draft.workoutName,
                    startedAt = draft.startedAt,
                    endedAt = draft.endedAt,
                    outcome = draft.outcome.name,
                    durationSec = draft.durationSec,
                    distanceMeters = draft.distanceMeters,
                    averageSpeedKmh = draft.averageSpeedKmh,
                    maxSpeedKmh = draft.maxSpeedKmh,
                    energyKcal = draft.energyKcal,
                ),
            )
            runs.insertIntervals(
                draft.intervals.mapIndexed { index, interval ->
                    RunIntervalEntity(
                        id = UUID.randomUUID().toString(),
                        runId = id,
                        position = index,
                        stage = interval.stage.name,
                        plannedDurationSec = interval.plannedDurationSec,
                        plannedSpeedKmh = interval.plannedSpeedKmh,
                        actualDurationSec = interval.actualDurationSec,
                        distanceMeters = interval.distanceMeters,
                    )
                },
            )
            runs.insertSamples(
                draft.samples.map { sample ->
                    SpeedSampleEntity(
                        id = UUID.randomUUID().toString(),
                        runId = id,
                        offsetSec = sample.offsetSec,
                        speedKmh = sample.speedKmh,
                    )
                },
            )
        }
        return id
    }

    suspend fun exportBackup(): BackupFile = BackupFile(
        workouts = workouts.all().map { row ->
            val workout = row.toWorkout()
            BackupWorkout(
                id = workout.id,
                name = workout.name,
                createdAt = workout.createdAt,
                updatedAt = workout.updatedAt,
                intervals = workout.intervals.map { interval ->
                    BackupInterval(
                        id = interval.id,
                        stage = interval.stage.name,
                        durationSec = interval.durationSec,
                        speedKmh = interval.speedKmh,
                    )
                },
            )
        },
        runs = runs.all().map { row ->
            val detail = row.toDetail()
            BackupRun(
                id = detail.id,
                workoutId = detail.workoutId,
                workoutName = detail.workoutName,
                startedAt = detail.startedAt,
                endedAt = detail.endedAt,
                outcome = detail.outcome.name,
                durationSec = detail.durationSec,
                distanceMeters = detail.distanceMeters,
                averageSpeedKmh = detail.averageSpeedKmh,
                maxSpeedKmh = detail.maxSpeedKmh,
                energyKcal = detail.energyKcal,
                intervals = detail.intervals.map { interval ->
                    BackupRunInterval(
                        stage = interval.stage.name,
                        plannedDurationSec = interval.plannedDurationSec,
                        plannedSpeedKmh = interval.plannedSpeedKmh,
                        actualDurationSec = interval.actualDurationSec,
                        distanceMeters = interval.distanceMeters,
                    )
                },
                samples = detail.samples.map { sample ->
                    BackupSample(offsetSec = sample.offsetSec, speedKmh = sample.speedKmh)
                },
            )
        },
    )

    suspend fun restore(file: BackupFile): RestoreCounts {
        val fresh = file.excluding(workouts.ids().toSet(), runs.ids().toSet())
        database.withTransaction {
            fresh.workouts.forEach { workout ->
                workouts.insert(
                    WorkoutEntity(
                        id = workout.id,
                        name = workout.name,
                        createdAt = workout.createdAt,
                        updatedAt = workout.updatedAt,
                    ),
                )
                workouts.deleteIntervals(workout.id)
                workouts.insertIntervals(
                    workout.intervals.mapIndexed { index, interval ->
                        IntervalEntity(
                            id = interval.id,
                            workoutId = workout.id,
                            position = index,
                            stage = interval.stage,
                            durationSec = interval.durationSec,
                            speedKmh = interval.speedKmh,
                        )
                    },
                )
            }
            fresh.runs.forEach { run -> insertRun(run) }
        }
        return RestoreCounts(fresh.workouts.size, fresh.runs.size)
    }

    private suspend fun insertRun(run: BackupRun) {
        runs.insert(
            RunEntity(
                id = run.id,
                workoutId = run.workoutId,
                workoutName = run.workoutName,
                startedAt = run.startedAt,
                endedAt = run.endedAt,
                outcome = run.outcome,
                durationSec = run.durationSec,
                distanceMeters = run.distanceMeters,
                averageSpeedKmh = run.averageSpeedKmh,
                maxSpeedKmh = run.maxSpeedKmh,
                energyKcal = run.energyKcal,
            ),
        )
        runs.insertIntervals(
            run.intervals.mapIndexed { index, interval ->
                RunIntervalEntity(
                    id = UUID.randomUUID().toString(),
                    runId = run.id,
                    position = index,
                    stage = interval.stage,
                    plannedDurationSec = interval.plannedDurationSec,
                    plannedSpeedKmh = interval.plannedSpeedKmh,
                    actualDurationSec = interval.actualDurationSec,
                    distanceMeters = interval.distanceMeters,
                )
            },
        )
        runs.insertSamples(
            run.samples.map { sample ->
                SpeedSampleEntity(
                    id = UUID.randomUUID().toString(),
                    runId = run.id,
                    offsetSec = sample.offsetSec,
                    speedKmh = sample.speedKmh,
                )
            },
        )
    }
}

private fun WorkoutWithIntervals.toWorkout(): Workout = Workout(
    id = workout.id,
    name = workout.name,
    createdAt = workout.createdAt,
    updatedAt = workout.updatedAt,
    intervals = intervals.sortedBy { it.position }.map { interval ->
        WorkoutInterval(
            id = interval.id,
            stage = Stage.valueOf(interval.stage),
            durationSec = interval.durationSec,
            speedKmh = interval.speedKmh,
        )
    },
)

private fun RunWithDetails.toDetail(): RunDetail = RunDetail(
    id = run.id,
    workoutId = run.workoutId,
    workoutName = run.workoutName,
    startedAt = run.startedAt,
    endedAt = run.endedAt,
    outcome = RunOutcome.valueOf(run.outcome),
    durationSec = run.durationSec,
    distanceMeters = run.distanceMeters,
    averageSpeedKmh = run.averageSpeedKmh,
    maxSpeedKmh = run.maxSpeedKmh,
    energyKcal = run.energyKcal,
    intervals = intervals.sortedBy { it.position }.map { interval ->
        RunIntervalStats(
            stage = Stage.valueOf(interval.stage),
            plannedDurationSec = interval.plannedDurationSec,
            plannedSpeedKmh = interval.plannedSpeedKmh,
            actualDurationSec = interval.actualDurationSec,
            distanceMeters = interval.distanceMeters,
        )
    },
    samples = samples.sortedBy { it.offsetSec }.map { SpeedSample(it.offsetSec, it.speedKmh) },
)
