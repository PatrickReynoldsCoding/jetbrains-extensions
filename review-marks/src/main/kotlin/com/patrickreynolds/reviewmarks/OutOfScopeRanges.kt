package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun outOfScopeRanges(documentText: String, changedLines: List<LineRange>): List<TextRange> {
    val ranges = mutableListOf<TextRange>()
    var lineStartOffset = 0
    var unchangedStretchStart: Int? = null
    var unchangedStretchEnd = 0
    documentText.split('\n').forEachIndexed { lineNumber, lineText ->
        val lineEndOffset = lineStartOffset + lineText.length
        val lineIsChanged = changedLines.any { it.contains(lineNumber) }
        if (lineIsChanged) {
            unchangedStretchStart?.let { ranges += TextRange(it, unchangedStretchEnd) }
            unchangedStretchStart = null
        } else {
            if (unchangedStretchStart == null) unchangedStretchStart = lineStartOffset
            unchangedStretchEnd = lineEndOffset
        }
        lineStartOffset = lineEndOffset + 1
    }
    unchangedStretchStart?.let { ranges += TextRange(it, unchangedStretchEnd) }
    return ranges.filter { !it.isEmpty }
}
