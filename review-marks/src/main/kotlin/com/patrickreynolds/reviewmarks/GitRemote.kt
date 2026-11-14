package com.patrickreynolds.reviewmarks

private val SCP_STYLE_REMOTE = Regex("""^(?:[^@/\s]+@)?([^:/\s]+):(?!//)([^/\s]+)/([^/\s]+?)(?:\.git)?/?$""")
private val URL_STYLE_REMOTE = Regex("""^[a-z+]+://(?:[^@/\s]+@)?([^/:\s]+)(?::\d+)?/([^/\s]+)/([^/\s]+?)(?:\.git)?/?$""")

data class GitRemote(val host: String, val owner: String, val repository: String)

fun parseGitRemote(remoteUrl: String): GitRemote? {
    val trimmedUrl = remoteUrl.trim()
    val match = URL_STYLE_REMOTE.find(trimmedUrl) ?: SCP_STYLE_REMOTE.find(trimmedUrl) ?: return null
    val (host, owner, repository) = match.destructured
    return GitRemote(host, owner, repository)
}
