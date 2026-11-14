package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewProgressTest {

    private val documentText = "first\nsecond\n\nthird\nfourth\n"

    @Test
    fun `counts only code lines, not empty lines`() {
        val progress = reviewProgress(documentText, reviewedRanges = emptyList(), changedRanges = emptyList(), outOfScopeRanges = emptyList())
        assertEquals(ReviewProgress(reviewedLineCount = 0, codeLineCount = 4, changedLineCount = 0), progress)
    }

    @Test
    fun `counts reviewed lines and changed lines`() {
        val reviewedRange = TextRange(0, documentText.indexOf("second") + "second".length)
        val changedRange = TextRange(documentText.indexOf("third"), documentText.indexOf("third") + 5)

        val progress = reviewProgress(documentText, listOf(reviewedRange), listOf(changedRange), outOfScopeRanges = emptyList())

        assertEquals(ReviewProgress(reviewedLineCount = 2, codeLineCount = 4, changedLineCount = 1), progress)
    }

    @Test
    fun `status text shows the percentage, the lines and the changed lines`() {
        val progress = ReviewProgress(reviewedLineCount = 120, codeLineCount = 300, changedLineCount = 2)
        assertEquals("Reviewed 40% (120/300 lines) · 2 changed", progress.statusText())
    }

    @Test
    fun `status text leaves out changed lines when there are none`() {
        val progress = ReviewProgress(reviewedLineCount = 1, codeLineCount = 4, changedLineCount = 0)
        assertEquals("Reviewed 25% (1/4 lines)", progress.statusText())
    }

    @Test
    fun `status text is empty for a file with no code`() {
        assertEquals("", ReviewProgress(0, 0, 0).statusText())
    }
}
