package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRowsTest {

    private val documentText = "one\n  two\nthree\n\nfour\nfive\n"
    private val lineThreeEnd = documentText.indexOf("three") + "three".length
    private val lineFourStart = documentText.indexOf("four")
    private val lineFiveEnd = documentText.indexOf("five") + "five".length

    @Test
    fun `a block gives its first and last code line, its size and a preview of its first line`() {
        val rows = blockRows(BlockKind.REVIEWED, documentText, listOf(TextRange(0, lineThreeEnd)))

        assertEquals(
            listOf(BlockRow(BlockKind.REVIEWED, firstLine = 1, lastLine = 3, codeLineCount = 3, preview = "one", startOffset = 0, endOffset = lineThreeEnd)),
            rows,
        )
    }

    @Test
    fun `marks with only an empty line between them are one block`() {
        val rows = blockRows(BlockKind.REVIEWED, documentText, listOf(TextRange(0, lineThreeEnd), TextRange(lineFourStart, lineFiveEnd)))

        assertEquals(1, rows.size)
        assertEquals(1, rows.single().firstLine)
        assertEquals(6, rows.single().lastLine)
        assertEquals(5, rows.single().codeLineCount)
    }

    @Test
    fun `a long first line is cut and ends with an ellipsis`() {
        val longLine = "x".repeat(60)
        val row = blockRows(BlockKind.TO_REVIEW, longLine, listOf(TextRange(0, longLine.length))).single()
        assertEquals("x".repeat(40) + "…", row.preview)
    }

    @Test
    fun `a mark with no code in it gives no block`() {
        assertEquals(emptyList<BlockRow>(), blockRows(BlockKind.REVIEWED, "a\n\n\nb", listOf(TextRange(2, 3))))
    }

    @Test
    fun `changed blocks are listed before blocks to review`() {
        val toReview = TextRange(0, lineThreeEnd)
        val changed = TextRange(lineFourStart, lineFourStart + "four".length)

        val rows = toReviewBlockRows(documentText, listOf(toReview), listOf(changed))

        assertEquals(listOf(BlockKind.CHANGED to 5, BlockKind.TO_REVIEW to 1), rows.map { it.kind to it.firstLine })
    }

    @Test
    fun `nothing marked gives no blocks to review`() {
        assertEquals(emptyList<BlockRow>(), toReviewBlockRows(documentText, emptyList(), emptyList()))
    }

    @Test
    fun `a mark that spills into the next line belongs to the block of the whole lines it covers`() {
        val documentText = "first line\nsecond line\nthird line\n"
        val markEndingInsideThirdLine = TextRange(0, documentText.indexOf("third") + 3)
        val blockOfFirstTwoLines = TextRange(0, documentText.indexOf("second line") + "second line".length)

        assertTrue(markBelongsToBlock(documentText, markEndingInsideThirdLine, blockOfFirstTwoLines))
    }

    @Test
    fun `a mark does not belong to a block of other lines`() {
        val documentText = "first line\nsecond line\nthird line\n"
        val markOnFirstLine = TextRange(0, "first line".length)
        val blockOfThirdLine = TextRange(documentText.indexOf("third"), documentText.indexOf("third") + "third line".length)

        assertFalse(markBelongsToBlock(documentText, markOnFirstLine, blockOfThirdLine))
    }
}
