package com.threadmill.connect.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultWorkoutTest {
    @Test
    fun starterWorkoutIsThirtyThreeMinutes() {
        val workout = defaultWorkout()
        assertEquals("Default", workout.name)
        assertEquals(33 * 60, workout.durationSec)
        assertEquals(Stage.WarmUp, workout.intervals.first().stage)
        assertEquals(Stage.CoolDown, workout.intervals.last().stage)
    }
}
