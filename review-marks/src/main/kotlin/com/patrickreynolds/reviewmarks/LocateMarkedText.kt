package com.patrickreynolds.reviewmarks

import kotlin.math.abs

fun locateMarkedText(
    documentText: String,
    markedText: String,
    storedStartOffset: Int,
): Int? {
    if (markedText.isEmpty()) return null
    if (documentText.startsWith(markedText, storedStartOffset)) return storedStartOffset
    return occurrencesOf(markedText, documentText).minByOrNull { occurrence -> abs(occurrence - storedStartOffset) }
}

private fun occurrencesOf(markedText: String, documentText: String): List<Int> =
    generateSequence(documentText.indexOf(markedText).takeIf { it >= 0 }) { previousOccurrence ->
        documentText.indexOf(markedText, previousOccurrence + 1).takeIf { it >= 0 }
    }.toList()
