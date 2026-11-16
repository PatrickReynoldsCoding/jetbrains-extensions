package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

private val FUNCTION_CLASS_NAME_WORDS = listOf("Function", "Method")

private const val IMPORT_CLASS_NAME_WORD = "Import"

private val IMPORT_LINE_START = Regex("""^\s*(import\b|from\s+\S+\s+import\b|#\s*include\b|using\s+[\w.]+\s*;|require(_relative)?\b)""")

fun rangesToMarkForChangedLines(
    documentText: String,
    changedLines: List<LineRange>,
    functionRangeAtLine: (Int) -> TextRange?,
    isImportLine: (Int) -> Boolean,
    skipImports: Boolean,
): List<TextRange> {
    val changedLineNumbers = changedLines.flatMap { it.firstLine..it.lastLine }.distinct().sorted()
    val functionRangeByLine = changedLineNumbers.associateWith { functionRangeAtLine(it) }
    val functionRanges = functionRangeByLine.values.filterNotNull().distinct()
    val linesOutsideFunctions = changedLineNumbers
        .filter { functionRangeByLine.getValue(it) == null }
        .filterNot { skipImports && isImportLine(it) }
    val looseRanges = runsOfNeighbouringLines(linesOutsideFunctions).mapNotNull { textRangeOfLines(documentText, it) }
    return (functionRanges + looseRanges).sortedBy { it.startOffset }
}

private fun runsOfNeighbouringLines(sortedLineNumbers: List<Int>): List<LineRange> =
    sortedLineNumbers.fold(emptyList()) { runsSoFar, lineNumber ->
        val lastRun = runsSoFar.lastOrNull()
        if (lastRun != null && lastRun.lastLine == lineNumber - 1) {
            runsSoFar.dropLast(1) + LineRange(lastRun.firstLine, lineNumber)
        } else {
            runsSoFar + LineRange(lineNumber, lineNumber)
        }
    }

fun outermostFunctionRangeAtLine(psiFile: PsiFile, document: Document, lineNumber: Int): TextRange? {
    val firstCodeOffset = firstCodeOffsetOfLine(document, lineNumber) ?: return null
    val outermostFunction = ancestorsOf(psiFile.findElementAt(firstCodeOffset)).lastOrNull { isFunction(it) } ?: return null
    return withLeadingIndentation(document.immutableCharSequence, outermostFunction.textRange)
}

fun isImportLineOf(psiFile: PsiFile, document: Document, lineNumber: Int): Boolean {
    val firstCodeOffset = firstCodeOffsetOfLine(document, lineNumber) ?: return false
    val insideImportElement = ancestorsOf(psiFile.findElementAt(firstCodeOffset)).any { IMPORT_CLASS_NAME_WORD in it.javaClass.simpleName }
    val lineText = document.getText(TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)))
    return insideImportElement || IMPORT_LINE_START.containsMatchIn(lineText)
}

private fun firstCodeOffsetOfLine(document: Document, lineNumber: Int): Int? {
    if (lineNumber < 0 || lineNumber >= document.lineCount) return null
    val lineRange = TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber))
    val lineText = document.getText(lineRange)
    val indentation = lineText.length - lineText.trimStart().length
    return if (lineText.isBlank()) null else lineRange.startOffset + indentation
}

private fun ancestorsOf(element: PsiElement?): List<PsiElement> =
    generateSequence(element) { it.parent }.takeWhile { it !is PsiFile }.toList()

private fun isFunction(element: PsiElement): Boolean =
    element is PsiNameIdentifierOwner && FUNCTION_CLASS_NAME_WORDS.any { it in element.javaClass.simpleName }
