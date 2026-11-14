package com.patrickreynolds.reviewmarks

private const val MILLISECONDS_PER_MINUTE = 60_000L

enum class PomodoroPhase(val label: String) { WORKING("Reviewing"), ON_BREAK("Break") }

sealed interface PomodoroState {
    val phase: PomodoroPhase

    data class Running(override val phase: PomodoroPhase, val phaseStartedAtMillis: Long) : PomodoroState

    data class Paused(override val phase: PomodoroPhase, val millisSpentInPhase: Long) : PomodoroState
}

data class PomodoroLengths(
    val workMinutes: Int,
    val breakMinutes: Int,
) {
    fun minutesOf(phase: PomodoroPhase): Int = if (phase == PomodoroPhase.WORKING) workMinutes else breakMinutes
}

data class PomodoroStep(
    val stateAfterStep: PomodoroState?,
    val message: String?,
)

fun startedPomodoro(nowMillis: Long): PomodoroState = PomodoroState.Running(PomodoroPhase.WORKING, nowMillis)

fun pausedPomodoro(running: PomodoroState.Running, nowMillis: Long): PomodoroState =
    PomodoroState.Paused(running.phase, nowMillis - running.phaseStartedAtMillis)

fun resumedPomodoro(paused: PomodoroState.Paused, nowMillis: Long): PomodoroState =
    PomodoroState.Running(paused.phase, nowMillis - paused.millisSpentInPhase)

fun stepPomodoro(stateBeforeStep: PomodoroState?, nowMillis: Long, lengths: PomodoroLengths): PomodoroStep {
    if (stateBeforeStep !is PomodoroState.Running) return PomodoroStep(stateBeforeStep, message = null)
    val minutesInPhase = (nowMillis - stateBeforeStep.phaseStartedAtMillis) / MILLISECONDS_PER_MINUTE
    if (minutesInPhase < lengths.minutesOf(stateBeforeStep.phase)) return PomodoroStep(stateBeforeStep, message = null)
    return when (stateBeforeStep.phase) {
        PomodoroPhase.WORKING -> PomodoroStep(PomodoroState.Running(PomodoroPhase.ON_BREAK, nowMillis), breakStartsMessage(lengths))
        PomodoroPhase.ON_BREAK -> PomodoroStep(PomodoroState.Running(PomodoroPhase.WORKING, nowMillis), breakOverMessage(lengths))
    }
}

fun minutesLeftInPhase(state: PomodoroState, nowMillis: Long, lengths: PomodoroLengths): Long {
    val millisSpent = when (state) {
        is PomodoroState.Running -> nowMillis - state.phaseStartedAtMillis
        is PomodoroState.Paused -> state.millisSpentInPhase
    }
    return maxOf(0L, lengths.minutesOf(state.phase) - millisSpent / MILLISECONDS_PER_MINUTE)
}

fun pomodoroStatusText(state: PomodoroState?, nowMillis: Long, lengths: PomodoroLengths): String {
    if (state == null) return "${lengths.workMinutes} min reviewing, ${lengths.breakMinutes} min break"
    val minutesLeft = minutesLeftInPhase(state, nowMillis, lengths)
    val pausedText = if (state is PomodoroState.Paused) "Paused · " else ""
    return "$pausedText${state.phase.label} · $minutesLeft min left"
}

data class PomodoroSession(
    val completedReviewPeriods: Int,
    val millisReviewedInEarlierPeriods: Long,
    val reviewedLinesAtStart: Int,
)

fun millisReviewing(state: PomodoroState?, session: PomodoroSession, nowMillis: Long): Long {
    val millisInCurrentReviewPeriod = when {
        state == null || state.phase != PomodoroPhase.WORKING -> 0L
        state is PomodoroState.Running -> nowMillis - state.phaseStartedAtMillis
        state is PomodoroState.Paused -> state.millisSpentInPhase
        else -> 0L
    }
    return session.millisReviewedInEarlierPeriods + millisInCurrentReviewPeriod
}

fun sessionAfterStep(session: PomodoroSession, stateBeforeStep: PomodoroState?, stateAfterStep: PomodoroState?, nowMillis: Long): PomodoroSession {
    val reviewPeriodEnded = stateBeforeStep is PomodoroState.Running &&
        stateBeforeStep.phase == PomodoroPhase.WORKING &&
        stateAfterStep?.phase == PomodoroPhase.ON_BREAK
    if (!reviewPeriodEnded) return session
    return session.copy(
        completedReviewPeriods = session.completedReviewPeriods + 1,
        millisReviewedInEarlierPeriods = session.millisReviewedInEarlierPeriods + (nowMillis - stateBeforeStep.phaseStartedAtMillis),
    )
}

fun pausedAfterRestart(savedState: PomodoroState?, nowMillis: Long, lengths: PomodoroLengths): PomodoroState? = when (savedState) {
    is PomodoroState.Running -> PomodoroState.Paused(
        savedState.phase,
        (nowMillis - savedState.phaseStartedAtMillis).coerceIn(0L, lengths.minutesOf(savedState.phase) * MILLISECONDS_PER_MINUTE),
    )
    else -> savedState
}

fun pomodoroSummaryText(minutesReviewing: Long, completedReviewPeriods: Int, linesReviewed: Int): String {
    val periodsText = if (completedReviewPeriods == 1) "1 full review period" else "$completedReviewPeriods full review periods"
    val linesText = if (linesReviewed == 1) "1 line" else "$linesReviewed lines"
    return "Session finished. You reviewed for $minutesReviewing minutes ($periodsText) and marked $linesText as reviewed. Well done. Rest now."
}

private fun breakStartsMessage(lengths: PomodoroLengths) =
    "Time for a ${lengths.breakMinutes} minute break. Step away from the screen. Your marks are saved."

private fun breakOverMessage(lengths: PomodoroLengths) =
    "Break over. A new ${lengths.workMinutes} minute review period starts now. Press Where I stopped to pick up where you were."
