package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.util.GHCompatibilityUtil

data class SignedInAccount(val name: String, val host: String)

fun accountForRemoteHost(remoteHost: String, signedInAccounts: List<SignedInAccount>): SignedInAccount? =
    signedInAccounts.firstOrNull { it.host.equals(remoteHost, ignoreCase = true) }

sealed interface GitHubCredentialsResult {
    data class Found(val apiUrl: String, val token: String, val accountName: String) : GitHubCredentialsResult
    data class NoMatchingAccount(val remoteHost: String, val signedInHosts: List<String>) : GitHubCredentialsResult
    data object NoToken : GitHubCredentialsResult
}

object IdeGitHubAccount {

    fun credentialsFor(project: Project, remoteHost: String): GitHubCredentialsResult {
        val accounts = service<GHAccountManager>().accountsState.value
        val signedInAccounts = accounts.map { SignedInAccount(it.name, it.server.host) }
        val chosenAccount = accountForRemoteHost(remoteHost, signedInAccounts)
            ?: return GitHubCredentialsResult.NoMatchingAccount(remoteHost, signedInAccounts.map { it.host })

        val account = accounts.first { it.name == chosenAccount.name && it.server.host == chosenAccount.host }
        val token = GHCompatibilityUtil.getOrRequestToken(account, project) ?: return GitHubCredentialsResult.NoToken
        return GitHubCredentialsResult.Found(account.server.toApiUrl(), token, account.name)
    }
}
