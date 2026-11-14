package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class LinesNeedingReviewTest {

    private val documentText = "fun total() {\n  val sum = 0\n\n  return sum\n}\n"
    private val valLineStart = documentText.indexOf("  val sum")
    private val returnLineEnd = documentText.indexOf("return sum") + "return sum".length

    @Test
    fun `every code line needs review when nothing is marked`() {
        assertEquals(listOf(0, 1, 3, 4), linesNeedingReview(documentText, emptyList(), emptyList()))
    }

    @Test
    fun `a line inside a reviewed range does not need review`() {
        val reviewedRange = TextRange(valLineStart, returnLineEnd)
        assertEquals(listOf(0, 4), linesNeedingReview(documentText, listOf(reviewedRange), emptyList()))
    }

    @Test
    fun `an empty line between two reviewed ranges does not split them`() {
        val valLineEnd = valLineStart + "  val sum = 0".length
        val returnLineStart = documentText.indexOf("  return sum")
        val reviewedRanges = listOf(TextRange(valLineStart, valLineEnd), TextRange(returnLineStart, returnLineEnd))
        assertEquals(listOf(0, 4), linesNeedingReview(documentText, reviewedRanges, emptyList()))
    }

    @Test
    fun `a line that is out of scope does not need review`() {
        val outOfScopeRange = TextRange(0, valLineStart - 1)
        assertEquals(listOf(1, 3, 4), linesNeedingReview(documentText, emptyList(), listOf(outOfScopeRange)))
    }

    @Test
    fun `a line that is only partly reviewed still needs review`() {
        val reviewedRange = TextRange(valLineStart, valLineStart + 6)
        assertEquals(listOf(0, 1, 3, 4), linesNeedingReview(documentText, listOf(reviewedRange), emptyList()))
    }
}
