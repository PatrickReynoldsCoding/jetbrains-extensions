package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class NewlyReviewedLinesTest {

    private val documentText = "one\ntwo\n\nthree\nfour\n"
    private val lineTwoStart = documentText.indexOf("two")
    private val lineFourEnd = documentText.indexOf("four") + "four".length

    @Test
    fun `counts the code lines in the marked range and not the empty lines`() {
        val markedRange = TextRange(lineTwoStart, lineFourEnd)
        assertEquals(3, newlyReviewedLineCount(documentText, markedRange, emptyList(), emptyList()))
    }

    @Test
    fun `does not count lines that were already reviewed`() {
        val markedRange = TextRange(0, lineFourEnd)
        val alreadyReviewed = listOf(TextRange(0, lineTwoStart + 3))
        assertEquals(2, newlyReviewedLineCount(documentText, markedRange, alreadyReviewed, emptyList()))
    }

    @Test
    fun `does not count lines that are out of scope`() {
        val markedRange = TextRange(0, lineFourEnd)
        val outOfScope = listOf(TextRange(0, lineTwoStart + 3))
        assertEquals(2, newlyReviewedLineCount(documentText, markedRange, emptyList(), outOfScope))
    }

    @Test
    fun `does not count a line that is only partly inside the marked range`() {
        val markedRange = TextRange(lineTwoStart + 1, lineFourEnd)
        assertEquals(2, newlyReviewedLineCount(documentText, markedRange, emptyList(), emptyList()))
    }
}
