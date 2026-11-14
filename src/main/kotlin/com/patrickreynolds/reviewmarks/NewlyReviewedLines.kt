package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun newlyReviewedLineCount(
    documentText: String,
    markedRange: TextRange,
    reviewedRangesBefore: List<TextRange>,
    outOfScopeRanges: List<TextRange>,
): Int {
    val lineNumbersNotReviewedBefore = linesNeedingReview(documentText, reviewedRangesBefore, outOfScopeRanges).toSet()
    return codeLinesOf(documentText).count { codeLine ->
        codeLine.lineNumber in lineNumbersNotReviewedBefore && markedRange.contains(codeLine.codeRange)
    }
}
