package com.patrickreynolds.reviewmarks

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class GitUncommittedChangesTest : BasePlatformTestCase() {

    private lateinit var repositoryFolder: File

    override fun setUp() {
        super.setUp()
        repositoryFolder = Files.createTempDirectory("review-marks-uncommitted-test").toFile().canonicalFile
    }

    override fun tearDown() {
        try {
            repositoryFolder.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun git(vararg arguments: String) {
        val process = ProcessBuilder(
            listOf("git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false") + arguments,
        ).directory(repositoryFolder).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals("git ${arguments.toList()} failed: $output", 0, process.waitFor())
    }

    private fun fileText(vararg lines: String) = lines.joinToString("\n", postfix = "\n")

    private fun createRepositoryWithOneCommit() {
        git("init", "-b", "main")
        File(repositoryFolder, "Sample.txt").writeText(fileText("one", "two", "three"))
        File(repositoryFolder, "Committed.txt").writeText(fileText("same"))
        git("add", "-A")
        git("commit", "-m", "base")
    }

    fun `test finds staged and unstaged edits but not committed lines`() {
        createRepositoryWithOneCommit()
        File(repositoryFolder, "Sample.txt").writeText(fileText("ONE", "two", "three"))
        git("add", "Sample.txt")
        File(repositoryFolder, "Sample.txt").writeText(fileText("ONE", "two", "THREE"))

        val found = GitUncommittedChanges.changedLinesNotCommitted(repositoryFolder.path) as BranchChangesResult.Found

        assertEquals(
            mapOf("${repositoryFolder.path}/Sample.txt" to listOf(LineRange(0, 0), LineRange(2, 2))),
            found.changedLinesByPath,
        )
    }

    fun `test marks every line of a new file git does not track yet`() {
        createRepositoryWithOneCommit()
        File(repositoryFolder, "New.txt").writeText(fileText("alpha", "beta", "gamma", "delta"))

        val found = GitUncommittedChanges.changedLinesNotCommitted(repositoryFolder.path) as BranchChangesResult.Found

        assertEquals(mapOf("${repositoryFolder.path}/New.txt" to listOf(LineRange(0, 3))), found.changedLinesByPath)
    }

    fun `test skips ignored files and binary files`() {
        createRepositoryWithOneCommit()
        File(repositoryFolder, ".gitignore").writeText("secret.txt\n")
        git("add", ".gitignore")
        git("commit", "-m", "ignore")
        File(repositoryFolder, "secret.txt").writeText(fileText("hidden"))
        File(repositoryFolder, "image.bin").writeBytes(byteArrayOf(1, 0, 2, 0))

        val found = GitUncommittedChanges.changedLinesNotCommitted(repositoryFolder.path) as BranchChangesResult.Found

        assertEquals(emptyMap<String, List<LineRange>>(), found.changedLinesByPath)
    }

    fun `test reports the git error when the folder is not a repository`() {
        val notARepository = Files.createTempDirectory("review-marks-not-a-repository").toFile().canonicalFile
        try {
            val failed = GitUncommittedChanges.changedLinesNotCommitted(notARepository.path) as BranchChangesResult.Failed

            assertTrue(failed.git.command.contains("rev-parse"))
        } finally {
            notARepository.deleteRecursively()
        }
    }
}
