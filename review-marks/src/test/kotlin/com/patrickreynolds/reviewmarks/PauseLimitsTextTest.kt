package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseLimitsTextTest {

    @Test
    fun `names both limits when both are set`() {
        assertEquals("every 30 min or 10 blocks", pauseLimitsText(minutesBetweenPauses = 30, blocksBetweenPauses = 10))
    }

    @Test
    fun `names only the minutes when the blocks limit is off`() {
        assertEquals("every 30 min", pauseLimitsText(minutesBetweenPauses = 30, blocksBetweenPauses = 0))
    }

    @Test
    fun `names only the blocks when the minutes limit is off`() {
        assertEquals("every 10 blocks", pauseLimitsText(minutesBetweenPauses = 0, blocksBetweenPauses = 10))
    }

    @Test
    fun `says that no limit is set when both are off`() {
        assertEquals("no limit set", pauseLimitsText(minutesBetweenPauses = 0, blocksBetweenPauses = 0))
    }
}
