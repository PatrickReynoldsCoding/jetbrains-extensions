package com.patrickreynolds.reviewmarks

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class GitBranchChangesTest : BasePlatformTestCase() {

    private lateinit var repositoryFolder: File

    override fun setUp() {
        super.setUp()
        repositoryFolder = Files.createTempDirectory("review-marks-git-test").toFile().canonicalFile
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

    private fun createRepositoryWithMainAndFeatureBranch() {
        git("init", "-b", "main")
        File(repositoryFolder, "Sample.txt").writeText(fileText("one", "two", "three", "four", "five"))
        File(repositoryFolder, "Untouched.txt").writeText(fileText("same"))
        git("add", "-A")
        git("commit", "-m", "base")
        git("checkout", "-b", "feature/login")
    }

    fun `test finds the committed and the uncommitted lines changed since the base branch`() {
        createRepositoryWithMainAndFeatureBranch()
        File(repositoryFolder, "Sample.txt").writeText(fileText("one", "two", "THREE", "four", "five"))
        git("commit", "-am", "change line three")
        File(repositoryFolder, "Sample.txt").writeText(fileText("one", "two", "THREE", "four", "FIVE"))

        val result = GitBranchChanges.changedLinesSince(repositoryFolder.path, "main")

        val found = result as BranchChangesResult.Found
        assertEquals(
            mapOf("${repositoryFolder.path}/Sample.txt" to listOf(LineRange(2, 2), LineRange(4, 4))),
            found.changedLinesByPath,
        )
    }

    fun `test reports the git error when the base branch does not exist`() {
        createRepositoryWithMainAndFeatureBranch()

        val result = GitBranchChanges.changedLinesSince(repositoryFolder.path, "no-such-branch")

        val failed = result as BranchChangesResult.Failed
        assertTrue(failed.git.command.contains("merge-base"))
        assertTrue(failed.git.errorOutput.isNotEmpty())
    }

    fun `test lists the local branches of the repository`() {
        createRepositoryWithMainAndFeatureBranch()

        val result = GitBranches.list(repositoryFolder.path)

        assertEquals(listOf("feature/login", "main"), (result as BranchListResult.Found).branches)
    }

    fun `test reads the root, remote, branch and head commit of a repository`() {
        createRepositoryWithMainAndFeatureBranch()
        git("remote", "add", "origin", "git@github.com:acme/widgets.git")

        val result = GitRepository.read(repositoryFolder.path) as GitRepositoryInfoResult.Found

        assertEquals(repositoryFolder.path, result.info.root)
        assertEquals("git@github.com:acme/widgets.git", result.info.remoteUrl)
        assertEquals("feature/login", result.info.currentBranch)
        assertEquals(40, result.info.headSha.length)
    }

    fun `test reports the git error when the repository has no origin remote`() {
        createRepositoryWithMainAndFeatureBranch()

        val result = GitRepository.read(repositoryFolder.path)

        assertTrue((result as GitRepositoryInfoResult.Failed).git.command.contains("remote get-url origin"))
    }
}
