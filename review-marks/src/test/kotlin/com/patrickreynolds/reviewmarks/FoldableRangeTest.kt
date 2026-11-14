package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoldableRangeTest {

    private val documentText = "line one\n  line two\nline three\nline four\nline five\n"
    private val lineTwoStart = documentText.indexOf("  line two")
    private val lineFourStart = documentText.indexOf("line four")
    private val lineFourEnd = lineFourStart + "line four".length

    @Test
    fun `folds from the start of the first marked line to the end of the last marked line`() {
        val markedLines = TextRange(lineTwoStart, lineFourEnd)
        assertEquals(TextRange(lineTwoStart, lineFourEnd), foldableRange(documentText, markedLines))
    }

    @Test
    fun `counts the indentation before the first marked character as part of the line`() {
        val markedLines = TextRange(lineTwoStart + 2, lineFourEnd)
        assertEquals(TextRange(lineTwoStart, lineFourEnd), foldableRange(documentText, markedLines))
    }

    @Test
    fun `leaves out the line break at the end of a mark that ends at the start of the next line`() {
        val markedLines = TextRange(lineTwoStart, lineFourEnd + 1)
        assertEquals(TextRange(lineTwoStart, lineFourEnd), foldableRange(documentText, markedLines))
    }

    @Test
    fun `leaves out a first line that is only partly marked`() {
        val markedFromMiddleOfLineTwo = TextRange(lineTwoStart + 5, lineFourEnd)
        val lineThreeStart = documentText.indexOf("line three")
        assertEquals(TextRange(lineThreeStart, lineFourEnd), foldableRange(documentText, markedFromMiddleOfLineTwo))
    }

    @Test
    fun `leaves out a last line that is only partly marked`() {
        val markedUntilMiddleOfLineFour = TextRange(lineTwoStart, lineFourStart + 4)
        val lineThreeEnd = documentText.indexOf("line three") + "line three".length
        assertEquals(TextRange(lineTwoStart, lineThreeEnd), foldableRange(documentText, markedUntilMiddleOfLineFour))
    }

    @Test
    fun `does not fold a single line`() {
        val lineThreeStart = documentText.indexOf("line three")
        val onlyLineThree = TextRange(lineThreeStart, lineThreeStart + "line three".length)
        assertNull(foldableRange(documentText, onlyLineThree))
    }
}
