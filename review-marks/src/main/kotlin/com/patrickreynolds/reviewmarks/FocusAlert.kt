package com.patrickreynolds.reviewmarks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFrame

const val DEFAULT_FOCUS_ALERT_MESSAGE = "Hey! keep focus, you're supposed to be reviewing!"
const val DEFAULT_FOCUS_ALERT_COOLDOWN_SECONDS = 30
const val DEFAULT_FOCUS_ALERT_SOUND_NAME = "Glass"
val MAC_ALERT_SOUND_NAMES = listOf(
    "Basso", "Blow", "Bottle", "Frog", "Funk", "Glass", "Hero", "Morse", "Ping", "Pop", "Purr", "Sosumi", "Submarine", "Tink",
)

private const val MILLISECONDS_PER_SECOND = 1000L

fun cooldownHasPassed(lastAlertAtMillis: Long?, nowMillis: Long, cooldownSeconds: Int): Boolean =
    lastAlertAtMillis == null || nowMillis - lastAlertAtMillis >= cooldownSeconds * MILLISECONDS_PER_SECOND

fun shouldSendFocusAlert(
    alertEnabled: Boolean,
    reviewing: Boolean,
    lastAlertAtMillis: Long?,
    nowMillis: Long,
    cooldownSeconds: Int,
): Boolean = alertEnabled && reviewing && cooldownHasPassed(lastAlertAtMillis, nowMillis, cooldownSeconds)

fun focusAlertSoundName(soundEnabled: Boolean, soundName: String): String? =
    soundName.takeIf { soundEnabled }

fun isReviewing(pomodoroState: PomodoroState?): Boolean =
    pomodoroState is PomodoroState.Running && pomodoroState.phase == PomodoroPhase.WORKING

@Service(Service.Level.APP)
class FocusAlert {

    private var lastAlertAtMillis: Long? = null

    @Synchronized
    fun alertIfNeeded(nowMillis: Long) {
        val settingsState = service<ReviewMarksSettings>().state
        val openProjects = ProjectManager.getInstance().openProjects
        val reviewing = openProjects.any { isReviewing(it.service<PomodoroTimer>().pomodoroState) }
        val due = shouldSendFocusAlert(
            alertEnabled = settingsState.focusAlertEnabled,
            reviewing = reviewing,
            lastAlertAtMillis = lastAlertAtMillis,
            nowMillis = nowMillis,
            cooldownSeconds = settingsState.focusAlertCooldownSeconds,
        )
        val project = openProjects.firstOrNull()
        if (!due || project == null) return
        lastAlertAtMillis = nowMillis
        val soundName = focusAlertSoundName(settingsState.focusAlertSoundEnabled, settingsState.focusAlertSoundName)
        showReviewMarksMessage(project, settingsState.focusAlertMessage, soundName)
    }
}

class FocusAlertListener : ApplicationActivationListener {
    override fun applicationDeactivated(ideFrame: IdeFrame) {
        ApplicationManager.getApplication().service<FocusAlert>().alertIfNeeded(System.currentTimeMillis())
    }
}
