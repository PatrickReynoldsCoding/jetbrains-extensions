package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class LocateStopTest {

    private val documentText = "fun sum() {\n  val total = 0\n  return total\n}\n"
    private val anchorText = "val total = 0"
    private val anchorStart = documentText.indexOf(anchorText)
    private val stop = StoredStop("file:///Sum.kt", anchorStart, anchorText)

    @Test
    fun `stops at the end of the last reviewed line`() {
        assertEquals(anchorStart + anchorText.length, stopOffsetIn(documentText, stop))
    }

    @Test
    fun `follows the last reviewed line when lines were added above it`() {
        val textWithLineAdded = "// added\n$documentText"
        assertEquals(textWithLineAdded.indexOf(anchorText) + anchorText.length, stopOffsetIn(textWithLineAdded, stop))
    }

    @Test
    fun `falls back to the stored place when the last reviewed line was edited`() {
        val editedText = documentText.replace(anchorText, "val total = 1")
        assertEquals(anchorStart + anchorText.length, stopOffsetIn(editedText, stop))
    }

    @Test
    fun `stays inside the file when the file became shorter`() {
        assertEquals(5, stopOffsetIn("short", stop))
    }
}
