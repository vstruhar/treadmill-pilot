package com.threadmill.connect.run

import com.threadmill.connect.ble.LiveMetrics
import com.threadmill.connect.model.IntervalPlan
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class RunRecorderTest {
    @Test
    fun usesTheTreadmillDistanceCounter() {
        val recorder = recorder()
        recorder.onMetrics(LiveMetrics(distanceMeters = 1_000, speedKmh = 10.0), 0, 0)
        recorder.onMetrics(LiveMetrics(distanceMeters = 1_250, speedKmh = 10.0, calories = 20), 10_000, 0)
        recorder.onMetrics(LiveMetrics(distanceMeters = 1_250, calories = 24), 10_000, 0)
        val draft = recorder.snapshot(0, 10_000, 10_000, RunOutcome.Completed)
        assertEquals(250.0, draft.distanceMeters, 0.001)
        assertEquals(4, draft.energyKcal)
        assertEquals(90.0, draft.averageSpeedKmh, 0.001)
    }

    @Test
    fun integratesSpeedWhenDistanceNeverArrives() {
        val recorder = recorder()
        recorder.onMetrics(LiveMetrics(speedKmh = 10.0), 0, 0)
        recorder.onMetrics(LiveMetrics(speedKmh = 10.0), 3_600, 0)
        assertEquals(10.0, recorder.distanceMeters(), 0.05)
    }

    @Test
    fun splitsDistanceAcrossIntervals() {
        val recorder = RunRecorder(
            "w",
            "Default",
            listOf(
                IntervalPlan(Stage.Training, 10_000, 5.0),
                IntervalPlan(Stage.Recover, 10_000, 4.0),
            ),
        )
        recorder.onMetrics(LiveMetrics(distanceMeters = 0), 0, 0)
        recorder.onMetrics(LiveMetrics(distanceMeters = 100), 10_000, 0)
        recorder.mark(10_000, 1)
        recorder.onMetrics(LiveMetrics(distanceMeters = 180), 20_000, 1)
        val draft = recorder.snapshot(0, 20_000, 20_000, RunOutcome.Stopped)
        assertEquals(100.0, draft.intervals[0].distanceMeters, 0.001)
        assertEquals(10, draft.intervals[0].actualDurationSec)
        assertEquals(80.0, draft.intervals[1].distanceMeters, 0.001)
        assertEquals(10, draft.intervals[1].actualDurationSec)
    }

    private fun recorder() = RunRecorder("w", "Default", listOf(IntervalPlan(Stage.Training, 60_000, 5.0)))
}
