package com.patrickreynolds.reviewmarks

fun locateMarkedText(
    documentText: String,
    markedText: String,
    storedStartOffset: Int,
): Int? {
    if (markedText.isEmpty()) return null
    if (documentText.startsWith(markedText, storedStartOffset)) return storedStartOffset

    val firstOccurrence = documentText.indexOf(markedText)
    if (firstOccurrence < 0) return null
    val markedTextOccursAgain = documentText.indexOf(markedText, firstOccurrence + 1) >= 0
    return if (markedTextOccursAgain) null else firstOccurrence
}
