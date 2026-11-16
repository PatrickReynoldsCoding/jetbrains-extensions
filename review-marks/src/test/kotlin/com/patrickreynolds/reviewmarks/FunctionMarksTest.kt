package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionMarksTest {

    private val documentText = "line0\nline1\nline2\nline3\nline4\n"
    private val functionOnLinesOneAndTwo = TextRange(6, 17)
    private val noImportLines: (Int) -> Boolean = { false }

    private fun functionOnLinesOneAndTwoAt(lineNumber: Int): TextRange? =
        if (lineNumber in 1..2) functionOnLinesOneAndTwo else null

    @Test
    fun `a change inside a function marks the whole function once`() {
        val actualRanges = rangesToMarkForChangedLines(
            documentText = documentText,
            changedLines = listOf(LineRange(1, 1), LineRange(2, 2)),
            functionRangeAtLine = ::functionOnLinesOneAndTwoAt,
            isImportLine = noImportLines,
            skipImports = false,
        )

        assertEquals(listOf(functionOnLinesOneAndTwo), actualRanges)
    }

    @Test
    fun `changed lines outside functions stay as runs of neighbouring lines`() {
        val actualRanges = rangesToMarkForChangedLines(
            documentText = documentText,
            changedLines = listOf(LineRange(0, 0), LineRange(2, 4)),
            functionRangeAtLine = ::functionOnLinesOneAndTwoAt,
            isImportLine = noImportLines,
            skipImports = false,
        )

        assertEquals(listOf(TextRange(0, 5), functionOnLinesOneAndTwo, TextRange(18, 29)), actualRanges)
    }

    @Test
    fun `import lines are left out when imports are skipped`() {
        val actualRanges = rangesToMarkForChangedLines(
            documentText = documentText,
            changedLines = listOf(LineRange(0, 0), LineRange(3, 3)),
            functionRangeAtLine = ::functionOnLinesOneAndTwoAt,
            isImportLine = { lineNumber -> lineNumber == 0 },
            skipImports = true,
        )

        assertEquals(listOf(TextRange(18, 23)), actualRanges)
    }

    @Test
    fun `import lines are marked when imports are not skipped`() {
        val actualRanges = rangesToMarkForChangedLines(
            documentText = documentText,
            changedLines = listOf(LineRange(0, 0)),
            functionRangeAtLine = ::functionOnLinesOneAndTwoAt,
            isImportLine = { lineNumber -> lineNumber == 0 },
            skipImports = false,
        )

        assertEquals(listOf(TextRange(0, 5)), actualRanges)
    }
}
