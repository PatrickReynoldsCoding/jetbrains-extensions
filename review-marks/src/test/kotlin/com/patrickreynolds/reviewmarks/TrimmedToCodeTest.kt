package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimmedToCodeTest {

    private val documentText = "one\n  two\n\nthree\n"

    @Test
    fun `cuts the line breaks and indentation off both ends`() {
        val range = TextRange(3, documentText.indexOf("three") - 1)
        assertEquals(TextRange(documentText.indexOf("two"), documentText.indexOf("two") + 3), trimmedToCode(documentText, range))
    }

    @Test
    fun `keeps a range that already starts and ends on code`() {
        assertEquals(TextRange(0, 3), trimmedToCode(documentText, TextRange(0, 3)))
    }

    @Test
    fun `gives nothing for a range with only white space`() {
        assertNull(trimmedToCode(documentText, TextRange(3, 6)))
    }
}
