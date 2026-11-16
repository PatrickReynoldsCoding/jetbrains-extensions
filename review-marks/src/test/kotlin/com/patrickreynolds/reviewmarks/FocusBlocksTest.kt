package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusBlocksTest {

    private fun focusBlock(id: String, parentId: String = TOP_LEVEL_FOCUS_PARENT_ID) =
        StoredFocusBlock(id = id, parentId = parentId, fileUrl = "file:///Example.kt", startOffset = 0, markedText = "code of $id")

    private fun idsWithParents(focusBlocks: List<StoredFocusBlock>): List<Pair<String, String>> =
        focusBlocks.map { it.id to it.parentId }

    private val threeTopLevelBlocks = listOf(focusBlock("first"), focusBlock("second"), focusBlock("third"))

    @Test
    fun `indenting a block puts it last under the block above`() {
        val actualBlocks = focusBlocksAfterIndenting(threeTopLevelBlocks, "second")!!

        assertEquals(listOf("first" to "", "second" to "first", "third" to ""), idsWithParents(actualBlocks))
    }

    @Test
    fun `the first block of a level cannot be indented`() {
        assertNull(focusBlocksAfterIndenting(threeTopLevelBlocks, "first"))
    }

    @Test
    fun `outdenting a block puts it right after its parent`() {
        val nestedBlocks = listOf(focusBlock("first"), focusBlock("inner", "first"), focusBlock("innerToo", "first"), focusBlock("second"))

        val actualBlocks = focusBlocksAfterOutdenting(nestedBlocks, "inner")!!

        assertEquals(listOf("first" to "", "innerToo" to "first", "inner" to "", "second" to ""), idsWithParents(actualBlocks))
    }

    @Test
    fun `a top level block cannot be outdented`() {
        assertNull(focusBlocksAfterOutdenting(threeTopLevelBlocks, "second"))
    }

    @Test
    fun `moving a block carries the blocks nested under it`() {
        val nestedBlocks = listOf(focusBlock("first"), focusBlock("second"), focusBlock("underSecond", "second"), focusBlock("third"))

        val actualBlocks = focusBlocksAfterMoving(nestedBlocks, "second", "third", 0)!!

        assertEquals(listOf("first" to "", "third" to "", "second" to "third", "underSecond" to "second"), idsWithParents(actualBlocks))
    }

    @Test
    fun `a block cannot be moved under itself or under a block nested in it`() {
        val nestedBlocks = listOf(focusBlock("first"), focusBlock("inner", "first"))

        assertNull(focusBlocksAfterMoving(nestedBlocks, "first", "first", 0))
        assertNull(focusBlocksAfterMoving(nestedBlocks, "first", "inner", 0))
    }

    @Test
    fun `a block can be moved to a place among the top level blocks`() {
        val actualBlocks = focusBlocksAfterMoving(threeTopLevelBlocks, "third", TOP_LEVEL_FOCUS_PARENT_ID, 0)!!

        assertEquals(listOf("third", "first", "second"), actualBlocks.map { it.id })
    }

    @Test
    fun `shifting a block down swaps it with the next block of its level`() {
        val actualBlocks = focusBlocksAfterShiftingAmongSiblings(threeTopLevelBlocks, "first", 1)!!

        assertEquals(listOf("second", "first", "third"), actualBlocks.map { it.id })
    }

    @Test
    fun `the last block cannot be shifted down`() {
        assertNull(focusBlocksAfterShiftingAmongSiblings(threeTopLevelBlocks, "third", 1))
    }

    @Test
    fun `removing a block also removes the blocks nested under it`() {
        val nestedBlocks = listOf(focusBlock("first"), focusBlock("inner", "first"), focusBlock("deeper", "inner"), focusBlock("second"))

        val actualBlocks = focusBlocksWithout(nestedBlocks, listOf("first"))

        assertEquals(listOf("second"), actualBlocks.map { it.id })
    }
}
