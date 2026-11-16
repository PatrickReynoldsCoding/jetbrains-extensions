package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

private const val DIALOG_TITLE = "Mark All Branch Changes"

fun markBranchChangesActionText(baseBranch: String): String = "Mark All Branch Changes to Review (vs $baseBranch)"

class MarkBranchChangesAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
        event.presentation.text = markBranchChangesActionText(service<ReviewMarksSettings>().state.defaultBaseBranch)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectFolder = project.basePath
        if (projectFolder == null) {
            Messages.showErrorDialog(project, "This project has no folder on disk.", DIALOG_TITLE)
            return
        }
        val baseBranch = service<ReviewMarksSettings>().state.defaultBaseBranch
        markChangedLinesToReviewWithProgress(
            project = project,
            dialogTitle = DIALOG_TITLE,
            progressTitle = "Finding what changed since $baseBranch",
            doneMessage = "Marked every line changed on this branch since $baseBranch to review.",
            findChangedLines = { GitBranchChanges.changedLinesSince(projectFolder, baseBranch) },
        )
    }
}
