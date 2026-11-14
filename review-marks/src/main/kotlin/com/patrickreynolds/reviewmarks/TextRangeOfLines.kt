package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun textRangeOfLines(documentText: String, lines: LineRange): TextRange? {
    val lineTexts = documentText.split('\n')
    if (lines.firstLine < 0 || lines.firstLine >= lineTexts.size) return null

    val lastLine = minOf(lines.lastLine, lineTexts.lastIndex)
    val startOffset = lineTexts.take(lines.firstLine).sumOf { it.length + 1 }
    val endOffset = startOffset + lineTexts.subList(lines.firstLine, lastLine + 1).sumOf { it.length + 1 } - 1
    val textOfLines = documentText.substring(startOffset, endOffset)
    return if (textOfLines.isBlank()) null else TextRange(startOffset, endOffset)
}
