package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitRemoteTest {

    @Test
    fun `reads an ssh remote`() {
        assertEquals(GitRemote("github.com", "acme", "widgets"), parseGitRemote("git@github.com:acme/widgets.git"))
    }

    @Test
    fun `reads an ssh remote that uses a host alias`() {
        assertEquals(
            GitRemote("github-personal", "PatrickReynoldsCoding", "jetbrains-extensions"),
            parseGitRemote("git@github-personal:PatrickReynoldsCoding/jetbrains-extensions.git"),
        )
    }

    @Test
    fun `reads an https remote with and without the git suffix`() {
        assertEquals(GitRemote("github.com", "acme", "widgets"), parseGitRemote("https://github.com/acme/widgets.git"))
        assertEquals(GitRemote("github.com", "acme", "widgets"), parseGitRemote("https://github.com/acme/widgets"))
    }

    @Test
    fun `reads an ssh url with a port and a company host`() {
        assertEquals(GitRemote("git.example.com", "team", "app"), parseGitRemote("ssh://git@git.example.com:2222/team/app.git"))
    }

    @Test
    fun `gives nothing for a remote it cannot read`() {
        assertNull(parseGitRemote("not a remote"))
        assertNull(parseGitRemote("/local/path/repo.git"))
    }
}
