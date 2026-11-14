package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable

private const val SUGGESTED_BASE_REF = "origin/main"

class ReviewOnlyBranchChangesAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val reviewMarks = project.service<ReviewMarksService>()

        val baseRef = Messages.showInputDialog(
            project,
            "Only lines changed since you branched from this branch or commit will count as code to review. Everything else is faded.",
            "Review Only Branch Changes",
            null,
            reviewMarks.scopeBaseRef ?: SUGGESTED_BASE_REF,
            null,
        )?.trim()
        if (baseRef.isNullOrEmpty()) return

        val projectFolder = project.basePath
        if (projectFolder == null) {
            Messages.showErrorDialog(project, "This project has no folder on disk.", "Review Only Branch Changes")
            return
        }

        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable<BranchChangesResult, RuntimeException> { GitBranchChanges.changedLinesSince(projectFolder, baseRef) },
            "Finding what changed since $baseRef",
            true,
            project,
        )
        when (result) {
            is BranchChangesResult.GitFailed ->
                Messages.showErrorDialog(project, "${result.command}\n\n${result.errorOutput}", "Review Only Branch Changes")
            is BranchChangesResult.Found -> {
                reviewMarks.startReviewingOnlyChanges(baseRef, result.changedLinesByPath)
                val changedLineCount = result.changedLinesByPath.values.sumOf { ranges -> ranges.sumOf { it.lastLine - it.firstLine + 1 } }
                Messages.showInfoMessage(
                    project,
                    "${result.changedLinesByPath.size} files and about $changedLineCount lines changed since $baseRef. Everything else is faded and is not counted.",
                    "Review Only Branch Changes",
                )
            }
        }
    }
}
