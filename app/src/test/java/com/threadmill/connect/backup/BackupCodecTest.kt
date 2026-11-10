package com.threadmill.connect.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    @Test
    fun roundTripsABackup() {
        val file = BackupFile(
            workouts = listOf(
                BackupWorkout(
                    id = "w1",
                    name = "Default",
                    createdAt = 1,
                    updatedAt = 2,
                    intervals = listOf(BackupInterval("i1", "WarmUp", 60, 3.0)),
                ),
            ),
            runs = listOf(
                BackupRun(
                    id = "r1",
                    workoutId = "w1",
                    workoutName = "Default",
                    startedAt = 10,
                    endedAt = 20,
                    outcome = "Completed",
                    durationSec = 60,
                    distanceMeters = 100.0,
                    averageSpeedKmh = 6.0,
                    maxSpeedKmh = 8.0,
                    intervals = listOf(BackupRunInterval("Training", 60, 6.0, 60, 100.0)),
                    samples = listOf(BackupSample(1, 6.0)),
                ),
            ),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        assertEquals("Default", decoded.workouts.single().name)
        assertEquals(100.0, decoded.runs.single().distanceMeters, 0.001)
        assertEquals(6.0, decoded.runs.single().samples.single().speedKmh, 0.001)
    }

    @Test
    fun mergeSkipsRecordsAlreadyOnThePhone() {
        val file = BackupFile(
            workouts = listOf(
                BackupWorkout("w1", "Keep", 0, 0, emptyList()),
                BackupWorkout("w2", "Add", 0, 0, emptyList()),
            ),
            runs = listOf(
                sampleRun("r1"),
                sampleRun("r2"),
            ),
        )
        val fresh = file.excluding(setOf("w1"), setOf("r2"))
        assertEquals(listOf("w2"), fresh.workouts.map { it.id })
        assertEquals(listOf("r1"), fresh.runs.map { it.id })
    }

    @Test
    fun rejectsANewerBackupAndARandomFile() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"version":2,"workouts":[],"runs":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("not json")
        }
    }

    private fun sampleRun(id: String) = BackupRun(
        id = id,
        workoutName = "Default",
        startedAt = 0,
        endedAt = 1,
        outcome = "Stopped",
        durationSec = 1,
        distanceMeters = 0.0,
        averageSpeedKmh = 0.0,
        maxSpeedKmh = 0.0,
    )
}
