package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun foldableRange(documentText: CharSequence, markedBlock: TextRange): TextRange? {
    val foldStart = firstWholeLineStart(documentText, markedBlock.startOffset) ?: return null
    val foldEnd = lastWholeLineEnd(documentText, markedBlock.endOffset) ?: return null
    if (foldEnd <= foldStart) return null

    val foldedText = documentText.subSequence(foldStart, foldEnd)
    val spansSeveralLines = foldedText.contains('\n')
    return if (spansSeveralLines) TextRange(foldStart, foldEnd) else null
}

private fun firstWholeLineStart(text: CharSequence, offset: Int): Int? {
    var lineContentStart = offset
    while (lineContentStart > 0 && isIndentation(text[lineContentStart - 1])) lineContentStart--
    val startsAtLineStart = lineContentStart == 0 || text[lineContentStart - 1] == '\n'
    if (startsAtLineStart) return lineContentStart

    val nextLineBreak = text.indexOf('\n', offset)
    return if (nextLineBreak < 0) null else nextLineBreak + 1
}

private fun lastWholeLineEnd(text: CharSequence, offset: Int): Int? {
    var lineContentEnd = offset
    while (lineContentEnd < text.length && isIndentation(text[lineContentEnd])) lineContentEnd++
    val endsAtLineEnd = lineContentEnd == text.length || text[lineContentEnd] == '\n'
    if (endsAtLineEnd) return lineContentEnd

    val lineBreakBeforeOffset = text.lastIndexOf('\n', offset - 1)
    return if (lineBreakBeforeOffset < 0) null else lineBreakBeforeOffset
}

private fun isIndentation(character: Char): Boolean = character == ' ' || character == '\t'
