package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun withoutRange(markedBlock: TextRange, removedRange: TextRange): List<TextRange> {
    val partBeforeRemovedRange = rangeOrNull(
        markedBlock.startOffset,
        minOf(markedBlock.endOffset, removedRange.startOffset),
    )
    val partAfterRemovedRange = rangeOrNull(
        maxOf(markedBlock.startOffset, removedRange.endOffset),
        markedBlock.endOffset,
    )
    return listOfNotNull(partBeforeRemovedRange, partAfterRemovedRange)
}

fun withoutRanges(markedBlock: TextRange, removedRanges: List<TextRange>): List<TextRange> =
    removedRanges.fold(listOf(markedBlock)) { remainingParts, removedRange ->
        remainingParts.flatMap { withoutRange(it, removedRange) }
    }

private fun rangeOrNull(startOffset: Int, endOffset: Int): TextRange? =
    if (endOffset > startOffset) TextRange(startOffset, endOffset) else null
