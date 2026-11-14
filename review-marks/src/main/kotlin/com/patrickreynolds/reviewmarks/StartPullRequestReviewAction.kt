package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable

private const val TITLE = "Start PR Review"

class StartPullRequestReviewAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectFolder = project.basePath ?: return showError(project, "This project has no folder on disk.")

        val repository = when (val result = GitRepository.read(projectFolder)) {
            is GitRepositoryInfoResult.Failed -> return showError(project, "${result.git.command}\n\n${result.git.errorOutput}")
            is GitRepositoryInfoResult.Found -> result.info
        }
        val remote = parseGitRemote(repository.remoteUrl)
            ?: return showError(project, "Could not read a GitHub owner and repository from the remote ${repository.remoteUrl}")
        val gitHubHost = gitHubHostFor(remote.host, service<ReviewMarksSettings>().state.gitHubHostAliases)
        val credentials = when (val result = IdeGitHubAccount.credentialsFor(project, gitHubHost)) {
            is GitHubCredentialsResult.Found -> result
            is GitHubCredentialsResult.NoToken -> return showError(project, "No GitHub token. Sign in under Settings, Version Control, GitHub.")
            is GitHubCredentialsResult.NoMatchingAccount -> return showError(project, noAccountMessage(result))
        }

        val api = GitHubApi(credentials.apiUrl, credentials.token)
        val pullRequest = findPullRequest(project, api, remote, repository.currentBranch) ?: return
        if (pullRequest.headSha != repository.headSha && !confirmCheckoutDiffers(project, pullRequest)) return

        val pullRequestChanges = loadChangedLines(project, api, remote, pullRequest) ?: return
        val changedLinesByRepositoryPath = pullRequestChanges.changedLinesByPath
        val session = PullRequestSession(
            number = pullRequest.number,
            title = pullRequest.title,
            htmlUrl = pullRequest.htmlUrl,
            host = gitHubHost,
            apiUrl = credentials.apiUrl,
            owner = remote.owner,
            repository = remote.repository,
            headSha = pullRequest.headSha,
            repositoryRoot = repository.root,
            accountName = credentials.accountName,
            commentableLines = pullRequestChanges.commentableLines.toMutableList(),
        )
        val changedLinesByPath = changedLinesByRepositoryPath.mapKeys { (relativePath, _) -> "${repository.root}/$relativePath" }
        val markedFileCount = project.service<ReviewMarksService>().startPullRequestReview(session, localFilesByPath(changedLinesByPath))
        Messages.showInfoMessage(
            project,
            "Reviewing PR #${pullRequest.number}: ${pullRequest.title}\nMarked the changed lines to review, in $markedFileCount files. " +
                "Add notes to lines to turn them into review comments, then use Post review.",
            TITLE,
        )
    }

    private fun findPullRequest(project: Project, api: GitHubApi, remote: GitRemote, branch: String): PullRequestInfo? {
        val foundForBranch = callGitHub(project, "Looking for the PR of branch $branch") {
            api.findOpenPullRequest(remote.owner, remote.repository, branch)
        } ?: return null
        foundForBranch.value?.let { return it }

        val numberText = Messages.showInputDialog(
            project,
            "No open PR was found for branch $branch. Enter the PR number to review.",
            TITLE,
            null,
        )?.trim()?.removePrefix("#")
        val number = numberText?.toIntOrNull()
        if (number == null) {
            if (!numberText.isNullOrEmpty()) showError(project, "$numberText is not a PR number.")
            return null
        }
        return callGitHub(project, "Loading PR #$number") { api.getPullRequest(remote.owner, remote.repository, number) }?.value
    }

    private fun loadChangedLines(project: Project, api: GitHubApi, remote: GitRemote, pullRequest: PullRequestInfo) =
        callGitHub(project, "Loading the files changed in PR #${pullRequest.number}") {
            api.changedLines(remote.owner, remote.repository, pullRequest.number)
        }?.value

    private fun <T> callGitHub(project: Project, progressTitle: String, call: () -> GitHubResult<T>): GitHubResult.Success<T>? {
        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable<GitHubResult<T>, RuntimeException> { call() },
            progressTitle,
            true,
            project,
        )
        return when (result) {
            is GitHubResult.Success -> result
            is GitHubResult.Failure -> {
                showError(project, "GitHub said: ${result.message} (${result.statusCode})")
                null
            }
        }
    }

    private fun confirmCheckoutDiffers(project: Project, pullRequest: PullRequestInfo): Boolean =
        Messages.showYesNoDialog(
            project,
            "The commit you have checked out is not the latest commit of PR #${pullRequest.number}. " +
                "Comments are placed by line number on the PR's latest commit, so they may land on the wrong lines. Continue anyway?",
            TITLE,
            null,
        ) == Messages.YES

    private fun noAccountMessage(result: GitHubCredentialsResult.NoMatchingAccount): String {
        val signedIn = result.signedInHosts.ifEmpty { listOf("none") }.joinToString()
        return "No GitHub account in the IDE for ${result.remoteHost} (signed in: $signedIn). " +
            "Add the account under Settings, Version Control, GitHub. " +
            "If your remote uses an SSH host alias, add it under Settings, Tools, Review Marks, GitHub host aliases."
    }

    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, TITLE)
    }
}
