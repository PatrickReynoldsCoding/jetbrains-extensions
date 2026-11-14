package com.patrickreynolds.reviewmarks

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler

private const val GIT_TIMEOUT_MILLISECONDS = 30_000

data class GitFailed(val command: String, val errorOutput: String)

sealed interface GitOutput {
    data class Succeeded(val standardOutput: String) : GitOutput
    data class Failed(val failure: GitFailed) : GitOutput
}

object Git {

    fun run(workingFolder: String, vararg arguments: String): GitOutput {
        val commandLine = GeneralCommandLine("git", *arguments).withWorkDirectory(workingFolder)
        val output = CapturingProcessHandler(commandLine).runProcess(GIT_TIMEOUT_MILLISECONDS)
        if (output.exitCode != 0) {
            return GitOutput.Failed(GitFailed("git ${arguments.joinToString(" ")}", output.stderr.trim()))
        }
        return GitOutput.Succeeded(output.stdout)
    }
}
