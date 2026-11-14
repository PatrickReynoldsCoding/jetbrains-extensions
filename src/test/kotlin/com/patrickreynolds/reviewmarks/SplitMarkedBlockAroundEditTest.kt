package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitMarkedBlockAroundEditTest {

    private val documentText = "line one\nline two\nline three\nline four\nline five\n"
    private val lineTwoStart = documentText.indexOf("line two")
    private val lineThreeStart = documentText.indexOf("line three")
    private val lineThreeEnd = lineThreeStart + "line three".length
    private val lineFourEnd = documentText.indexOf("line four") + "line four".length
    private val markedLinesTwoToFour = TextRange(lineTwoStart, lineFourEnd)

    @Test
    fun `an edit inside one line changes that whole line and keeps the lines around it reviewed`() {
        val split = splitMarkedBlockAroundEdit(documentText, markedLinesTwoToFour, lineThreeStart + 5, removedLength = 0)

        assertEquals(TextRange(lineTwoStart, lineThreeStart), split.unchangedBefore)
        assertEquals(TextRange(lineThreeStart, lineThreeEnd), split.changedLines)
        assertEquals(TextRange(lineThreeEnd, lineFourEnd), split.unchangedAfter)
    }

    @Test
    fun `an edit spanning two lines changes both lines`() {
        val split = splitMarkedBlockAroundEdit(documentText, markedLinesTwoToFour, lineThreeStart + 5, removedLength = 8)

        assertEquals(TextRange(lineThreeStart, lineFourEnd), split.changedLines)
        assertEquals(TextRange(lineTwoStart, lineThreeStart), split.unchangedBefore)
        assertNull(split.unchangedAfter)
    }

    @Test
    fun `an edit in the first line of the marked block leaves nothing reviewed before it`() {
        val split = splitMarkedBlockAroundEdit(documentText, markedLinesTwoToFour, lineTwoStart + 2, removedLength = 0)

        assertNull(split.unchangedBefore)
        assertEquals(TextRange(lineTwoStart, lineThreeStart - 1), split.changedLines)
    }

    @Test
    fun `an edit that starts above the marked block only changes the part inside it`() {
        val split = splitMarkedBlockAroundEdit(documentText, markedLinesTwoToFour, lineTwoStart - 3, removedLength = 6)

        assertNull(split.unchangedBefore)
        assertEquals(TextRange(lineTwoStart, lineThreeStart - 1), split.changedLines)
    }

    @Test
    fun `typing on an empty line inside the marked block changes an empty range at that line`() {
        val textWithEmptyLine = "one\n\ntwo\n"
        val markedBlock = TextRange(0, textWithEmptyLine.length - 1)

        val split = splitMarkedBlockAroundEdit(textWithEmptyLine, markedBlock, editOffset = 4, removedLength = 0)

        assertEquals(TextRange(4, 4), split.changedLines)
    }
}
