package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

data class ReviewProgress(
    val reviewedLineCount: Int,
    val codeLineCount: Int,
    val changedLineCount: Int,
) {
    val percentReviewed: Int get() = if (codeLineCount == 0) 0 else reviewedLineCount * 100 / codeLineCount

    fun statusText(): String {
        if (codeLineCount == 0) return ""
        val reviewedText = "Reviewed $percentReviewed% ($reviewedLineCount/$codeLineCount lines)"
        return if (changedLineCount == 0) reviewedText else "$reviewedText · $changedLineCount changed"
    }
}

fun reviewProgress(
    documentText: String,
    reviewedRanges: List<TextRange>,
    changedRanges: List<TextRange>,
    outOfScopeRanges: List<TextRange>,
): ReviewProgress {
    val codeLines = codeLinesInScope(documentText, outOfScopeRanges)
    val unreviewedLineCount = linesNeedingReview(documentText, reviewedRanges, outOfScopeRanges).size
    val changedLineCount = codeLines.count { codeLine ->
        changedRanges.any { it.intersectsStrict(codeLine.codeRange) }
    }
    return ReviewProgress(
        reviewedLineCount = codeLines.size - unreviewedLineCount,
        codeLineCount = codeLines.size,
        changedLineCount = changedLineCount,
    )
}
