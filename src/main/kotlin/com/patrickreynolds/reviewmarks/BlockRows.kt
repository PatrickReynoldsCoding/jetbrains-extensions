package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

private const val PREVIEW_LENGTH = 40

enum class BlockKind {
    REVIEWED,
    CHANGED,
    UNREAD,
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

fun reviewedBlockRows(documentText: String, reviewedRanges: List<TextRange>): List<BlockRow> {
    val codeLines = codeLinesOf(documentText)
    return joinAcrossWhitespace(documentText, reviewedRanges).mapNotNull { reviewedArea ->
        val codeLinesInArea = codeLines.filter { reviewedArea.contains(it.codeRange) }
        if (codeLinesInArea.isEmpty()) null else blockRowOf(BlockKind.REVIEWED, documentText, codeLinesInArea)
    }
}

fun blocksToReviewRows(
    documentText: String,
    reviewedRanges: List<TextRange>,
    changedRanges: List<TextRange>,
    outOfScopeRanges: List<TextRange>,
): List<BlockRow> {
    val lineNumbersToReview = linesNeedingReview(documentText, reviewedRanges, outOfScopeRanges).toSet()
    val codeLinesToReview = codeLinesOf(documentText).filter { it.lineNumber in lineNumbersToReview }
    val rows = splitIntoRuns(codeLinesToReview).map { run ->
        val runHasChangedLine = run.any { codeLine -> changedRanges.any { it.intersectsStrict(codeLine.codeRange) } }
        blockRowOf(if (runHasChangedLine) BlockKind.CHANGED else BlockKind.UNREAD, documentText, run)
    }
    return rows.sortedBy { if (it.kind == BlockKind.CHANGED) 0 else 1 }
}

private fun splitIntoRuns(codeLines: List<CodeLine>): List<List<CodeLine>> {
    val runs = mutableListOf<MutableList<CodeLine>>()
    codeLines.forEach { codeLine ->
        val currentRun = runs.lastOrNull()
        val continuesCurrentRun = currentRun != null && currentRun.last().lineNumber + 1 == codeLine.lineNumber
        if (continuesCurrentRun) currentRun!!.add(codeLine) else runs.add(mutableListOf(codeLine))
    }
    return runs
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
