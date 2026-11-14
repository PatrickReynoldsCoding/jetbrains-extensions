package com.patrickreynolds.reviewmarks

private const val MOST_FILES_LISTED = 8

data class FileLinesLeft(val fileUrl: String, val lineCount: Int)

data class ReviewSummary(
    val day: String,
    val reviewedLineCountToday: Int,
    val fileCountReviewedToday: Int,
    val filesWithLinesLeft: List<FileLinesLeft>,
) {
    fun text(): String {
        val reviewedToday = if (reviewedLineCountToday == 0) {
            "You have not reviewed any lines today yet."
        } else {
            "Reviewed $reviewedLineCountToday lines in ${filesText(fileCountReviewedToday)}."
        }
        return "Today ($day)\n$reviewedToday\n\n${linesLeftText()}"
    }

    private fun linesLeftText(): String {
        if (filesWithLinesLeft.isEmpty()) return "Nothing left to review in the files you are reviewing."

        val lineCountLeft = filesWithLinesLeft.sumOf { it.lineCount }
        val heading = "Still to review: $lineCountLeft lines in ${filesText(filesWithLinesLeft.size)}."
        val listedFiles = filesWithLinesLeft.take(MOST_FILES_LISTED).joinToString("\n") { fileLinesLeft ->
            "  • ${fileLinesLeft.fileUrl.substringAfterLast('/')}: ${fileLinesLeft.lineCount} lines"
        }
        val unlistedFileCount = filesWithLinesLeft.size - MOST_FILES_LISTED
        val moreFilesText = if (unlistedFileCount > 0) "\n  … and ${filesText(unlistedFileCount)} more" else ""
        return "$heading\n$listedFiles$moreFilesText"
    }

    private fun filesText(fileCount: Int) = if (fileCount == 1) "1 file" else "$fileCount files"
}

fun summarizeReview(
    reviewLog: List<ReviewLogEntry>,
    day: String,
    filesWithLinesLeft: List<FileLinesLeft>,
): ReviewSummary {
    val entriesToday = reviewLog.filter { it.day == day }
    return ReviewSummary(
        day = day,
        reviewedLineCountToday = entriesToday.sumOf { it.reviewedLineCount },
        fileCountReviewedToday = entriesToday.map { it.fileUrl }.distinct().size,
        filesWithLinesLeft = filesWithLinesLeft.sortedByDescending { it.lineCount },
    )
}
