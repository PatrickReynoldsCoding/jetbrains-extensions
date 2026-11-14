package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class OutOfScopeRangesTest {

    private val documentText = "zero\none\ntwo\nthree\nfour\n"
    private val lineTwoStart = documentText.indexOf("two")
    private val lineThreeStart = documentText.indexOf("three")
    private val lineFourStart = documentText.indexOf("four")

    @Test
    fun `everything is out of scope when no line changed`() {
        assertEquals(listOf(TextRange(0, documentText.length)), outOfScopeRanges(documentText, emptyList()))
    }

    @Test
    fun `leaves out the changed line and keeps the stretches above and below it`() {
        val ranges = outOfScopeRanges(documentText, listOf(LineRange(2, 2)))
        assertEquals(
            listOf(TextRange(0, lineTwoStart - 1), TextRange(lineThreeStart, documentText.length)),
            ranges,
        )
    }

    @Test
    fun `nothing is out of scope when every line changed`() {
        assertEquals(emptyList<TextRange>(), outOfScopeRanges(documentText, listOf(LineRange(0, 10))))
    }

    @Test
    fun `two changed ranges leave the stretch between them out of scope`() {
        val ranges = outOfScopeRanges(documentText, listOf(LineRange(0, 0), LineRange(4, 4)))
        assertEquals(listOf(TextRange(documentText.indexOf("one"), lineFourStart - 1)), ranges)
    }
}
