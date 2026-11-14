package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

private const val DIALOG_TITLE = "Mark Uncommitted Changes"

class MarkUncommittedChangesAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectFolder = project.basePath
        if (projectFolder == null) {
            Messages.showErrorDialog(project, "This project has no folder on disk.", DIALOG_TITLE)
            return
        }
        markChangedLinesToReviewWithProgress(
            project = project,
            dialogTitle = DIALOG_TITLE,
            progressTitle = "Finding what is not committed",
            doneMessage = "Marked every uncommitted line to review, including new files git does not track yet.",
            findChangedLines = { GitUncommittedChanges.changedLinesNotCommitted(projectFolder) },
        )
    }
}
