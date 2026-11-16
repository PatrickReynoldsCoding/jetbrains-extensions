package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

private const val MOST_WORK_MINUTES = 240
private const val MOST_BREAK_MINUTES = 60
private const val MOST_FOCUS_ALERT_COOLDOWN_SECONDS = 3600

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
                row {
                    checkBox("Start breaks automatically")
                        .bindSelected(settingsState::startBreaksAutomatically)
                        .comment("Off: press Start break when a review period ends.")
                }
            }
            group("Focus alert") {
                row {
                    checkBox("Send a Mac notification when the IDE loses focus during a review period")
                        .bindSelected(settingsState::focusAlertEnabled)
                }
                row("Message:") {
                    textField().bindText(settingsState::focusAlertMessage).columns(COLUMNS_LARGE)
                }
                row("At most once every this many seconds:") {
                    intTextField(1..MOST_FOCUS_ALERT_COOLDOWN_SECONDS).bindIntText(settingsState::focusAlertCooldownSeconds)
                }
                row {
                    checkBox("Play a sound with the alert")
                        .bindSelected(settingsState::focusAlertSoundEnabled)
                        .comment("Off by default.")
                    comboBox(MAC_ALERT_SOUND_NAMES).bindItem(settingsState::focusAlertSoundName.toNullableProperty())
                }
            }
            group("Notes and comments") {
                row {
                    checkBox("Show new notes and comments above the code")
                        .bindSelected(settingsState::notesFloatByDefault)
                        .comment("You can still change it for each note.")
                }
            }
            group("Sidebar") {
                row {
                    checkBox("Sync sidebar with editor (highlight and scroll)")
                        .bindSelected(settingsState::syncSidebarWithEditor)
                        .comment("Highlight the open file, and the block under a click, in the To review and Reviewed tabs.")
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
