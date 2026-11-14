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
            group("Pomodoro") {
                row("Review for this many minutes:") {
                    intTextField(1..MOST_WORK_MINUTES).bindIntText(settingsState::workMinutes)
                }
                row("Then take a break of this many minutes:") {
                    intTextField(1..MOST_BREAK_MINUTES).bindIntText(settingsState::breakMinutes)
                }
            }
            group("Notes and comments") {
                row {
                    checkBox("Show new notes and comments above the code")
                        .bindSelected(settingsState::notesFloatByDefault)
                        .comment("You can still change it for each note.")
                }
            }
            group("Review from git") {
                row("Branch to compare with:") {
                    textField().bindText(settingsState::defaultBaseBranch)
                        .comment("Used by Mark All Branch Changes to Review.")
                }
                row("GitHub host aliases:") {
                    textField().bindText(settingsState::gitHubHostAliases)
                        .comment("For SSH host aliases in your remotes, as alias=host, split by commas. For example github-personal=github.com.")
                }
            }
        }
    }
}
