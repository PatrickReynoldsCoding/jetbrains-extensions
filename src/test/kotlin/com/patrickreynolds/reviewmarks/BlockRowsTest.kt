package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockRowsTest {

    private val documentText = "one\n  two\nthree\n\nfour\nfive\n"
    private val lineTwoCodeStart = documentText.indexOf("two")
    private val lineThreeEnd = documentText.indexOf("three") + "three".length
    private val lineFourStart = documentText.indexOf("four")
    private val lineFiveEnd = documentText.indexOf("five") + "five".length

    @Test
    fun `a reviewed block gives its first and last code line, its size and a preview of its first line`() {
        val rows = reviewedBlockRows(documentText, listOf(TextRange(0, lineThreeEnd)))

        assertEquals(
            listOf(BlockRow(BlockKind.REVIEWED, firstLine = 1, lastLine = 3, codeLineCount = 3, preview = "one", startOffset = 0, endOffset = lineThreeEnd)),
            rows,
        )
    }

    @Test
    fun `reviewed marks with only an empty line between them are one block`() {
        val rows = reviewedBlockRows(documentText, listOf(TextRange(0, lineThreeEnd), TextRange(lineFourStart, lineFiveEnd)))

        assertEquals(1, rows.size)
        assertEquals(1, rows.single().firstLine)
        assertEquals(6, rows.single().lastLine)
        assertEquals(5, rows.single().codeLineCount)
    }

    @Test
    fun `a long first line is cut and ends with an ellipsis`() {
        val longLine = "x".repeat(60)
        val row = reviewedBlockRows(longLine, listOf(TextRange(0, longLine.length))).single()
        assertEquals("x".repeat(40) + "…", row.preview)
    }

    @Test
    fun `code left to review is split into blocks at every empty line`() {
        val reviewedRange = TextRange(lineTwoCodeStart, lineThreeEnd)

        val rows = blocksToReviewRows(documentText, listOf(reviewedRange), emptyList(), emptyList())

        assertEquals(
            listOf(1 to 1, 5 to 6),
            rows.map { it.firstLine to it.lastLine },
        )
        assertEquals(listOf(BlockKind.UNREAD, BlockKind.UNREAD), rows.map { it.kind })
    }

    @Test
    fun `a block with a changed line is marked as changed and listed first`() {
        val changedRange = TextRange(lineFourStart, lineFourStart + "four".length)

        val rows = blocksToReviewRows(documentText, emptyList(), listOf(changedRange), emptyList())

        assertEquals(BlockKind.CHANGED, rows.first().kind)
        assertEquals(5, rows.first().firstLine)
        assertEquals(BlockKind.UNREAD, rows.last().kind)
    }

    @Test
    fun `lines that are out of scope are not listed`() {
        val outOfScopeRange = TextRange(0, lineThreeEnd)

        val rows = blocksToReviewRows(documentText, emptyList(), emptyList(), listOf(outOfScopeRange))

        assertEquals(listOf(5 to 6), rows.map { it.firstLine to it.lastLine })
    }
}
