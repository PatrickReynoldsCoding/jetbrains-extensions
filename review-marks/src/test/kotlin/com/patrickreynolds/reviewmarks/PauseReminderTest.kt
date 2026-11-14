package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseReminderTest {

    private val minute = 60_000L

    @Test
    fun `the first reviewed block starts a window and gives no reminder`() {
        val check = checkForPause(null, nowMillis = 0, minutesBetweenPauses = 30, blocksBetweenPauses = 0)

        assertEquals(ReviewWindow(0, 0, 1), check.windowAfterBlock)
        assertNull(check.reminder)
    }

    @Test
    fun `reminds once the minutes are up and then starts a new window`() {
        val window = ReviewWindow(startedAtMillis = 0, lastBlockAtMillis = 25 * minute, reviewedBlockCount = 4)

        val check = checkForPause(window, nowMillis = 30 * minute, minutesBetweenPauses = 30, blocksBetweenPauses = 0)

        assertEquals(PauseReminder(reviewedBlockCount = 5, minutesReviewing = 30), check.reminder)
        assertNull(check.windowAfterBlock)
    }

    @Test
    fun `does not remind before the minutes are up`() {
        val window = ReviewWindow(startedAtMillis = 0, lastBlockAtMillis = 10 * minute, reviewedBlockCount = 2)

        val check = checkForPause(window, nowMillis = 20 * minute, minutesBetweenPauses = 30, blocksBetweenPauses = 0)

        assertNull(check.reminder)
        assertEquals(ReviewWindow(0, 20 * minute, 3), check.windowAfterBlock)
    }

    @Test
    fun `reminds once enough blocks are reviewed`() {
        val window = ReviewWindow(startedAtMillis = 0, lastBlockAtMillis = minute, reviewedBlockCount = 9)

        val check = checkForPause(window, nowMillis = 2 * minute, minutesBetweenPauses = 0, blocksBetweenPauses = 10)

        assertEquals(PauseReminder(reviewedBlockCount = 10, minutesReviewing = 2), check.reminder)
    }

    @Test
    fun `starts again when the reviewer was away for longer than the pause length`() {
        val window = ReviewWindow(startedAtMillis = 0, lastBlockAtMillis = 5 * minute, reviewedBlockCount = 8)

        val check = checkForPause(window, nowMillis = 60 * minute, minutesBetweenPauses = 30, blocksBetweenPauses = 0)

        assertNull(check.reminder)
        assertEquals(ReviewWindow(60 * minute, 60 * minute, 1), check.windowAfterBlock)
    }

    @Test
    fun `never reminds when both limits are off`() {
        val window = ReviewWindow(startedAtMillis = 0, lastBlockAtMillis = minute, reviewedBlockCount = 500)

        val check = checkForPause(window, nowMillis = 1_000 * minute, minutesBetweenPauses = 0, blocksBetweenPauses = 0)

        assertNull(check.reminder)
    }

    @Test
    fun `the message says how many blocks and minutes`() {
        assertEquals(
            "You have reviewed 12 blocks in 31 minutes. This could be a good place to pause.",
            PauseReminder(12, 31).message(),
        )
        assertEquals(
            "You have reviewed 1 block in 0 minutes. This could be a good place to pause.",
            PauseReminder(1, 0).message(),
        )
    }
}
