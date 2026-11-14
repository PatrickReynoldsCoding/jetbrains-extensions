package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

data class MarkedBlockSplit(
    val unchangedBefore: TextRange?,
    val changedLines: TextRange,
    val unchangedAfter: TextRange?,
)

fun splitMarkedBlockAroundEdit(
    documentTextBeforeEdit: CharSequence,
    markedBlock: TextRange,
    editOffset: Int,
    removedLength: Int,
): MarkedBlockSplit {
    val lastTouchedOffset = if (removedLength == 0) editOffset else editOffset + removedLength - 1
    val firstChangedLineStart = lineStartAt(documentTextBeforeEdit, editOffset)
    val lastChangedLineEnd = lineEndAt(documentTextBeforeEdit, lastTouchedOffset)

    val changedLines = TextRange(
        maxOf(markedBlock.startOffset, firstChangedLineStart),
        minOf(markedBlock.endOffset, lastChangedLineEnd),
    )
    return MarkedBlockSplit(
        unchangedBefore = rangeOrNull(markedBlock.startOffset, changedLines.startOffset),
        changedLines = changedLines,
        unchangedAfter = rangeOrNull(changedLines.endOffset, markedBlock.endOffset),
    )
}

private fun lineStartAt(text: CharSequence, offset: Int): Int = text.lastIndexOf('\n', offset - 1) + 1

private fun lineEndAt(text: CharSequence, offset: Int): Int {
    val lineBreakOffset = text.indexOf('\n', offset)
    return if (lineBreakOffset < 0) text.length else lineBreakOffset
}

private fun rangeOrNull(startOffset: Int, endOffset: Int): TextRange? =
    if (endOffset > startOffset) TextRange(startOffset, endOffset) else null
