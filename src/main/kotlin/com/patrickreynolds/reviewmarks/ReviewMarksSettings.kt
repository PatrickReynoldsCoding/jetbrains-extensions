package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

class ReviewMarksSettingsState {
    var pauseRemindersOn: Boolean = false
    var minutesBetweenPauses: Int = 30
    var blocksBetweenPauses: Int = 0
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
