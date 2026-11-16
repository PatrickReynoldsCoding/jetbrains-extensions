package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroTest {

    private val minute = 60_000L
    private val lengths = PomodoroLengths(workMinutes = 25, breakMinutes = 5)

    @Test
    fun `starting begins a review period`() {
        assertEquals(PomodoroState.Running(PomodoroPhase.WORKING, 10 * minute), startedPomodoro(10 * minute))
    }

    @Test
    fun `does nothing before it is started`() {
        val step = stepPomodoro(null, nowMillis = 99 * minute, lengths = lengths, startBreaksAutomatically = false)

        assertNull(step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `keeps reviewing with no message before the review minutes are up`() {
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)

        val step = stepPomodoro(reviewing, nowMillis = 24 * minute, lengths = lengths, startBreaksAutomatically = false)

        assertEquals(reviewing, step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `waits for the break to be started when the review minutes are up`() {
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)

        val step = stepPomodoro(reviewing, nowMillis = 25 * minute, lengths = lengths, startBreaksAutomatically = false)

        assertEquals(PomodoroState.AwaitingBreak, step.stateAfterStep)
        assertTrue(step.message!!.startsWith("Review period over. Press Start break"))
    }

    @Test
    fun `starts the break by itself when the review minutes are up and breaks start automatically`() {
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)

        val step = stepPomodoro(reviewing, nowMillis = 25 * minute, lengths = lengths, startBreaksAutomatically = true)

        assertEquals(PomodoroState.Running(PomodoroPhase.ON_BREAK, 25 * minute), step.stateAfterStep)
        assertTrue(step.message!!.startsWith("Time for a 5 minute break"))
    }

    @Test
    fun `a break that is waiting to start stays waiting however long it waits`() {
        val step = stepPomodoro(PomodoroState.AwaitingBreak, nowMillis = 500 * minute, lengths = lengths, startBreaksAutomatically = false)

        assertEquals(PomodoroState.AwaitingBreak, step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `starting the break and finishing it early move to the break and back to reviewing`() {
        assertEquals(PomodoroState.Running(PomodoroPhase.ON_BREAK, 30 * minute), startedBreak(30 * minute))
        assertEquals(PomodoroState.Running(PomodoroPhase.WORKING, 32 * minute), finishedBreak(32 * minute))
    }

    @Test
    fun `a waiting break is described and counts as a finished review period`() {
        assertEquals("Break ready · press Start break", pomodoroStatusText(PomodoroState.AwaitingBreak, 0, lengths))
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)

        val session = sessionAfterStep(freshSession, reviewing, PomodoroState.AwaitingBreak, nowMillis = 25 * minute)

        assertEquals(PomodoroSession(1, 25 * minute, 40), session)
        assertEquals(25 * minute, millisReviewing(PomodoroState.AwaitingBreak, session, 99 * minute))
    }

    @Test
    fun `starts the next review period when the break is over`() {
        val onBreak = PomodoroState.Running(PomodoroPhase.ON_BREAK, 25 * minute)

        val step = stepPomodoro(onBreak, nowMillis = 30 * minute, lengths = lengths, startBreaksAutomatically = false)

        assertEquals(PomodoroState.Running(PomodoroPhase.WORKING, 30 * minute), step.stateAfterStep)
        assertTrue(step.message!!.startsWith("Break over. A new 25 minute review period"))
    }

    @Test
    fun `a paused timer does not move on however long it waits`() {
        val paused = PomodoroState.Paused(PomodoroPhase.WORKING, millisSpentInPhase = 20 * minute)

        val step = stepPomodoro(paused, nowMillis = 500 * minute, lengths = lengths, startBreaksAutomatically = false)

        assertEquals(paused, step.stateAfterStep)
        assertNull(step.message)
    }

    @Test
    fun `pausing and resuming keeps the minutes already spent in the period`() {
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)

        val paused = pausedPomodoro(reviewing, nowMillis = 20 * minute) as PomodoroState.Paused
        val resumed = resumedPomodoro(paused, nowMillis = 100 * minute)

        assertEquals(PomodoroState.Running(PomodoroPhase.WORKING, 80 * minute), resumed)
        assertEquals(5L, minutesLeftInPhase(resumed, nowMillis = 100 * minute, lengths = lengths))
    }

    @Test
    fun `describes the timer before it starts, while it runs and while it is paused`() {
        assertEquals("25 min reviewing, 5 min break", pomodoroStatusText(null, 0, lengths))
        assertEquals(
            "Reviewing · 10 min left",
            pomodoroStatusText(PomodoroState.Running(PomodoroPhase.WORKING, 0), 15 * minute, lengths),
        )
        assertEquals(
            "Paused · Break · 3 min left",
            pomodoroStatusText(PomodoroState.Paused(PomodoroPhase.ON_BREAK, 2 * minute), 99 * minute, lengths),
        )
    }

    private val freshSession = PomodoroSession(completedReviewPeriods = 0, millisReviewedInEarlierPeriods = 0, reviewedLinesAtStart = 40)

    @Test
    fun `counts a finished review period and its minutes when the break starts`() {
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)
        val onBreak = PomodoroState.Running(PomodoroPhase.ON_BREAK, 25 * minute)

        val session = sessionAfterStep(freshSession, reviewing, onBreak, nowMillis = 25 * minute)

        assertEquals(PomodoroSession(1, 25 * minute, 40), session)
    }

    @Test
    fun `adds the minutes of the current review period to the earlier ones`() {
        val session = PomodoroSession(1, 25 * minute, 40)

        assertEquals(35 * minute, millisReviewing(PomodoroState.Running(PomodoroPhase.WORKING, 100 * minute), session, 110 * minute))
        assertEquals(25 * minute, millisReviewing(PomodoroState.Running(PomodoroPhase.ON_BREAK, 100 * minute), session, 110 * minute))
        assertEquals(30 * minute, millisReviewing(PomodoroState.Paused(PomodoroPhase.WORKING, 5 * minute), session, 999 * minute))
    }

    @Test
    fun `a timer that was running when the IDE closed comes back paused with the time it had used`() {
        val reviewing = PomodoroState.Running(PomodoroPhase.WORKING, 0)

        assertEquals(PomodoroState.Paused(PomodoroPhase.WORKING, 10 * minute), pausedAfterRestart(reviewing, 10 * minute, lengths))
        assertEquals(PomodoroState.Paused(PomodoroPhase.WORKING, 25 * minute), pausedAfterRestart(reviewing, 900 * minute, lengths))
    }

    @Test
    fun `sums up the session when it is finished`() {
        assertEquals(
            "Session finished. You reviewed for 52 minutes (2 full review periods) and marked 140 lines as reviewed. Well done. Rest now.",
            pomodoroSummaryText(minutesReviewing = 52, completedReviewPeriods = 2, linesReviewed = 140),
        )
    }
}
