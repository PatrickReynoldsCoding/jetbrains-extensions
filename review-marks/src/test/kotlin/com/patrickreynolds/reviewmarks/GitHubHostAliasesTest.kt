package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubHostAliasesTest {

    @Test
    fun `turns an ssh host alias into the github host it stands for`() {
        assertEquals("github.com", gitHubHostFor("github-personal", "github-personal=github.com"))
    }

    @Test
    fun `keeps a host that has no alias`() {
        assertEquals("github.com", gitHubHostFor("github.com", "github-personal=github.com"))
    }

    @Test
    fun `reads several aliases split by commas or lines and ignores case and spaces`() {
        val aliases = "github-work = github.com,\nGHE-Alias=ghe.acme.com"
        assertEquals("ghe.acme.com", gitHubHostFor("ghe-alias", aliases))
        assertEquals("github.com", gitHubHostFor("github-work", aliases))
    }
}
