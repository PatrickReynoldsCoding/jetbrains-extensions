package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PomodoroTest {

    private val minute = 60_000L
    private val lengths = PomodoroLengths(workMinutes = 25, breakMinutes = 5)

    @Test
    fun `turning reminders on starts a work period with no message`() {
        val step = stepPomodoro(null, nowMillis = 10 * minute, remindersOn = true, lengths = lengths)

        assertEquals(PomodoroState(PomodoroPhase.WORKING, 10 * minute), step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `turning reminders off forgets the period`() {
        val working = PomodoroState(PomodoroPhase.WORKING, 0)

        val step = stepPomodoro(working, nowMillis = 3 * minute, remindersOn = false, lengths = lengths)

        assertNull(step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `keeps working with no message before the work minutes are up`() {
        val working = PomodoroState(PomodoroPhase.WORKING, 0)

        val step = stepPomodoro(working, nowMillis = 24 * minute, remindersOn = true, lengths = lengths)

        assertEquals(working, step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `starts the break when the work minutes are up and says how long it is`() {
        val working = PomodoroState(PomodoroPhase.WORKING, 0)

        val step = stepPomodoro(working, nowMillis = 25 * minute, remindersOn = true, lengths = lengths)

        assertEquals(PomodoroState(PomodoroPhase.ON_BREAK, 25 * minute), step.stateAfterStep)
        assertEquals(true, step.message!!.startsWith("Time for a 5 minute break"))
    }

    @Test
    fun `stays on the break with no message until the break minutes are up`() {
        val onBreak = PomodoroState(PomodoroPhase.ON_BREAK, 25 * minute)

        val step = stepPomodoro(onBreak, nowMillis = 29 * minute, remindersOn = true, lengths = lengths)

        assertEquals(onBreak, step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `starts the next work period when the break is over`() {
        val onBreak = PomodoroState(PomodoroPhase.ON_BREAK, 25 * minute)

        val step = stepPomodoro(onBreak, nowMillis = 30 * minute, remindersOn = true, lengths = lengths)

        assertEquals(PomodoroState(PomodoroPhase.WORKING, 30 * minute), step.stateAfterStep)
        assertEquals(true, step.message!!.startsWith("Break over. A new 25 minute work period"))
    }

    @Test
    fun `counts the minutes left in the current period`() {
        val working = PomodoroState(PomodoroPhase.WORKING, 0)

        assertEquals(10L, minutesLeftInPhase(working, nowMillis = 15 * minute, lengths = lengths))
        assertEquals(0L, minutesLeftInPhase(working, nowMillis = 99 * minute, lengths = lengths))
    }

    @Test
    fun `describes the lengths`() {
        assertEquals("25 min work, 5 min break", pomodoroLengthsText(lengths))
    }
}
