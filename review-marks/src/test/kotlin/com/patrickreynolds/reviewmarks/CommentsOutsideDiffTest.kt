package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentsOutsideDiffTest {

    private val retryHunk = CommentableLines("src/Client.kt", firstLineNumber = 10, lastLineNumber = 20)

    @Test
    fun `keeps comments inside a hunk as line comments and moves the others out`() {
        val inside = ReviewComment("src/Client.kt", 12, 14, "Why retry here?")
        val pastTheHunk = ReviewComment("src/Client.kt", 18, 22, "Split this up?")
        val otherFile = ReviewComment("src/Config.kt", 3, 3, "Typo")

        val byPlace = commentsByPlace(listOf(inside, pastTheHunk, otherFile), listOf(retryHunk))

        assertEquals(listOf(inside), byPlace.insideDiff)
        assertEquals(listOf(pastTheHunk, otherFile), byPlace.outsideDiff)
    }

    @Test
    fun `keeps every comment when the PR's lines are not known`() {
        val comment = ReviewComment("src/Client.kt", 99, 99, "Why?")

        assertEquals(CommentsByPlace(listOf(comment), emptyList()), commentsByPlace(listOf(comment), emptyList()))
    }

    @Test
    fun `adds the comments outside the changes to the end of the summary`() {
        val summary = summaryWithCommentsOutsideDiff(
            "Looks good",
            listOf(ReviewComment("src/Config.kt", 3, 3, "Typo"), ReviewComment("src/A.kt", 5, 7, "Rename")),
        )

        assertEquals(
            "Looks good\n\nComments on lines outside this PR's changes:\n- `src/Config.kt:3`: Typo\n- `src/A.kt:5-7`: Rename",
            summary,
        )
    }

    @Test
    fun `keeps the summary as it is when every comment is inside the changes`() {
        assertEquals("Looks good", summaryWithCommentsOutsideDiff("Looks good", emptyList()))
    }
}
