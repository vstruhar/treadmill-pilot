package com.threadmill.connect.ui

import com.threadmill.connect.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalReorderTest {
    @Test
    fun moveLaterByOne() {
        val moved = intervals("a", "b", "c").reordered("a", 1)
        assertEquals(listOf("b", "a", "c"), moved.ids())
    }

    @Test
    fun moveEarlierByOne() {
        val moved = intervals("a", "b", "c").reordered("c", -1)
        assertEquals(listOf("a", "c", "b"), moved.ids())
    }

    @Test
    fun moveSeveralStepsAndClampAtTheEnds() {
        val start = intervals("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), start.reordered("a", 2).ids())
        assertEquals(listOf("b", "c", "d", "a"), start.reordered("a", 10).ids())
        assertEquals(listOf("d", "a", "b", "c"), start.reordered("d", -10).ids())
    }

    @Test
    fun secondMoveFollowsTheIntervalRatherThanItsOldSlot() {
        val once = intervals("a", "b", "c", "d").reordered("b", 1)
        val twice = once.reordered("b", 1)
        assertEquals(listOf("a", "c", "d", "b"), twice.ids())
    }

    @Test
    fun unknownIdAndZeroDeltaLeaveTheOrderAlone() {
        val start = intervals("a", "b")
        assertEquals(listOf("a", "b"), start.reordered("missing", 1).ids())
        assertEquals(listOf("a", "b"), start.reordered("a", 0).ids())
    }

    private fun intervals(vararg ids: String) =
        ids.map { DraftInterval(it, Stage.Training, 60, 5.0) }

    private fun List<DraftInterval>.ids() = map { it.id }
}
