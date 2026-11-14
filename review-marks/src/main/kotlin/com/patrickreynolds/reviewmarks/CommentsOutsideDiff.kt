package com.patrickreynolds.reviewmarks

data class CommentsByPlace(
    val insideDiff: List<ReviewComment>,
    val outsideDiff: List<ReviewComment>,
)

fun commentsByPlace(comments: List<ReviewComment>, commentableLines: List<CommentableLines>): CommentsByPlace {
    if (commentableLines.isEmpty()) return CommentsByPlace(insideDiff = comments, outsideDiff = emptyList())
    val (insideDiff, outsideDiff) = comments.partition { comment ->
        commentableLines.any { hunk ->
            hunk.path == comment.path && comment.firstLine >= hunk.firstLineNumber && comment.lastLine <= hunk.lastLineNumber
        }
    }
    return CommentsByPlace(insideDiff, outsideDiff)
}

fun summaryWithCommentsOutsideDiff(summary: String, outsideDiff: List<ReviewComment>): String {
    if (outsideDiff.isEmpty()) return summary
    val commentLines = outsideDiff.joinToString("\n") { comment ->
        val linesText = if (comment.firstLine == comment.lastLine) "${comment.firstLine}" else "${comment.firstLine}-${comment.lastLine}"
        "- `${comment.path}:$linesText`: ${comment.body}"
    }
    val heading = "Comments on lines outside this PR's changes:"
    return listOf(summary, "$heading\n$commentLines").filter { it.isNotBlank() }.joinToString("\n\n")
}
