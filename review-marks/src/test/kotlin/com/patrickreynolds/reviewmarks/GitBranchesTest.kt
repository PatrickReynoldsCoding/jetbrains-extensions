package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitBranchesTest {

    private val forEachRefOutput = """
        refs/heads/main
        refs/heads/feature/login
        refs/remotes/origin/HEAD
        refs/remotes/origin/main
        refs/remotes/origin/feature/login
        refs/remotes/origin/release/1.0
    """.trimIndent()

    @Test
    fun `lists local and remote branches by name, sorted, without the remote HEAD`() {
        assertEquals(
            listOf("feature/login", "main", "origin/feature/login", "origin/main", "origin/release/1.0"),
            parseBranchNames(forEachRefOutput),
        )
    }

    @Test
    fun `gives no branches for empty output`() {
        assertEquals(emptyList<String>(), parseBranchNames(""))
    }

    @Test
    fun `suggests origin main first`() {
        assertEquals("origin/main", suggestedBaseBranch(listOf("master", "main", "origin/main")))
    }

    @Test
    fun `suggests master when there is no main`() {
        assertEquals("master", suggestedBaseBranch(listOf("feature", "master")))
    }

    @Test
    fun `suggests nothing when no usual base branch exists`() {
        assertNull(suggestedBaseBranch(listOf("develop", "feature")))
    }
}
