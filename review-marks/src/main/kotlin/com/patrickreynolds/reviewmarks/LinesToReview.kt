package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun linesToReview(documentText: String, rangesToReview: List<TextRange>): List<Int> {
    val areasToReview = joinAcrossWhitespace(documentText, rangesToReview)
    return codeLinesOf(documentText)
        .filter { codeLine -> areasToReview.any { it.contains(codeLine.codeRange) } }
        .map { it.lineNumber }
}
