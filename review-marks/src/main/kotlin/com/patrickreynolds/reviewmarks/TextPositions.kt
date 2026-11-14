package com.patrickreynolds.reviewmarks

fun lineNumberAt(documentText: String, offset: Int): Int =
    documentText.substring(0, offset.coerceIn(0, documentText.length)).count { it == '\n' } + 1

fun relativePathIn(repositoryRoot: String, filePath: String): String? {
    val rootWithSeparator = repositoryRoot.trimEnd('/') + "/"
    return if (filePath.startsWith(rootWithSeparator)) filePath.removePrefix(rootWithSeparator) else null
}
