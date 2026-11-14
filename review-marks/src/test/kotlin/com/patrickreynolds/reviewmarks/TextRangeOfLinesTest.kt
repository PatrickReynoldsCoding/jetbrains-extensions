package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextRangeOfLinesTest {

    private val documentText = "zero\none\ntwo\n\nfour\n"

    @Test
    fun `covers the lines from the start of the first to the end of the last`() {
        assertEquals(TextRange(5, 12), textRangeOfLines(documentText, LineRange(1, 2)))
    }

    @Test
    fun `covers one line`() {
        assertEquals(TextRange(0, 4), textRangeOfLines(documentText, LineRange(0, 0)))
    }

    @Test
    fun `stops at the end of the file when the range runs past it`() {
        assertEquals(TextRange(14, documentText.length), textRangeOfLines(documentText, LineRange(4, 99)))
    }

    @Test
    fun `gives nothing for a line that is not in the file`() {
        assertNull(textRangeOfLines(documentText, LineRange(50, 60)))
    }

    @Test
    fun `gives nothing for lines that are only empty`() {
        assertNull(textRangeOfLines(documentText, LineRange(3, 3)))
    }
}
