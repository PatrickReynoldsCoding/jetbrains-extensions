package com.patrickreynolds.reviewmarks

data class PullRequestSession(
    var number: Int = 0,
    var title: String = "",
    var htmlUrl: String = "",
    var host: String = "",
    var apiUrl: String = "",
    var owner: String = "",
    var repository: String = "",
    var headSha: String = "",
    var repositoryRoot: String = "",
    var accountName: String = "",
    var commentableLines: MutableList<CommentableLines> = mutableListOf(),
)

data class CommentableLines(
    var path: String = "",
    var firstLineNumber: Int = 0,
    var lastLineNumber: Int = 0,
)
