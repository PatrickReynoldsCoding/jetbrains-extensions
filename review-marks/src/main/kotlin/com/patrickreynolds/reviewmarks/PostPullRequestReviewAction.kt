package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable

private const val TITLE = "Post Review"

class PostPullRequestReviewAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project?.service<ReviewMarksService>()?.pullRequest != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val reviewMarks = project.service<ReviewMarksService>()
        val session = reviewMarks.pullRequest ?: return

        val dialog = PostReviewDialog(project, session, reviewMarks.pullRequestComments())
        if (!dialog.showAndGet()) return

        val credentials = when (val result = IdeGitHubAccount.credentialsFor(project, session.host)) {
            is GitHubCredentialsResult.Found -> result
            is GitHubCredentialsResult.NoToken -> return showError(project, "No GitHub token. Sign in under Settings, Version Control, GitHub.")
            is GitHubCredentialsResult.NoMatchingAccount -> return showError(project, "No GitHub account in the IDE for ${session.host}.")
        }
        if (credentials.accountName != session.accountName) {
            return showError(
                project,
                "The IDE account for ${session.host} is now ${credentials.accountName}, but this review was started as ${session.accountName}. Nothing was posted.",
            )
        }
        postReview(project, reviewMarks, session, credentials, dialog.submission())
    }

    private fun postReview(
        project: Project,
        reviewMarks: ReviewMarksService,
        session: PullRequestSession,
        credentials: GitHubCredentialsResult.Found,
        submission: ReviewSubmission,
    ) {
        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable<GitHubResult<String>, RuntimeException> {
                GitHubApi(credentials.apiUrl, credentials.token).postReview(session.owner, session.repository, session.number, submission)
            },
            "Posting your review to PR #${session.number}",
            true,
            project,
        )
        when (result) {
            is GitHubResult.Failure -> showError(
                project,
                "GitHub did not accept the review: ${result.message} (${result.statusCode}). Nothing was posted. " +
                    "A comment on a line that is not part of the PR's changes is refused.",
            )
            is GitHubResult.Success -> {
                reviewMarks.endPullRequestReview()
                Messages.showInfoMessage(project, "Your review was posted.\n${result.value}", TITLE)
            }
        }
    }

    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, TITLE)
    }
}
