package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

private const val MOST_MINUTES_BETWEEN_PAUSES = 600
private const val MOST_BLOCKS_BETWEEN_PAUSES = 1000

class ReviewMarksConfigurable : BoundConfigurable("Review Marks") {

    override fun createPanel(): DialogPanel {
        val settingsState = service<ReviewMarksSettings>().state
        return panel {
            group("Pause reminders") {
                row {
                    checkBox("Remind me to pause while I review")
                        .bindSelected(settingsState::pauseRemindersOn)
                        .comment("A quiet message appears in the corner. Nothing is blocked. Reminders are off until you turn them on.")
                }
                row("Remind me after this many minutes of reviewing:") {
                    intTextField(0..MOST_MINUTES_BETWEEN_PAUSES).bindIntText(settingsState::minutesBetweenPauses)
                        .comment("0 turns this off. If you stop marking code for this long, the count starts again.")
                }
                row("Remind me after this many reviewed blocks:") {
                    intTextField(0..MOST_BLOCKS_BETWEEN_PAUSES).bindIntText(settingsState::blocksBetweenPauses)
                        .comment("0 turns this off.")
                }
            }
        }
    }
}
