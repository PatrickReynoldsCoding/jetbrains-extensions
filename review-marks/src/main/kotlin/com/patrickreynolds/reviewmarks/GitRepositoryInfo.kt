package com.patrickreynolds.reviewmarks

data class GitRepositoryInfo(
    val root: String,
    val remoteUrl: String,
    val currentBranch: String,
    val headSha: String,
)

sealed interface GitRepositoryInfoResult {
    data class Found(val info: GitRepositoryInfo) : GitRepositoryInfoResult
    data class Failed(val git: GitFailed) : GitRepositoryInfoResult
}

object GitRepository {

    fun read(projectFolder: String): GitRepositoryInfoResult {
        val root = when (val output = Git.run(projectFolder, "rev-parse", "--show-toplevel")) {
            is GitOutput.Failed -> return GitRepositoryInfoResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        val remoteUrl = when (val output = Git.run(projectFolder, "remote", "get-url", "origin")) {
            is GitOutput.Failed -> return GitRepositoryInfoResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        val currentBranch = when (val output = Git.run(projectFolder, "branch", "--show-current")) {
            is GitOutput.Failed -> return GitRepositoryInfoResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        val headSha = when (val output = Git.run(projectFolder, "rev-parse", "HEAD")) {
            is GitOutput.Failed -> return GitRepositoryInfoResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        return GitRepositoryInfoResult.Found(GitRepositoryInfo(root, remoteUrl, currentBranch, headSha))
    }
}
