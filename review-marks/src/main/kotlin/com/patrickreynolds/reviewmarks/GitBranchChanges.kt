package com.patrickreynolds.reviewmarks

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler

private const val GIT_TIMEOUT_MILLISECONDS = 30_000

sealed interface BranchChangesResult {
    data class Found(val changedLinesByPath: Map<String, List<LineRange>>) : BranchChangesResult
    data class GitFailed(val command: String, val errorOutput: String) : BranchChangesResult
}

private sealed interface GitOutput {
    data class Succeeded(val standardOutput: String) : GitOutput
    data class Failed(val failure: BranchChangesResult.GitFailed) : GitOutput
}

object GitBranchChanges {

    fun changedLinesSince(projectFolder: String, baseRef: String): BranchChangesResult {
        val repositoryRoot = when (val output = runGit(projectFolder, "rev-parse", "--show-toplevel")) {
            is GitOutput.Failed -> return output.failure
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        val commonAncestor = when (val output = runGit(projectFolder, "merge-base", "HEAD", baseRef)) {
            is GitOutput.Failed -> return output.failure
            is GitOutput.Succeeded -> output.standardOutput.trim()
        }
        val diffWithNoContext = when (
            val output = runGit(
                projectFolder, "-c", "core.quotepath=false", "diff", "--unified=0", "--no-color", "--no-ext-diff", commonAncestor,
            )
        ) {
            is GitOutput.Failed -> return output.failure
            is GitOutput.Succeeded -> output.standardOutput
        }

        val changedLinesByRelativePath = parseChangedLines(diffWithNoContext)
        return BranchChangesResult.Found(
            changedLinesByRelativePath.mapKeys { (relativePath, _) -> "$repositoryRoot/$relativePath" },
        )
    }

    private fun runGit(workingFolder: String, vararg arguments: String): GitOutput {
        val commandLine = GeneralCommandLine("git", *arguments).withWorkDirectory(workingFolder)
        val output = CapturingProcessHandler(commandLine).runProcess(GIT_TIMEOUT_MILLISECONDS)
        if (output.exitCode != 0) {
            val failure = BranchChangesResult.GitFailed("git ${arguments.joinToString(" ")}", output.stderr.trim())
            return GitOutput.Failed(failure)
        }
        return GitOutput.Succeeded(output.stdout)
    }
}
