package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class GitDiffParserTest {

    private val diffWithNoContext = """
        diff --git a/src/A.kt b/src/A.kt
        index 111..222 100644
        --- a/src/A.kt
        +++ b/src/A.kt
        @@ -3,0 +4,2 @@ fun x()
        +added one
        +added two
        @@ -10 +12 @@
        -old
        +new
        @@ -20,2 +21,0 @@
        -gone one
        -gone two
        diff --git a/old.txt b/old.txt
        deleted file mode 100644
        --- a/old.txt
        +++ /dev/null
        @@ -1,2 +0,0 @@
        -x
        -y
        diff --git a/dir with space/B.kt b/dir with space/B.kt
        --- a/dir with space/B.kt
        +++ b/dir with space/B.kt
        @@ -1 +1 @@
        -a
        +b
    """.trimIndent()

    private val changedLinesByPath = parseChangedLines(diffWithNoContext)

    @Test
    fun `a hunk that adds lines gives those lines counting from zero`() {
        assertEquals(LineRange(3, 4), changedLinesByPath.getValue("src/A.kt")[0])
    }

    @Test
    fun `a hunk that changes one line gives that line`() {
        assertEquals(LineRange(11, 11), changedLinesByPath.getValue("src/A.kt")[1])
    }

    @Test
    fun `a hunk that only deletes lines gives the lines on both sides of the deletion`() {
        assertEquals(LineRange(20, 21), changedLinesByPath.getValue("src/A.kt")[2])
    }

    @Test
    fun `a deleted file has no lines to review`() {
        assertEquals(false, changedLinesByPath.containsKey("old.txt"))
    }

    @Test
    fun `a path with a space is read without the trailing tab`() {
        assertEquals(listOf(LineRange(0, 0)), changedLinesByPath.getValue("dir with space/B.kt"))
    }

    @Test
    fun `an empty diff gives no files`() {
        assertEquals(emptyMap<String, List<LineRange>>(), parseChangedLines(""))
    }
}
