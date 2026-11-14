package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseCommentableLinesFromPatchTest {

    @Test
    fun `gives the new side lines of each hunk counting from one, context lines included`() {
        val patch = "@@ -1,2 +1,3 @@\n a\n+b\n c\n@@ -20,2 +21,4 @@\n x\n+y\n+y2\n z"

        assertEquals(
            listOf(CommentableLines("src/A.kt", 1, 3), CommentableLines("src/A.kt", 21, 24)),
            parseCommentableLinesFromPatch("src/A.kt", patch),
        )
    }

    @Test
    fun `a hunk that only removes lines has no lines to comment on`() {
        assertEquals(emptyList<CommentableLines>(), parseCommentableLinesFromPatch("src/A.kt", "@@ -1,2 +0,0 @@\n-gone\n-gone too"))
    }
}
