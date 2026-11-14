package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun linesNeedingReview(
    documentText: String,
    reviewedRanges: List<TextRange>,
    outOfScopeRanges: List<TextRange>,
): List<Int> {
    val reviewedAreas = joinAcrossWhitespace(documentText, reviewedRanges)
    return codeLinesInScope(documentText, outOfScopeRanges)
        .filter { codeLine -> reviewedAreas.none { it.contains(codeLine.codeRange) } }
        .map { it.lineNumber }
}
