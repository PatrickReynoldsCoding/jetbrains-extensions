package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkBranchChangesActionTest {

    @Test
    fun `names the branch the changes are compared with`() {
        assertEquals("Mark All Branch Changes to Review (vs origin/main)", markBranchChangesActionText("origin/main"))
    }
}
