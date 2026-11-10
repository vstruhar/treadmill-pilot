package com.threadmill.connect.run

import com.threadmill.connect.model.IntervalPlan
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunEngineTest {
    @Test
    fun startBeltThenCountsDownTogether() {
        val engine = engine(plan(15_000, 5.0), plan(20_000, 8.0))
        val started = engine.start()
        assertTrue(started.first() is EngineCommand.StartBelt)
        assertEquals(5.0, started.speed(), 0.001)
        assertEquals(listOf("3"), started.announcements())
        val commands = engine.tick(3_000)
        assertEquals(listOf("2", "1"), commands.announcements())
        assertTrue(commands.none { it is EngineCommand.StartBelt })
        val active = engine.state as EngineState.Active
        assertEquals(0, active.index)
        assertEquals(15_000, active.remainingMs)
    }

    @Test
    fun pauseDuringCountdownStopsTheBeltAndResumeStartsItAgain() {
        val engine = engine(plan(15_000, 5.0))
        engine.start()
        engine.tick(1_000)
        assertEquals(listOf(EngineCommand.PauseBelt), engine.pause())
        val resumed = engine.resume()
        assertTrue(resumed.first() is EngineCommand.StartBelt)
        assertEquals(5.0, resumed.speed(), 0.001)
        assertTrue(engine.state is EngineState.Countdown)
    }

    @Test
    fun announcesTheNextIntervalTenSecondsAheadAndBeeps() {
        val engine = running(plan(15_000, 5.0, Stage.Training), plan(20_000, 8.0, Stage.Recover))
        val early = engine.tick(5_000)
        assertEquals(listOf("Next: Recover, 8.0 kilometers per hour"), early.announcements())
        assertEquals(0, early.count { it is EngineCommand.Beep })
        assertEquals(1, engine.tick(7_000).count { it is EngineCommand.Beep })
        assertEquals(1, engine.tick(1_000).count { it is EngineCommand.Beep })
        assertEquals(1, engine.tick(1_000).count { it is EngineCommand.Beep })
        val advanced = engine.tick(1_000)
        assertTrue(advanced.none { it is EngineCommand.StartBelt })
        assertEquals(8.0, advanced.speed(), 0.001)
        assertEquals(1, (engine.state as EngineState.Active).index)
        assertTrue(advanced.announcements().isEmpty())
    }

    @Test
    fun shortPreviousIntervalAnnouncesTheNextOneWhenItStarts() {
        val engine = running(plan(8_000, 4.0, Stage.WarmUp), plan(12_000, 7.0, Stage.Training))
        val commands = engine.tick(8_000)
        assertEquals(listOf("Next: Training, 7.0 kilometers per hour"), commands.announcements())
        assertEquals(7.0, commands.speed(), 0.001)
        assertEquals(1, (engine.state as EngineState.Active).index)
    }

    @Test
    fun doesNotRepeatAnAnnouncementThatAlreadyPlayed() {
        val engine = running(plan(15_000, 5.0), plan(5_000, 6.0, Stage.Recover))
        engine.tick(5_000)
        val change = engine.tick(10_000)
        assertTrue(change.announcements().isEmpty())
        assertEquals(6.0, change.speed(), 0.001)
    }

    @Test
    fun skipSpeaksTheNewIntervalAndSetsItsSpeed() {
        val engine = running(plan(15_000, 5.0), plan(10_000, 6.2, Stage.Recover))
        val commands = engine.skip()
        assertEquals(listOf("Recover, 6.2 kilometers per hour"), commands.announcements())
        assertEquals(6.2, commands.speed(), 0.001)
        assertEquals(10_000, (engine.state as EngineState.Active).remainingMs)
    }

    @Test
    fun pauseFreezesTheClockAndResumeRestartsTheBelt() {
        val engine = running(plan(15_000, 5.0))
        engine.tick(4_000)
        assertEquals(listOf(EngineCommand.PauseBelt), engine.pause())
        assertTrue(engine.tick(10_000).isEmpty())
        val paused = engine.state as EngineState.Paused
        assertEquals(11_000, paused.remainingMs)
        val resumed = engine.resume()
        assertTrue(resumed.first() is EngineCommand.StartBelt)
        assertEquals(5.0, resumed.speed(), 0.001)
        assertEquals(11_000, (engine.state as EngineState.Active).remainingMs)
    }

    @Test
    fun linkLossPausesUntilTheLinkReturns() {
        val engine = running(plan(15_000, 5.0))
        engine.tick(2_000)
        assertEquals(listOf("Connection lost"), engine.linkLost().announcements())
        assertTrue(engine.tick(5_000).isEmpty())
        val restored = engine.linkRestored()
        assertTrue(restored.first() is EngineCommand.StartBelt)
        assertEquals(5.0, restored.speed(), 0.001)
        assertEquals(13_000, (engine.state as EngineState.Active).remainingMs)
    }

    @Test
    fun remoteSpeedHoldsUntilTheNextInterval() {
        val engine = running(plan(12_000, 5.0), plan(10_000, 8.0))
        engine.tick(2_000)
        assertTrue(engine.remoteSpeed(6.0).isEmpty())
        assertTrue(engine.tick(2_000).none { it is EngineCommand.SetSpeed })
        engine.linkLost()
        val restored = engine.linkRestored()
        assertEquals(6.0, restored.speed(), 0.001)
        val next = engine.tick(8_000)
        assertEquals(8.0, next.speed(), 0.001)
    }

    @Test
    fun lastIntervalCompletesTheRun() {
        val engine = running(plan(5_000, 4.0))
        val commands = engine.tick(5_000)
        assertEquals(listOf("Workout complete"), commands.announcements())
        assertTrue(commands.any { it is EngineCommand.StopBelt })
        assertEquals(RunOutcome.Completed, (engine.state as EngineState.Finished).outcome)
    }

    @Test
    fun stopEndsARunEarly() {
        val engine = running(plan(20_000, 5.0), plan(20_000, 8.0))
        val commands = engine.stop()
        assertEquals(listOf(EngineCommand.StopBelt), commands)
        assertEquals(RunOutcome.Stopped, (engine.state as EngineState.Finished).outcome)
        assertTrue(engine.tick(1_000).isEmpty())
    }

    private fun running(vararg intervals: IntervalPlan): RunEngine {
        val engine = engine(*intervals)
        engine.start()
        engine.tick(3_000)
        return engine
    }

    private fun engine(vararg intervals: IntervalPlan) = RunEngine(intervals.toList())

    private fun plan(durationMs: Long, speed: Double, stage: Stage = Stage.Training) =
        IntervalPlan(stage, durationMs, speed)

    private fun List<EngineCommand>.announcements() = mapNotNull { (it as? EngineCommand.Announce)?.text }

    private fun List<EngineCommand>.speed() = filterIsInstance<EngineCommand.SetSpeed>().single().kmh

    private fun List<EngineCommand>.firstBelt() = first { it is EngineCommand.StartBelt || it is EngineCommand.StopBelt }
}
