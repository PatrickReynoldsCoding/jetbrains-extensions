package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextRunToReviewTest {

    private val linesNeedingReview = listOf(2, 3, 4, 9, 10, 20)

    @Test
    fun `goes to the start of the next run below the current line`() {
        assertEquals(NextRunToReview(9, wrappedToTop = false), nextRunToReview(linesNeedingReview, currentLine = 5))
    }

    @Test
    fun `skips the rest of the run the caret is in`() {
        assertEquals(NextRunToReview(9, wrappedToTop = false), nextRunToReview(linesNeedingReview, currentLine = 3))
    }

    @Test
    fun `continues from the top after the last run`() {
        assertEquals(NextRunToReview(2, wrappedToTop = true), nextRunToReview(linesNeedingReview, currentLine = 20))
    }

    @Test
    fun `returns nothing when every line is reviewed`() {
        assertNull(nextRunToReview(emptyList(), currentLine = 0))
    }
}
