package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun linesNeedingReview(documentText: String, reviewedRanges: List<TextRange>): List<Int> {
    val reviewedAreas = joinAcrossWhitespace(documentText, reviewedRanges)
    return codeLinesOf(documentText)
        .filter { codeLine -> reviewedAreas.none { it.contains(codeLine.codeRange) } }
        .map { it.lineNumber }
}

private fun joinAcrossWhitespace(documentText: String, reviewedRanges: List<TextRange>): List<TextRange> {
    val joinedAreas = mutableListOf<TextRange>()
    reviewedRanges.sortedBy { it.startOffset }.forEach { reviewedRange ->
        val previousArea = joinedAreas.lastOrNull()
        val textBetweenIsOnlyWhitespace = previousArea != null &&
            reviewedRange.startOffset >= previousArea.endOffset &&
            documentText.substring(previousArea.endOffset, reviewedRange.startOffset).isBlank()
        val overlapsPreviousArea = previousArea != null && reviewedRange.startOffset < previousArea.endOffset
        if (previousArea != null && (textBetweenIsOnlyWhitespace || overlapsPreviousArea)) {
            joinedAreas[joinedAreas.lastIndex] =
                TextRange(previousArea.startOffset, maxOf(previousArea.endOffset, reviewedRange.endOffset))
        } else {
            joinedAreas += reviewedRange
        }
    }
    return joinedAreas
}
