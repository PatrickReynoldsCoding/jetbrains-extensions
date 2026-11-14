package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

private const val MOST_WORK_MINUTES = 240
private const val MOST_BREAK_MINUTES = 60

class ReviewMarksConfigurable : BoundConfigurable("Review Marks") {

    override fun createPanel(): DialogPanel {
        val settingsState = service<ReviewMarksSettings>().state
        return panel {
            group("Pomodoro reminders") {
                row {
                    checkBox("Remind me to take breaks while I review")
                        .bindSelected(settingsState::pauseRemindersOn)
                        .comment("A message stays in the corner until you close it. Nothing is blocked. Reminders are off until you turn them on. Work and break periods repeat.")
                }
                row("Work for this many minutes:") {
                    intTextField(1..MOST_WORK_MINUTES).bindIntText(settingsState::workMinutes)
                }
                row("Then take a break of this many minutes:") {
                    intTextField(1..MOST_BREAK_MINUTES).bindIntText(settingsState::breakMinutes)
                }
            }
            group("Review from git") {
                row("Branch to compare with:") {
                    textField().bindText(settingsState::defaultBaseBranch)
                        .comment("Used by the All branch changes button.")
                }
            }
        }
    }
}
