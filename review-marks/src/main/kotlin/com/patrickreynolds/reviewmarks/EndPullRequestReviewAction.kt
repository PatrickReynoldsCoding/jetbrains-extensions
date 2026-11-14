package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class EndPullRequestReviewAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project?.service<ReviewMarksService>()?.pullRequest != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val confirmed = Messages.showYesNoDialog(
            project,
            "End the PR review without posting? Your marks and notes stay, but they will no longer be sent as a review.",
            "End PR Review",
            null,
        ) == Messages.YES
        if (confirmed) project.service<ReviewMarksService>().endPullRequestReview()
    }
}
