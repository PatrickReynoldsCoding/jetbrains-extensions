package com.patrickreynolds.reviewmarks

import java.io.File

private const val LARGEST_NEW_FILE_BYTES = 1_000_000L

object GitUncommittedChanges {

    fun changedLinesNotCommitted(projectFolder: String): BranchChangesResult {
        val repositoryRoot = when (val output = Git.repositoryRoot(projectFolder)) {
            is GitOutput.Failed -> return BranchChangesResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput
        }
        val diffWithNoContext = when (
            val output = Git.run(
                projectFolder, "-c", "core.quotepath=false", "diff", "--unified=0", "--no-color", "--no-ext-diff", "HEAD",
            )
        ) {
            is GitOutput.Failed -> return BranchChangesResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput
        }
        val untrackedPaths = when (
            val output = Git.run(projectFolder, "-c", "core.quotepath=false", "ls-files", "--others", "--exclude-standard", "-z", "--full-name")
        ) {
            is GitOutput.Failed -> return BranchChangesResult.Failed(output.failure)
            is GitOutput.Succeeded -> output.standardOutput.split('\u0000').filter { it.isNotEmpty() }
        }

        val changedLinesByRelativePath = parseChangedLines(diffWithNoContext)
        val allLinesOfNewFilesByRelativePath = untrackedPaths.mapNotNull { relativePath ->
            allLinesOf(File(repositoryRoot, relativePath))?.let { relativePath to listOf(it) }
        }.toMap()
        return BranchChangesResult.Found(
            (changedLinesByRelativePath + allLinesOfNewFilesByRelativePath)
                .mapKeys { (relativePath, _) -> "$repositoryRoot/$relativePath" },
        )
    }

    private fun allLinesOf(newFile: File): LineRange? {
        if (!newFile.isFile || newFile.length() == 0L || newFile.length() > LARGEST_NEW_FILE_BYTES) return null
        val bytes = newFile.readBytes()
        if (bytes.any { it == 0.toByte() }) return null
        val lineCount = String(bytes, Charsets.UTF_8).lines().dropLastWhile { it.isEmpty() }.size
        return LineRange(firstLine = 0, lastLine = lineCount - 1)
    }
}
