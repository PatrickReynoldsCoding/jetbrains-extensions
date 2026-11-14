package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class LinesToReviewTest {

    private val documentText = "fun total() {\n  val sum = 0\n\n  return sum\n}\n"
    private val valLineStart = documentText.indexOf("  val sum")
    private val returnLineEnd = documentText.indexOf("return sum") + "return sum".length

    @Test
    fun `no line needs review when nothing is marked`() {
        assertEquals(emptyList<Int>(), linesToReview(documentText, emptyList()))
    }

    @Test
    fun `the code lines inside a marked range need review and the empty line does not`() {
        assertEquals(listOf(1, 3), linesToReview(documentText, listOf(TextRange(valLineStart, returnLineEnd))))
    }

    @Test
    fun `a line that is only partly marked does not need review`() {
        assertEquals(emptyList<Int>(), linesToReview(documentText, listOf(TextRange(valLineStart, valLineStart + 6))))
    }

    @Test
    fun `two marks with only an empty line between them count as one`() {
        val valLineEnd = valLineStart + "  val sum = 0".length
        val returnLineStart = documentText.indexOf("  return sum")
        val ranges = listOf(TextRange(valLineStart, valLineEnd), TextRange(returnLineStart, returnLineEnd))
        assertEquals(listOf(1, 3), linesToReview(documentText, ranges))
    }
}
