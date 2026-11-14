package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable

fun markChangedLinesToReviewWithProgress(
    project: Project,
    dialogTitle: String,
    progressTitle: String,
    doneMessage: String,
    findChangedLines: () -> BranchChangesResult,
) {
    val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable<BranchChangesResult, RuntimeException> { findChangedLines() },
        progressTitle,
        true,
        project,
    )
    when (result) {
        is BranchChangesResult.Failed ->
            Messages.showErrorDialog(project, "${result.git.command}\n\n${result.git.errorOutput}", dialogTitle)
        is BranchChangesResult.Found -> {
            val markedFileCount = project.service<ReviewMarksService>().markChangedLinesToReview(localFilesByPath(result.changedLinesByPath))
            Messages.showInfoMessage(
                project,
                "$doneMessage In $markedFileCount files. Lines you had already marked were left as they were.",
                dialogTitle,
            )
        }
    }
}
