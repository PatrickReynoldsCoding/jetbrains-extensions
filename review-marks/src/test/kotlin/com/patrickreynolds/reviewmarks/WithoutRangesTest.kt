package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class WithoutRangesTest {

    private val markedBlock = TextRange(10, 50)

    @Test
    fun `leaves the parts between and around the removed ranges`() {
        val removedRanges = listOf(TextRange(15, 20), TextRange(30, 35))
        assertEquals(
            listOf(TextRange(10, 15), TextRange(20, 30), TextRange(35, 50)),
            withoutRanges(markedBlock, removedRanges),
        )
    }

    @Test
    fun `leaves the whole block when nothing is removed`() {
        assertEquals(listOf(markedBlock), withoutRanges(markedBlock, emptyList()))
    }

    @Test
    fun `leaves nothing when the removed ranges cover the block`() {
        assertEquals(emptyList<TextRange>(), withoutRanges(markedBlock, listOf(TextRange(0, 30), TextRange(30, 60))))
    }
}
