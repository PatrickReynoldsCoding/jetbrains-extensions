package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseChangedLinesFromPatchTest {

    @Test
    fun `gives the added lines counting from zero and skips context lines`() {
        val patch = "@@ -1,4 +1,5 @@ fun x()\n context one\n+added two\n+added three\n context four\n context five"
        assertEquals(listOf(LineRange(1, 2)), parseChangedLinesFromPatch(patch))
    }

    @Test
    fun `counts lines in the new file when lines were removed above`() {
        val patch = "@@ -10,3 +10,3 @@\n context\n-old line\n+new line\n context"
        assertEquals(listOf(LineRange(10, 10)), parseChangedLinesFromPatch(patch))
    }

    @Test
    fun `gives separate ranges for separate hunks`() {
        val patch = "@@ -1,2 +1,3 @@\n a\n+b\n c\n@@ -20,2 +21,3 @@\n x\n+y\n z"
        assertEquals(listOf(LineRange(1, 1), LineRange(21, 21)), parseChangedLinesFromPatch(patch))
    }

    @Test
    fun `a patch that only removes lines has no changed lines`() {
        assertEquals(emptyList<LineRange>(), parseChangedLinesFromPatch("@@ -1,2 +0,0 @@\n-gone\n-gone too"))
    }

    @Test
    fun `ignores the no newline marker`() {
        val patch = "@@ -1 +1 @@\n-old\n\\ No newline at end of file\n+new\n\\ No newline at end of file"
        assertEquals(listOf(LineRange(0, 0)), parseChangedLinesFromPatch(patch))
    }
}
