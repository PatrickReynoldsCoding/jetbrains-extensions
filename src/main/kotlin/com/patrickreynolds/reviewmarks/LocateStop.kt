package com.patrickreynolds.reviewmarks

fun stopOffsetIn(documentText: String, stop: StoredStop): Int {
    val anchorStart = locateMarkedText(documentText, stop.anchorText, stop.startOffset) ?: stop.startOffset
    return (anchorStart + stop.anchorText.length).coerceIn(0, documentText.length)
}
