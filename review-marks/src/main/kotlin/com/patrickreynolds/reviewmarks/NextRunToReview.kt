package com.patrickreynolds.reviewmarks

data class NextRunToReview(val firstLine: Int, val wrappedToTop: Boolean)

fun nextRunToReview(linesNeedingReview: List<Int>, currentLine: Int): NextRunToReview? {
    val lineNumbersNeedingReview = linesNeedingReview.toSet()
    val firstLinesOfRuns = linesNeedingReview.filter { (it - 1) !in lineNumbersNeedingReview }

    val runAfterCurrentLine = firstLinesOfRuns.firstOrNull { it > currentLine }
    if (runAfterCurrentLine != null) return NextRunToReview(runAfterCurrentLine, wrappedToTop = false)

    val firstRunInFile = firstLinesOfRuns.firstOrNull() ?: return null
    return NextRunToReview(firstRunInFile, wrappedToTop = true)
}
