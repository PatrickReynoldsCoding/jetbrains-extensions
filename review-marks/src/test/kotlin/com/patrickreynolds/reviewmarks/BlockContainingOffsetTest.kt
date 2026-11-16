package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockContainingOffsetTest {

    private val fileUrl = "file:///project/Client.kt"
    private val otherFileUrl = "file:///project/Config.kt"

    private fun blockOfFile(fileUrl: String, startOffset: Int, endOffset: Int) = BlockOfFile(
        fileUrl,
        BlockRow(BlockKind.TO_REVIEW, firstLine = 1, lastLine = 3, codeLineCount = 3, preview = "fun send()", startOffset = startOffset, endOffset = endOffset),
    )

    @Test
    fun `finds the block that holds a click inside it`() {
        val sendBlock = blockOfFile(fileUrl, startOffset = 10, endOffset = 50)
        val retryBlock = blockOfFile(fileUrl, startOffset = 80, endOffset = 120)

        assertEquals(retryBlock, blockContainingOffset(listOf(sendBlock, retryBlock), fileUrl, offset = 100))
    }

    @Test
    fun `counts a click at the very start or the very end of a block as inside it`() {
        val sendBlock = blockOfFile(fileUrl, startOffset = 10, endOffset = 50)

        assertEquals(sendBlock, blockContainingOffset(listOf(sendBlock), fileUrl, offset = 10))
        assertEquals(sendBlock, blockContainingOffset(listOf(sendBlock), fileUrl, offset = 50))
    }

    @Test
    fun `finds nothing for a click between two blocks`() {
        val sendBlock = blockOfFile(fileUrl, startOffset = 10, endOffset = 50)
        val retryBlock = blockOfFile(fileUrl, startOffset = 80, endOffset = 120)

        assertNull(blockContainingOffset(listOf(sendBlock, retryBlock), fileUrl, offset = 60))
    }

    @Test
    fun `ignores a block of another file at the same offset`() {
        val blockOfOtherFile = blockOfFile(otherFileUrl, startOffset = 10, endOffset = 50)

        assertNull(blockContainingOffset(listOf(blockOfOtherFile), fileUrl, offset = 20))
    }
}
