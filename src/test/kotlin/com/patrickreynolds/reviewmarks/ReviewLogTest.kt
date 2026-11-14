package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewLogTest {

    private val today = "2026-11-14"

    @Test
    fun `adds a new entry for a file reviewed for the first time today`() {
        val log = addToReviewLog(emptyList(), today, "file:///A.kt", 12)
        assertEquals(listOf(ReviewLogEntry(today, "file:///A.kt", 12)), log)
    }

    @Test
    fun `adds to the entry of the same file and day`() {
        val log = addToReviewLog(listOf(ReviewLogEntry(today, "file:///A.kt", 12)), today, "file:///A.kt", 5)
        assertEquals(listOf(ReviewLogEntry(today, "file:///A.kt", 17)), log)
    }

    @Test
    fun `keeps a separate entry for another file`() {
        val log = addToReviewLog(listOf(ReviewLogEntry(today, "file:///A.kt", 12)), today, "file:///B.kt", 3)
        assertEquals(2, log.size)
    }

    @Test
    fun `ignores a mark that reviewed no new lines`() {
        assertEquals(emptyList<ReviewLogEntry>(), addToReviewLog(emptyList(), today, "file:///A.kt", 0))
    }

    @Test
    fun `forgets entries older than thirty days`() {
        val oldEntry = ReviewLogEntry("2026-10-01", "file:///A.kt", 50)
        val recentEntry = ReviewLogEntry("2026-10-20", "file:///B.kt", 30)

        val log = addToReviewLog(listOf(oldEntry, recentEntry), today, "file:///C.kt", 1)

        assertEquals(listOf(recentEntry, ReviewLogEntry(today, "file:///C.kt", 1)), log)
    }
}
