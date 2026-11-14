package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAccountTest {

    private val workAccount = SignedInAccount("work-account", "github.com")
    private val companyAccount = SignedInAccount("company-account", "git.example.com")

    @Test
    fun `picks the account signed in to the host of the remote`() {
        assertEquals(companyAccount, accountForRemoteHost("git.example.com", listOf(workAccount, companyAccount)))
    }

    @Test
    fun `ignores the case of the host`() {
        assertEquals(workAccount, accountForRemoteHost("GitHub.com", listOf(workAccount)))
    }

    @Test
    fun `does not guess an account for a host alias`() {
        assertNull(accountForRemoteHost("github-personal", listOf(workAccount)))
    }

    @Test
    fun `gives nothing when no account is signed in`() {
        assertNull(accountForRemoteHost("github.com", emptyList()))
    }
}
