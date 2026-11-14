package com.patrickreynolds.reviewmarks

fun gitHubHostFor(remoteHost: String, hostAliasesText: String): String {
    val hostByAlias = hostAliasesText.split(',', '\n')
        .map { it.trim() }
        .filter { it.contains('=') }
        .associate { aliasAndHost -> aliasAndHost.substringBefore('=').trim().lowercase() to aliasAndHost.substringAfter('=').trim() }
    return hostByAlias[remoteHost.lowercase()]?.ifEmpty { null } ?: remoteHost
}
