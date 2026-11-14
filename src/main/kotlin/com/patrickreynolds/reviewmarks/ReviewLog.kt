package com.patrickreynolds.reviewmarks

import java.time.LocalDate

private const val DAYS_TO_KEEP = 30L

data class ReviewLogEntry(
    var day: String = "",
    var fileUrl: String = "",
    var reviewedLineCount: Int = 0,
)

fun addToReviewLog(
    log: List<ReviewLogEntry>,
    day: String,
    fileUrl: String,
    reviewedLineCount: Int,
): List<ReviewLogEntry> {
    val entryForFileToday = log.firstOrNull { it.day == day && it.fileUrl == fileUrl }
    val logWithLines = when {
        reviewedLineCount <= 0 -> log
        entryForFileToday == null -> log + ReviewLogEntry(day, fileUrl, reviewedLineCount)
        else -> log.map { entry ->
            if (entry == entryForFileToday) entry.copy(reviewedLineCount = entry.reviewedLineCount + reviewedLineCount) else entry
        }
    }
    val oldestDayToKeep = LocalDate.parse(day).minusDays(DAYS_TO_KEEP).toString()
    return logWithLines.filter { it.day >= oldestDayToKeep }
}
