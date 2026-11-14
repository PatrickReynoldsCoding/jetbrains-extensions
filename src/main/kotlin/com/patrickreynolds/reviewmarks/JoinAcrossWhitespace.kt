package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun joinAcrossWhitespace(documentText: String, ranges: List<TextRange>): List<TextRange> {
    val joinedAreas = mutableListOf<TextRange>()
    ranges.sortedBy { it.startOffset }.forEach { range ->
        val previousArea = joinedAreas.lastOrNull()
        val overlapsPreviousArea = previousArea != null && range.startOffset < previousArea.endOffset
        val onlyWhitespaceBetween = previousArea != null &&
            range.startOffset >= previousArea.endOffset &&
            documentText.substring(previousArea.endOffset, range.startOffset).isBlank()
        if (previousArea != null && (overlapsPreviousArea || onlyWhitespaceBetween)) {
            joinedAreas[joinedAreas.lastIndex] =
                TextRange(previousArea.startOffset, maxOf(previousArea.endOffset, range.endOffset))
        } else {
            joinedAreas += range
        }
    }
    return joinedAreas
}
