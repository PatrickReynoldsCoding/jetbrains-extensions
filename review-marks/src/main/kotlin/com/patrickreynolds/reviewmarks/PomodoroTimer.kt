package com.patrickreynolds.reviewmarks

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val SECONDS_BETWEEN_CHECKS = 10L
private const val MILLISECONDS_PER_MINUTE = 60_000L

class PomodoroTimerState {
    var phase: String = ""
    var running: Boolean = false
    var phaseStartedAtMillis: Long = 0
    var millisSpentInPhase: Long = 0
    var completedReviewPeriods: Int = 0
    var millisReviewedInEarlierPeriods: Long = 0
    var reviewedLinesAtStart: Int = 0
}

@Service(Service.Level.PROJECT)
@State(name = "ReviewMarksPomodoro", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class PomodoroTimer(private val project: Project) : PersistentStateComponent<PomodoroTimerState>, Disposable {

    var pomodoroState: PomodoroState? = null
        @Synchronized get
        private set

    private var session = PomodoroSession(completedReviewPeriods = 0, millisReviewedInEarlierPeriods = 0, reviewedLinesAtStart = 0)
    private var scheduledChecks: ScheduledFuture<*>? = null

    @Synchronized
    fun start(nowMillis: Long) {
        pomodoroState = startedPomodoro(nowMillis)
        session = PomodoroSession(0, 0, reviewedLinesToday())
        scheduleChecks()
        announceChange()
    }

    @Synchronized
    fun pauseOrResume(nowMillis: Long) {
        pomodoroState = when (val currentState = pomodoroState) {
            is PomodoroState.Running -> pausedPomodoro(currentState, nowMillis)
            is PomodoroState.Paused -> resumedPomodoro(currentState, nowMillis).also { scheduleChecks() }
            null -> null
        }
        announceChange()
    }

    @Synchronized
    fun finish(nowMillis: Long): String? {
        val finishedState = pomodoroState ?: return null
        val minutesReviewing = millisReviewing(finishedState, session, nowMillis) / MILLISECONDS_PER_MINUTE
        val linesReviewed = (reviewedLinesToday() - session.reviewedLinesAtStart).coerceAtLeast(0)
        pomodoroState = null
        stopChecks()
        announceChange()
        return pomodoroSummaryText(minutesReviewing, session.completedReviewPeriods, linesReviewed)
    }

    @Synchronized
    fun checkNow(nowMillis: Long) {
        val stateBeforeStep = pomodoroState
        val step = stepPomodoro(stateBeforeStep, nowMillis, lengths())
        pomodoroState = step.stateAfterStep
        session = sessionAfterStep(session, stateBeforeStep, step.stateAfterStep, nowMillis)
        step.message?.let { message ->
            ApplicationManager.getApplication().invokeLater({ showReviewMarksMessage(project, message) }, project.disposed)
            announceChange()
        }
    }

    @Synchronized
    fun statusText(nowMillis: Long): String = pomodoroStatusText(pomodoroState, nowMillis, lengths())

    @Synchronized
    override fun getState(): PomodoroTimerState {
        val savedState = PomodoroTimerState()
        val currentState = pomodoroState ?: return savedState
        savedState.phase = currentState.phase.name
        when (currentState) {
            is PomodoroState.Running -> {
                savedState.running = true
                savedState.phaseStartedAtMillis = currentState.phaseStartedAtMillis
            }
            is PomodoroState.Paused -> savedState.millisSpentInPhase = currentState.millisSpentInPhase
        }
        savedState.completedReviewPeriods = session.completedReviewPeriods
        savedState.millisReviewedInEarlierPeriods = session.millisReviewedInEarlierPeriods
        savedState.reviewedLinesAtStart = session.reviewedLinesAtStart
        return savedState
    }

    @Synchronized
    override fun loadState(state: PomodoroTimerState) {
        val savedPhase = PomodoroPhase.entries.firstOrNull { it.name == state.phase }
        val savedState = when {
            savedPhase == null -> null
            state.running -> PomodoroState.Running(savedPhase, state.phaseStartedAtMillis)
            else -> PomodoroState.Paused(savedPhase, state.millisSpentInPhase)
        }
        pomodoroState = pausedAfterRestart(savedState, System.currentTimeMillis(), lengths())
        session = PomodoroSession(state.completedReviewPeriods, state.millisReviewedInEarlierPeriods, state.reviewedLinesAtStart)
    }

    @Synchronized
    override fun dispose() {
        stopChecks()
    }

    private fun reviewedLinesToday(): Int = project.service<ReviewMarksService>().reviewSummaryToday().reviewedLineCountToday

    private fun lengths() = service<ReviewMarksSettings>().state.pomodoroLengths()

    private fun scheduleChecks() {
        if (scheduledChecks != null) return
        scheduledChecks = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { checkNow(System.currentTimeMillis()) },
            SECONDS_BETWEEN_CHECKS,
            SECONDS_BETWEEN_CHECKS,
            TimeUnit.SECONDS,
        )
    }

    private fun stopChecks() {
        scheduledChecks?.cancel(false)
        scheduledChecks = null
    }

    private fun announceChange() {
        ApplicationManager.getApplication().invokeLater(
            { project.messageBus.syncPublisher(ReviewStateListener.TOPIC).reviewStateChanged() },
            project.disposed,
        )
    }
}
