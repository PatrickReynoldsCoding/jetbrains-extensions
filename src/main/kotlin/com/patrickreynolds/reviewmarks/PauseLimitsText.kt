package com.patrickreynolds.reviewmarks

fun pauseLimitsText(minutesBetweenPauses: Int, blocksBetweenPauses: Int): String {
    val minutesText = "$minutesBetweenPauses min"
    val blocksText = "$blocksBetweenPauses blocks"
    return when {
        minutesBetweenPauses > 0 && blocksBetweenPauses > 0 -> "every $minutesText or $blocksText"
        minutesBetweenPauses > 0 -> "every $minutesText"
        blocksBetweenPauses > 0 -> "every $blocksText"
        else -> "no limit set"
    }
}
