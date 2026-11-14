package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

fun trimmedToCode(documentText: String, range: TextRange): TextRange? {
    var startOffset = range.startOffset
    var endOffset = range.endOffset
    while (startOffset < endOffset && documentText[startOffset].isWhitespace()) startOffset++
    while (endOffset > startOffset && documentText[endOffset - 1].isWhitespace()) endOffset--
    return if (endOffset > startOffset) TextRange(startOffset, endOffset) else null
}
