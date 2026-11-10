package com.threadmill.connect.model

const val DEFAULT_WORKOUT_ID = "00000000-0000-4000-8000-000000000001"

fun defaultWorkout(now: Long = System.currentTimeMillis()): Workout {
    val rows = listOf(
        Stage.WarmUp to (60 to 3.0),
        Stage.Training to (150 to 4.0),
        Stage.Training to (240 to 5.6),
        Stage.Recover to (180 to 4.0),
        Stage.Training to (255 to 6.2),
        Stage.Recover to (180 to 4.0),
        Stage.Training to (255 to 6.2),
        Stage.Recover to (180 to 4.0),
        Stage.Training to (240 to 5.6),
        Stage.CoolDown to (240 to 3.0),
    )
    return Workout(
        id = DEFAULT_WORKOUT_ID,
        name = "Default",
        createdAt = now,
        updatedAt = now,
        intervals = rows.mapIndexed { index, (stage, timing) ->
            WorkoutInterval(
                id = "00000000-0000-4000-8000-0000000000%02d".format(index + 1),
                stage = stage,
                durationSec = timing.first,
                speedKmh = timing.second,
            )
        },
    )
}
