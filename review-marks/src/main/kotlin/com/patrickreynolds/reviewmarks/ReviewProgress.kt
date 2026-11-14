package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

data class ReviewProgress(
    val reviewedLineCount: Int,
    val markedLineCount: Int,
    val changedLineCount: Int,
) {
    val percentReviewed: Int get() = if (markedLineCount == 0) 0 else reviewedLineCount * 100 / markedLineCount

    fun statusText(): String {
        if (markedLineCount == 0) return ""
        val reviewedText = "Reviewed $percentReviewed% ($reviewedLineCount/$markedLineCount lines)"
        return if (changedLineCount == 0) reviewedText else "$reviewedText · $changedLineCount changed"
    }
}

fun reviewProgress(
    documentText: String,
    reviewedRanges: List<TextRange>,
    toReviewRanges: List<TextRange>,
    changedRanges: List<TextRange>,
): ReviewProgress {
    val codeLines = codeLinesOf(documentText)
    val reviewedAreas = joinAcrossWhitespace(documentText, reviewedRanges)
    val markedAreas = joinAcrossWhitespace(documentText, reviewedRanges + toReviewRanges + changedRanges)

    return ReviewProgress(
        reviewedLineCount = codeLines.count { codeLine -> reviewedAreas.any { it.contains(codeLine.codeRange) } },
        markedLineCount = codeLines.count { codeLine -> markedAreas.any { it.contains(codeLine.codeRange) } },
        changedLineCount = codeLines.count { codeLine -> changedRanges.any { it.intersectsStrict(codeLine.codeRange) } },
    )
}
