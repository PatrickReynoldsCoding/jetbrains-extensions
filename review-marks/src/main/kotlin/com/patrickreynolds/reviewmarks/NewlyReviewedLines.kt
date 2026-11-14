package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun newlyReviewedLineCount(
    documentText: String,
    markedRange: TextRange,
    reviewedRangesBefore: List<TextRange>,
): Int {
    val reviewedAreasBefore = joinAcrossWhitespace(documentText, reviewedRangesBefore)
    return codeLinesOf(documentText).count { codeLine ->
        markedRange.contains(codeLine.codeRange) && reviewedAreasBefore.none { it.contains(codeLine.codeRange) }
    }
}
