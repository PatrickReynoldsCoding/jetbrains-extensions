package com.patrickreynolds.reviewmarks

sealed interface BranchChangesResult {
    data class Found(val changedLinesByPath: Map<String, List<LineRange>>) : BranchChangesResult
    data class Failed(val git: GitFailed) : BranchChangesResult
}

object GitBranchChanges {

    fun changedLinesSince(projectFolder: String, baseRef: String): BranchChangesResult {
        val repositoryRoot = when (val output = Git.repositoryRoot(projectFolder)) {
            is GitOutput.Failed -> return BranchChangesResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput
        }
        val commonAncestor = when (val output = Git.run(projectFolder, "merge-base", "HEAD", baseRef)) {
            is GitOutput.Failed -> return BranchChangesResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        val diffWithNoContext = when (
            val output = Git.run(
                projectFolder, "-c", "core.quotepath=false", "diff", "--unified=0", "--no-color", "--no-ext-diff", commonAncestor,
            )
        ) {
            is GitOutput.Failed -> return BranchChangesResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput
        }

        val changedLinesByRelativePath = parseChangedLines(diffWithNoContext)
        return BranchChangesResult.Found(
            changedLinesByRelativePath.mapKeys { (relativePath, _) -> "$repositoryRoot/$relativePath" },
        )
    }
}
