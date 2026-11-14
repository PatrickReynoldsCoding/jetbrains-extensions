package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

fun findEnclosingDeclarationRange(psiFile: PsiFile, document: Document, caretOffset: Int): TextRange? {
    var element: PsiElement? = psiFile.findElementAt(caretOffset)
    while (element != null && element !is PsiFile) {
        if (element is PsiNameIdentifierOwner && spansSeveralLines(document, element.textRange)) {
            return withLeadingIndentation(document.immutableCharSequence, element.textRange)
        }
        element = element.parent
    }
    return null
}

private fun spansSeveralLines(document: Document, range: TextRange): Boolean =
    document.getLineNumber(range.startOffset) < document.getLineNumber(range.endOffset)

private fun withLeadingIndentation(documentText: CharSequence, range: TextRange): TextRange {
    var startOffset = range.startOffset
    while (startOffset > 0 && documentText[startOffset - 1].let { it == ' ' || it == '\t' }) {
        startOffset--
    }
    val reachedStartOfLine = startOffset == 0 || documentText[startOffset - 1] == '\n'
    return if (reachedStartOfLine) TextRange(startOffset, range.endOffset) else range
}
