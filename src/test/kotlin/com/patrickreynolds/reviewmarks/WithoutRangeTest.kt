package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class WithoutRangeTest {

    private val markedBlock = TextRange(10, 30)

    @Test
    fun `removing the middle leaves a part before and a part after`() {
        assertEquals(
            listOf(TextRange(10, 15), TextRange(20, 30)),
            withoutRange(markedBlock, TextRange(15, 20)),
        )
    }

    @Test
    fun `removing the start leaves only the part after`() {
        assertEquals(listOf(TextRange(20, 30)), withoutRange(markedBlock, TextRange(5, 20)))
    }

    @Test
    fun `removing the end leaves only the part before`() {
        assertEquals(listOf(TextRange(10, 25)), withoutRange(markedBlock, TextRange(25, 40)))
    }

    @Test
    fun `removing everything leaves nothing`() {
        assertEquals(emptyList<TextRange>(), withoutRange(markedBlock, TextRange(0, 50)))
    }

    @Test
    fun `removing a range that does not touch the block leaves the whole block`() {
        assertEquals(listOf(markedBlock), withoutRange(markedBlock, TextRange(40, 50)))
        assertEquals(listOf(markedBlock), withoutRange(markedBlock, TextRange(0, 5)))
    }
}
