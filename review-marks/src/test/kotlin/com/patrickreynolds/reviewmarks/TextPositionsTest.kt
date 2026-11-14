package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextPositionsTest {

    @Test
    fun `line numbers start at one`() {
        val text = "a\nb\nc"
        assertEquals(1, lineNumberAt(text, 0))
        assertEquals(2, lineNumberAt(text, 2))
        assertEquals(3, lineNumberAt(text, 4))
    }

    @Test
    fun `an offset past the end is the last line`() {
        assertEquals(3, lineNumberAt("a\nb\nc", 99))
    }

    @Test
    fun `a file inside the repository gets a path relative to its root`() {
        assertEquals("src/A.kt", relativePathIn("/repo", "/repo/src/A.kt"))
        assertEquals("src/A.kt", relativePathIn("/repo/", "/repo/src/A.kt"))
    }

    @Test
    fun `a file outside the repository has no relative path`() {
        assertNull(relativePathIn("/repo", "/other/src/A.kt"))
        assertNull(relativePathIn("/repo", "/repository/A.kt"))
    }
}
