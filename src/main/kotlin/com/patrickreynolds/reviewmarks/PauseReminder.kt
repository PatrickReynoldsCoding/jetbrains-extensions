package com.patrickreynolds.reviewmarks

private const val MILLISECONDS_PER_MINUTE = 60_000L

data class ReviewWindow(
    val startedAtMillis: Long,
    val lastBlockAtMillis: Long,
    val reviewedBlockCount: Int,
)

data class PauseReminder(
    val reviewedBlockCount: Int,
    val minutesReviewing: Long,
) {
    fun message(): String {
        val blocksText = if (reviewedBlockCount == 1) "1 block" else "$reviewedBlockCount blocks"
        return "You have reviewed $blocksText in $minutesReviewing minutes. This could be a good place to pause."
    }
}

data class PauseCheck(
    val windowAfterBlock: ReviewWindow?,
    val reminder: PauseReminder?,
)

fun checkForPause(
    windowBeforeBlock: ReviewWindow?,
    nowMillis: Long,
    minutesBetweenPauses: Int,
    blocksBetweenPauses: Int,
): PauseCheck {
    val millisBetweenPauses = minutesBetweenPauses * MILLISECONDS_PER_MINUTE
    val windowWentIdle = windowBeforeBlock != null &&
        minutesBetweenPauses > 0 &&
        nowMillis - windowBeforeBlock.lastBlockAtMillis > millisBetweenPauses

    val windowAfterBlock = if (windowBeforeBlock == null || windowWentIdle) {
        ReviewWindow(startedAtMillis = nowMillis, lastBlockAtMillis = nowMillis, reviewedBlockCount = 1)
    } else {
        windowBeforeBlock.copy(
            lastBlockAtMillis = nowMillis,
            reviewedBlockCount = windowBeforeBlock.reviewedBlockCount + 1,
        )
    }

    val minutesAreUp = minutesBetweenPauses > 0 && nowMillis - windowAfterBlock.startedAtMillis >= millisBetweenPauses
    val blocksAreUp = blocksBetweenPauses > 0 && windowAfterBlock.reviewedBlockCount >= blocksBetweenPauses
    if (!minutesAreUp && !blocksAreUp) return PauseCheck(windowAfterBlock, reminder = null)

    val minutesReviewing = (nowMillis - windowAfterBlock.startedAtMillis) / MILLISECONDS_PER_MINUTE
    return PauseCheck(
        windowAfterBlock = null,
        reminder = PauseReminder(windowAfterBlock.reviewedBlockCount, minutesReviewing),
    )
}
