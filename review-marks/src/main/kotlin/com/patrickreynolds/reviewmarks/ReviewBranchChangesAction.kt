package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory

private const val ERROR_TITLE = "Review Branch Changes"

class ReviewBranchChangesAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectFolder = project.basePath
        if (projectFolder == null) {
            Messages.showErrorDialog(project, "This project has no folder on disk.", ERROR_TITLE)
            return
        }
        val branches = when (val result = GitBranches.list(projectFolder)) {
            is BranchListResult.Failed -> {
                Messages.showErrorDialog(project, "${result.git.command}\n\n${result.git.errorOutput}", ERROR_TITLE)
                return
            }
            is BranchListResult.Found -> result.branches
        }
        chooseBaseBranch(project, branches) { baseBranch -> markChangesSince(project, projectFolder, baseBranch) }
    }

    private fun chooseBaseBranch(project: Project, branches: List<String>, onChosen: (String) -> Unit) {
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(branches)
            .setTitle("Review changes since which branch? Type to search")
            .setNamerForFiltering { it }
            .setItemChosenCallback(onChosen)
        suggestedBaseBranch(branches)?.let { popup.setSelectedValue(it, true) }
        popup.createPopup().showCenteredInCurrentWindow(project)
    }

    private fun markChangesSince(project: Project, projectFolder: String, baseBranch: String) {
        markChangedLinesToReviewWithProgress(
            project = project,
            dialogTitle = ERROR_TITLE,
            progressTitle = "Finding what changed since $baseBranch",
            doneMessage = "Marked the lines changed since $baseBranch to review.",
            findChangedLines = { GitBranchChanges.changedLinesSince(projectFolder, baseBranch) },
        )
    }
}
