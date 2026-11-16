package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

class ReviewMarksSettingsState {
    var workMinutes: Int = 25
    var breakMinutes: Int = 5
    var startBreaksAutomatically: Boolean = false
    var defaultBaseBranch: String = "main"
    var notesFloatByDefault: Boolean = true
    var syncSidebarWithEditor: Boolean = true
    var gitHubHostAliases: String = "github-personal=github.com"
    var focusAlertEnabled: Boolean = true
    var focusAlertMessage: String = DEFAULT_FOCUS_ALERT_MESSAGE
    var focusAlertCooldownSeconds: Int = DEFAULT_FOCUS_ALERT_COOLDOWN_SECONDS
    var focusAlertSoundEnabled: Boolean = false
    var focusAlertSoundName: String = DEFAULT_FOCUS_ALERT_SOUND_NAME
    var sidebarTabOrder: MutableList<String> = DEFAULT_SIDEBAR_TAB_ORDER.toMutableList()
    var hiddenSidebarTabs: MutableList<String> = mutableListOf()

    fun pomodoroLengths() = PomodoroLengths(workMinutes, breakMinutes)
}

@Service(Service.Level.APP)
@State(name = "ReviewMarksSettings", storages = [Storage("review-marks.xml")])
class ReviewMarksSettings : PersistentStateComponent<ReviewMarksSettingsState> {

    private var settingsState = ReviewMarksSettingsState()

    override fun getState(): ReviewMarksSettingsState = settingsState

    override fun loadState(state: ReviewMarksSettingsState) {
        settingsState = state
    }
}
