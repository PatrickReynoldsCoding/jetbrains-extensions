package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager

fun rangeToMarkAt(editor: Editor, project: Project): TextRange? {
    val selection = editor.selectionModel
    if (selection.hasSelection()) return TextRange(selection.selectionStart, selection.selectionEnd)

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
    return findEnclosingDeclarationRange(psiFile, editor.document, editor.caretModel.offset)
}
