package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocateMarkedTextTest {

    private val markedText = "return total"

    @Test
    fun `keeps the stored position when the text is still there`() {
        val documentText = "fun sum() {\n  return total\n}\n"
        assertEquals(14, locateMarkedText(documentText, markedText, storedStartOffset = 14))
    }

    @Test
    fun `follows the text when lines were added above it`() {
        val documentText = "// new comment\nfun sum() {\n  return total\n}\n"
        assertEquals(29, locateMarkedText(documentText, markedText, storedStartOffset = 14))
    }

    @Test
    fun `drops the mark when the text was edited`() {
        val documentText = "fun sum() {\n  return totalCount\n}\n".replace("totalCount", "count")
        assertNull(locateMarkedText(documentText, markedText, storedStartOffset = 14))
    }

    @Test
    fun `picks the copy nearest to where the text was when the text moved and now appears twice`() {
        val documentText = "return total\n// moved\nfun sum() {\n  return total\n}\n"
        assertEquals(36, locateMarkedText(documentText, markedText, storedStartOffset = 30))
        assertEquals(0, locateMarkedText(documentText, markedText, storedStartOffset = 3))
    }

    @Test
    fun `drops a mark that has no text`() {
        assertNull(locateMarkedText("fun sum() {}", markedText = "", storedStartOffset = 0))
    }
}
