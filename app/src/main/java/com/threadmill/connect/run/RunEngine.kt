package com.threadmill.connect.run

import com.threadmill.connect.model.IntervalPlan
import com.threadmill.connect.model.RunOutcome
import java.util.Locale

sealed interface EngineState {
    data object Idle : EngineState
    data class Countdown(val remainingMs: Long) : EngineState
    data class Active(val index: Int, val remainingMs: Long) : EngineState
    data class Paused(
        val user: Boolean,
        val link: Boolean,
        val countingDown: Boolean,
        val index: Int,
        val remainingMs: Long,
    ) : EngineState
    data class Finished(val outcome: RunOutcome) : EngineState
}

sealed interface EngineCommand {
    data object StartBelt : EngineCommand
    data class SetSpeed(val kmh: Double) : EngineCommand
    data object PauseBelt : EngineCommand
    data object StopBelt : EngineCommand
    data class Announce(val text: String) : EngineCommand
    data object Beep : EngineCommand
}

/**
 * Pure timing for one Run. The caller feeds elapsed time and performs the commands.
 * An Interval of at least 10 s announces the next one when 10 s remain. A shorter
 * previous Interval announces the new one when the new one starts.
 */
class RunEngine(private val intervals: List<IntervalPlan>) {
    init {
        require(intervals.isNotEmpty()) { "A workout needs an interval." }
    }

    var state: EngineState = EngineState.Idle
        private set

    private var announcedIndex = -1
    private var speedHeld = false
    private var heldKmh: Double? = null

    fun start(): List<EngineCommand> {
        if (state !is EngineState.Idle) return emptyList()
        announcedIndex = -1
        speedHeld = false
        heldKmh = null
        state = EngineState.Countdown(COUNTDOWN_MS)
        return listOf(
            EngineCommand.StartBelt,
            EngineCommand.SetSpeed(intervals.first().speedKmh),
            EngineCommand.Announce("3"),
        )
    }

    fun tick(deltaMs: Long): List<EngineCommand> {
        if (deltaMs <= 0L) return emptyList()
        var left = deltaMs
        val commands = mutableListOf<EngineCommand>()
        var guard = 0
        while (left > 0L && guard++ < intervals.size + 4) {
            when (val current = state) {
                is EngineState.Countdown -> {
                    val step = minOf(left, current.remainingMs)
                    val after = current.remainingMs - step
                    if (current.remainingMs > 2_000L && after <= 2_000L) commands += EngineCommand.Announce("2")
                    if (current.remainingMs > 1_000L && after <= 1_000L) commands += EngineCommand.Announce("1")
                    left -= step
                    if (after == 0L) commands += beginInterval(0, Speech.None, startBelt = false)
                    else state = EngineState.Countdown(after)
                }
                is EngineState.Active -> {
                    val step = minOf(left, current.remainingMs)
                    val after = current.remainingMs - step
                    commands += cues(current.index, current.remainingMs, after)
                    left -= step
                    if (after == 0L) commands += advance(current.index)
                    else state = current.copy(remainingMs = after)
                }
                else -> return commands
            }
        }
        return commands
    }

    fun skip(): List<EngineCommand> = when (val current = state) {
        is EngineState.Countdown -> beginInterval(0, Speech.None, startBelt = true)
        is EngineState.Active -> if (current.index + 1 >= intervals.size) finish(RunOutcome.Completed) else {
            beginInterval(current.index + 1, Speech.Short, startBelt = false)
        }
        else -> emptyList()
    }

    fun pause(): List<EngineCommand> = when (val current = state) {
        is EngineState.Countdown -> {
            state = EngineState.Paused(user = true, link = false, countingDown = true, index = 0, remainingMs = current.remainingMs)
            listOf(EngineCommand.PauseBelt)
        }
        is EngineState.Active -> {
            state = EngineState.Paused(
                user = true,
                link = false,
                countingDown = false,
                index = current.index,
                remainingMs = current.remainingMs,
            )
            listOf(EngineCommand.PauseBelt)
        }
        is EngineState.Paused -> if (current.user) {
            emptyList()
        } else {
            state = current.copy(user = true)
            if (current.link || current.countingDown) emptyList() else listOf(EngineCommand.PauseBelt)
        }
        else -> emptyList()
    }

    fun resume(): List<EngineCommand> {
        val current = state as? EngineState.Paused ?: return emptyList()
        if (!current.user) return emptyList()
        if (current.link) {
            state = current.copy(user = false)
            return emptyList()
        }
        speedHeld = false
        heldKmh = null
        return if (current.countingDown) {
            state = EngineState.Countdown(current.remainingMs)
            listOf(EngineCommand.StartBelt, EngineCommand.SetSpeed(intervals.first().speedKmh))
        } else {
            state = EngineState.Active(current.index, current.remainingMs)
            listOf(EngineCommand.StartBelt, EngineCommand.SetSpeed(intervals[current.index].speedKmh))
        }
    }

    fun linkLost(): List<EngineCommand> {
        val announce = listOf(EngineCommand.Announce("Connection lost"))
        when (val current = state) {
            is EngineState.Countdown -> {
                state = EngineState.Paused(user = false, link = true, countingDown = true, index = 0, remainingMs = current.remainingMs)
            }
            is EngineState.Active -> {
                state = EngineState.Paused(
                    user = false,
                    link = true,
                    countingDown = false,
                    index = current.index,
                    remainingMs = current.remainingMs,
                )
            }
            is EngineState.Paused -> {
                if (current.link) return emptyList()
                state = current.copy(link = true)
            }
            else -> return emptyList()
        }
        return announce
    }

    fun linkRestored(): List<EngineCommand> {
        val current = state as? EngineState.Paused ?: return emptyList()
        if (!current.link) return emptyList()
        if (current.user) {
            state = current.copy(link = false)
            return emptyList()
        }
        return if (current.countingDown) {
            state = EngineState.Countdown(current.remainingMs)
            listOf(EngineCommand.StartBelt, EngineCommand.SetSpeed(intervals.first().speedKmh))
        } else {
            state = EngineState.Active(current.index, current.remainingMs)
            listOf(EngineCommand.StartBelt, EngineCommand.SetSpeed(runningSpeed(current.index)))
        }
    }

    fun remoteSpeed(kmh: Double): List<EngineCommand> {
        when (val current = state) {
            is EngineState.Active -> {
                speedHeld = true
                heldKmh = kmh
                state = current
            }
            is EngineState.Paused -> if (!current.countingDown) {
                speedHeld = true
                heldKmh = kmh
            }
            else -> Unit
        }
        return emptyList()
    }

    fun stop(): List<EngineCommand> = when (state) {
        is EngineState.Finished, EngineState.Idle -> emptyList()
        else -> finish(RunOutcome.Stopped)
    }

    fun treadmillPaused(): List<EngineCommand> = when (state) {
        is EngineState.Active, is EngineState.Countdown -> pause()
        else -> emptyList()
    }

    private fun advance(index: Int): List<EngineCommand> {
        val next = index + 1
        return if (next >= intervals.size) finish(RunOutcome.Completed) else {
            beginInterval(next, Speech.Auto, startBelt = false)
        }
    }

    private fun finish(outcome: RunOutcome): List<EngineCommand> {
        state = EngineState.Finished(outcome)
        speedHeld = false
        heldKmh = null
        return if (outcome == RunOutcome.Completed) {
            listOf(EngineCommand.Announce("Workout complete"), EngineCommand.StopBelt)
        } else {
            listOf(EngineCommand.StopBelt)
        }
    }

    private fun beginInterval(index: Int, speech: Speech, startBelt: Boolean): List<EngineCommand> {
        val interval = intervals[index]
        speedHeld = false
        heldKmh = null
        val commands = mutableListOf<EngineCommand>()
        val announce = when (speech) {
            Speech.Short -> true
            Speech.None -> false
            Speech.Auto -> index > 0 && announcedIndex != index && intervals[index - 1].durationMs < LEAD_MS
        }
        if (announce) {
            commands += EngineCommand.Announce(if (speech == Speech.Short) shortLine(interval) else nextLine(interval))
            announcedIndex = index
        }
        if (startBelt) commands += EngineCommand.StartBelt
        commands += EngineCommand.SetSpeed(interval.speedKmh)
        state = EngineState.Active(index, interval.durationMs)
        val next = intervals.getOrNull(index + 1)
        if (next != null && interval.durationMs >= LEAD_MS && interval.durationMs <= LEAD_MS && announcedIndex != index + 1) {
            commands += EngineCommand.Announce(nextLine(next))
            announcedIndex = index + 1
        }
        return commands
    }

    private fun cues(index: Int, before: Long, after: Long): List<EngineCommand> {
        val commands = mutableListOf<EngineCommand>()
        val nextIndex = index + 1
        val next = intervals.getOrNull(nextIndex) ?: return commands
        if (intervals[index].durationMs >= LEAD_MS && announcedIndex != nextIndex && before > LEAD_MS && after <= LEAD_MS) {
            commands += EngineCommand.Announce(nextLine(next))
            announcedIndex = nextIndex
        }
        listOf(3_000L, 2_000L, 1_000L).forEach { mark ->
            if (before > mark && after <= mark) commands += EngineCommand.Beep
        }
        return commands
    }

    private fun runningSpeed(index: Int): Double = if (speedHeld) heldKmh ?: intervals[index].speedKmh else intervals[index].speedKmh

    private enum class Speech { None, Auto, Short }

    private companion object {
        const val COUNTDOWN_MS = 3_000L
        const val LEAD_MS = 10_000L

        fun nextLine(interval: IntervalPlan): String =
            "Next: ${interval.stage.spoken}, ${speedWords(interval.speedKmh)}"

        fun shortLine(interval: IntervalPlan): String =
            "${interval.stage.spoken}, ${speedWords(interval.speedKmh)}"

        fun speedWords(kmh: Double): String = "%.1f kilometers per hour".format(Locale.US, kmh)
    }
}
