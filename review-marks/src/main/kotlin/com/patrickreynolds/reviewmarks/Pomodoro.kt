package com.patrickreynolds.reviewmarks

private const val MILLISECONDS_PER_MINUTE = 60_000L

enum class PomodoroPhase { WORKING, ON_BREAK }

data class PomodoroState(
    val phase: PomodoroPhase,
    val phaseStartedAtMillis: Long,
)

data class PomodoroLengths(
    val workMinutes: Int,
    val breakMinutes: Int,
)

data class PomodoroStep(
    val stateAfterStep: PomodoroState?,
    val message: String?,
)

fun stepPomodoro(
    stateBeforeStep: PomodoroState?,
    nowMillis: Long,
    remindersOn: Boolean,
    lengths: PomodoroLengths,
): PomodoroStep {
    if (!remindersOn) return PomodoroStep(stateAfterStep = null, message = null)
    if (stateBeforeStep == null) return PomodoroStep(PomodoroState(PomodoroPhase.WORKING, nowMillis), message = null)

    val minutesInPhase = (nowMillis - stateBeforeStep.phaseStartedAtMillis) / MILLISECONDS_PER_MINUTE
    return when (stateBeforeStep.phase) {
        PomodoroPhase.WORKING -> if (minutesInPhase >= lengths.workMinutes) {
            PomodoroStep(PomodoroState(PomodoroPhase.ON_BREAK, nowMillis), breakStartsMessage(lengths))
        } else {
            PomodoroStep(stateBeforeStep, message = null)
        }
        PomodoroPhase.ON_BREAK -> if (minutesInPhase >= lengths.breakMinutes) {
            PomodoroStep(PomodoroState(PomodoroPhase.WORKING, nowMillis), breakOverMessage(lengths))
        } else {
            PomodoroStep(stateBeforeStep, message = null)
        }
    }
}

fun minutesLeftInPhase(state: PomodoroState, nowMillis: Long, lengths: PomodoroLengths): Long {
    val phaseMinutes = if (state.phase == PomodoroPhase.WORKING) lengths.workMinutes else lengths.breakMinutes
    val minutesPassed = (nowMillis - state.phaseStartedAtMillis) / MILLISECONDS_PER_MINUTE
    return maxOf(0L, phaseMinutes - minutesPassed)
}

fun pomodoroLengthsText(lengths: PomodoroLengths): String =
    "${lengths.workMinutes} min work, ${lengths.breakMinutes} min break"

private fun breakStartsMessage(lengths: PomodoroLengths) =
    "Time for a ${lengths.breakMinutes} minute break. Step away from the screen. Your marks are saved."

private fun breakOverMessage(lengths: PomodoroLengths) =
    "Break over. A new ${lengths.workMinutes} minute work period starts now. Press Where I stopped to pick up where you were."
