package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

data class CodeLine(val lineNumber: Int, val codeRange: TextRange)

fun codeLinesOf(documentText: String): List<CodeLine> {
    val codeLines = mutableListOf<CodeLine>()
    var lineStartOffset = 0
    documentText.split('\n').forEachIndexed { lineNumber, lineText ->
        val firstCodeCharacter = lineText.indexOfFirst { !it.isWhitespace() }
        if (firstCodeCharacter >= 0) {
            val lastCodeCharacter = lineText.indexOfLast { !it.isWhitespace() }
            codeLines += CodeLine(
                lineNumber = lineNumber,
                codeRange = TextRange(lineStartOffset + firstCodeCharacter, lineStartOffset + lastCodeCharacter + 1),
            )
        }
        lineStartOffset += lineText.length + 1
    }
    return codeLines
}
