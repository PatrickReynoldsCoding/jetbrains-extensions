package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSummaryTest {

    private val today = "2026-11-14"

    @Test
    fun `adds up today's lines and files and ignores other days`() {
        val reviewLog = listOf(
            ReviewLogEntry(today, "file:///A.kt", 100),
            ReviewLogEntry(today, "file:///B.kt", 114),
            ReviewLogEntry("2026-11-13", "file:///C.kt", 999),
        )

        val summary = summarizeReview(reviewLog, today, emptyList())

        assertEquals(214, summary.reviewedLineCountToday)
        assertEquals(2, summary.fileCountReviewedToday)
    }

    @Test
    fun `the text says what was reviewed today and what is left, biggest file first`() {
        val summary = summarizeReview(
            reviewLog = listOf(ReviewLogEntry(today, "file:///A.kt", 214), ReviewLogEntry(today, "file:///B.kt", 1)),
            day = today,
            filesWithLinesLeft = listOf(
                FileLinesLeft("file:///src/Small.kt", 10),
                FileLinesLeft("file:///src/Big.kt", 120),
            ),
        )

        assertEquals(
            "Today (2026-11-14)\nReviewed 215 lines in 2 files.\n\n" +
                "Still to review: 130 lines in 2 files.\n  • Big.kt: 120 lines\n  • Small.kt: 10 lines",
            summary.text(),
        )
    }

    @Test
    fun `the text says so when nothing was reviewed today and nothing is left`() {
        assertEquals(
            "Today (2026-11-14)\nYou have not reviewed any lines today yet.\n\n" +
                "Nothing left to review in the files you are reviewing.",
            summarizeReview(emptyList(), today, emptyList()).text(),
        )
    }

    @Test
    fun `the text lists at most eight files and counts the rest`() {
        val tenFiles = (1..10).map { FileLinesLeft("file:///src/File$it.kt", it) }

        val text = summarizeReview(emptyList(), today, tenFiles).text()

        assertEquals(8, text.lines().count { it.startsWith("  • ") })
        assertEquals(true, text.endsWith("  … and 2 files more"))
    }
}
