package com.patrickreynolds.reviewmarks

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val SECONDS_BETWEEN_CHECKS = 10L

@Service(Service.Level.PROJECT)
class PomodoroTimer(private val project: Project) : Disposable {

    private var pomodoroState: PomodoroState? = null
    private var scheduledChecks: ScheduledFuture<*>? = null

    @Synchronized
    fun startChecking() {
        if (scheduledChecks != null) return
        scheduledChecks = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { checkNow(System.currentTimeMillis()) },
            SECONDS_BETWEEN_CHECKS,
            SECONDS_BETWEEN_CHECKS,
            TimeUnit.SECONDS,
        )
    }

    @Synchronized
    fun checkNow(nowMillis: Long) {
        val pauseSettings = service<ReviewMarksSettings>().state
        val step = stepPomodoro(pomodoroState, nowMillis, pauseSettings.pauseRemindersOn, pauseSettings.pomodoroLengths())
        pomodoroState = step.stateAfterStep
        step.message?.let { message ->
            ApplicationManager.getApplication().invokeLater({ showPauseReminder(project, message) }, project.disposed)
        }
    }

    @Synchronized
    fun minutesLeftText(nowMillis: Long): String {
        val currentState = pomodoroState ?: return ""
        val minutesLeft = minutesLeftInPhase(currentState, nowMillis, service<ReviewMarksSettings>().state.pomodoroLengths())
        val phaseName = if (currentState.phase == PomodoroPhase.WORKING) "Working" else "Break"
        return "$phaseName: $minutesLeft min left"
    }

    @Synchronized
    override fun dispose() {
        scheduledChecks?.cancel(false)
        scheduledChecks = null
    }
}
