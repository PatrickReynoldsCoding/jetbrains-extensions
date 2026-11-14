package com.patrickreynolds.reviewmarks

private val HUNK_HEADER = Regex("""^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@""")

private const val NEW_FILE_PREFIX = "+++ b/"

fun parseChangedLines(diffWithNoContext: String): Map<String, List<LineRange>> {
    val changedLinesByPath = mutableMapOf<String, MutableList<LineRange>>()
    var currentPath: String? = null
    diffWithNoContext.lineSequence().forEach { diffLine ->
        if (diffLine.startsWith("+++ ")) {
            currentPath = pathOfChangedFile(diffLine)
        } else if (diffLine.startsWith("@@")) {
            val path = currentPath ?: return@forEach
            val hunkHeader = HUNK_HEADER.find(diffLine) ?: return@forEach
            changedLinesByPath.getOrPut(path) { mutableListOf() }.add(changedLinesOfHunk(hunkHeader))
        }
    }
    return changedLinesByPath
}

fun parseChangedLinesFromPatch(filePatch: String): List<LineRange> {
    val addedLineNumbers = mutableListOf<Int>()
    var nextNewLine = 0
    filePatch.lineSequence().forEach { patchLine ->
        val hunkHeader = HUNK_HEADER.find(patchLine)
        when {
            hunkHeader != null -> nextNewLine = hunkHeader.groupValues[1].toInt() - 1
            patchLine.startsWith("+") -> addedLineNumbers += nextNewLine++
            patchLine.startsWith("-") || patchLine.startsWith("\\") -> Unit
            else -> nextNewLine++
        }
    }
    return joinConsecutiveLines(addedLineNumbers)
}

private fun joinConsecutiveLines(lineNumbers: List<Int>): List<LineRange> {
    val ranges = mutableListOf<LineRange>()
    lineNumbers.forEach { lineNumber ->
        val lastRange = ranges.lastOrNull()
        if (lastRange != null && lastRange.lastLine + 1 == lineNumber) {
            ranges[ranges.lastIndex] = LineRange(lastRange.firstLine, lineNumber)
        } else {
            ranges += LineRange(lineNumber, lineNumber)
        }
    }
    return ranges
}

private fun pathOfChangedFile(newFileLine: String): String? =
    if (newFileLine.startsWith(NEW_FILE_PREFIX)) newFileLine.removePrefix(NEW_FILE_PREFIX).substringBefore('\t') else null

private fun changedLinesOfHunk(hunkHeader: MatchResult): LineRange {
    val firstNewLine = hunkHeader.groupValues[1].toInt()
    val newLineCount = hunkHeader.groupValues[2].ifEmpty { "1" }.toInt()
    val onlyDeletedLines = newLineCount == 0
    if (onlyDeletedLines) return LineRange(maxOf(0, firstNewLine - 1), firstNewLine)
    return LineRange(firstNewLine - 1, firstNewLine - 1 + newLineCount - 1)
}
