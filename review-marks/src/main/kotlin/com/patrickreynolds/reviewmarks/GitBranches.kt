package com.patrickreynolds.reviewmarks

private const val LOCAL_BRANCH_PREFIX = "refs/heads/"
private const val REMOTE_BRANCH_PREFIX = "refs/remotes/"
private val BASE_BRANCHES_TO_SUGGEST = listOf("origin/main", "origin/master", "main", "master")

sealed interface BranchListResult {
    data class Found(val branches: List<String>) : BranchListResult
    data class Failed(val git: GitFailed) : BranchListResult
}

fun parseBranchNames(forEachRefOutput: String): List<String> =
    forEachRefOutput.lines()
        .map { it.trim() }
        .filter { !it.endsWith("/HEAD") }
        .mapNotNull { refName ->
            when {
                refName.startsWith(LOCAL_BRANCH_PREFIX) -> refName.removePrefix(LOCAL_BRANCH_PREFIX)
                refName.startsWith(REMOTE_BRANCH_PREFIX) -> refName.removePrefix(REMOTE_BRANCH_PREFIX)
                else -> null
            }
        }
        .distinct()
        .sorted()

fun suggestedBaseBranch(branches: List<String>): String? = BASE_BRANCHES_TO_SUGGEST.firstOrNull { it in branches }

object GitBranches {

    fun list(projectFolder: String): BranchListResult =
        when (val output = Git.run(projectFolder, "for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes")) {
            is GitOutput.Failed -> BranchListResult.Failed(output.failure)
            is GitOutput.Succeeded -> BranchListResult.Found(parseBranchNames(output.standardOutput))
        }
}
