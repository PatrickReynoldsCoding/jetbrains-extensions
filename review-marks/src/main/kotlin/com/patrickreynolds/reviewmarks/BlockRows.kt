package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

private const val PREVIEW_LENGTH = 40

enum class BlockKind {
    REVIEWED,
    CHANGED,
    TO_REVIEW,
}

data class BlockRow(
    val kind: BlockKind,
    val firstLine: Int,
    val lastLine: Int,
    val codeLineCount: Int,
    val preview: String,
    val startOffset: Int,
    val endOffset: Int,
)

fun blockRows(kind: BlockKind, documentText: String, markedRanges: List<TextRange>): List<BlockRow> {
    val codeLines = codeLinesOf(documentText)
    return joinAcrossWhitespace(documentText, markedRanges).mapNotNull { markedArea ->
        val codeLinesInArea = codeLines.filter { markedArea.contains(it.codeRange) }
        if (codeLinesInArea.isEmpty()) null else blockRowOf(kind, documentText, codeLinesInArea)
    }
}

fun toReviewBlockRows(documentText: String, toReviewRanges: List<TextRange>, changedRanges: List<TextRange>): List<BlockRow> {
    val changedRows = blockRows(BlockKind.CHANGED, documentText, changedRanges)
    val toReviewRows = blockRows(BlockKind.TO_REVIEW, documentText, toReviewRanges)
    return changedRows.sortedBy { it.firstLine } + toReviewRows.sortedBy { it.firstLine }
}

private fun blockRowOf(kind: BlockKind, documentText: String, codeLinesInBlock: List<CodeLine>): BlockRow {
    val firstCodeLine = codeLinesInBlock.first()
    val lastCodeLine = codeLinesInBlock.last()
    val firstLineText = documentText.substring(firstCodeLine.codeRange.startOffset, firstCodeLine.codeRange.endOffset)
    val preview = if (firstLineText.length > PREVIEW_LENGTH) firstLineText.take(PREVIEW_LENGTH) + "…" else firstLineText
    return BlockRow(
        kind = kind,
        firstLine = firstCodeLine.lineNumber + 1,
        lastLine = lastCodeLine.lineNumber + 1,
        codeLineCount = codeLinesInBlock.size,
        preview = preview,
        startOffset = firstCodeLine.codeRange.startOffset,
        endOffset = lastCodeLine.codeRange.endOffset,
    )
}
