package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewProgressTest {

    private val documentText = "first\nsecond\n\nthird\nfourth\nfifth\n"
    private val secondLineEnd = documentText.indexOf("second") + "second".length
    private val thirdLineStart = documentText.indexOf("third")
    private val fourthLineEnd = documentText.indexOf("fourth") + "fourth".length

    @Test
    fun `counts nothing when no code is marked`() {
        val progress = reviewProgress(documentText, emptyList(), emptyList(), emptyList())
        assertEquals(ReviewProgress(reviewedLineCount = 0, markedLineCount = 0, changedLineCount = 0), progress)
    }

    @Test
    fun `counts reviewed lines out of all the lines that are marked`() {
        val reviewed = TextRange(0, secondLineEnd)
        val toReview = TextRange(thirdLineStart, fourthLineEnd)

        val progress = reviewProgress(documentText, listOf(reviewed), listOf(toReview), emptyList())

        assertEquals(ReviewProgress(reviewedLineCount = 2, markedLineCount = 4, changedLineCount = 0), progress)
    }

    @Test
    fun `does not count code that is not marked`() {
        val reviewed = TextRange(0, secondLineEnd)

        val progress = reviewProgress(documentText, listOf(reviewed), emptyList(), emptyList())

        assertEquals(ReviewProgress(reviewedLineCount = 2, markedLineCount = 2, changedLineCount = 0), progress)
    }

    @Test
    fun `changed lines are marked but not reviewed, and are counted as changed`() {
        val reviewed = TextRange(0, secondLineEnd)
        val changed = TextRange(thirdLineStart, thirdLineStart + "third".length)

        val progress = reviewProgress(documentText, listOf(reviewed), emptyList(), listOf(changed))

        assertEquals(ReviewProgress(reviewedLineCount = 2, markedLineCount = 3, changedLineCount = 1), progress)
    }

    @Test
    fun `status text shows the percentage, the lines and the changed lines`() {
        val progress = ReviewProgress(reviewedLineCount = 120, markedLineCount = 300, changedLineCount = 2)
        assertEquals("Reviewed 40% (120/300 lines) · 2 changed", progress.statusText())
    }

    @Test
    fun `status text leaves out changed lines when there are none`() {
        val progress = ReviewProgress(reviewedLineCount = 1, markedLineCount = 4, changedLineCount = 0)
        assertEquals("Reviewed 25% (1/4 lines)", progress.statusText())
    }

    @Test
    fun `status text is empty when no code is marked`() {
        assertEquals("", ReviewProgress(0, 0, 0).statusText())
    }
}
