package com.patrickreynolds.reviewmarks

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager

class MarkEnclosingDeclarationAsReviewedAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(CommonDataKeys.EDITOR)?.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val project = editor.project ?: return
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return

        val declarationRange = findEnclosingDeclarationRange(psiFile, editor.document, editor.caretModel.offset)
        if (declarationRange == null) {
            HintManager.getInstance().showInformationHint(editor, "No method or class around the caret")
            return
        }
        project.service<ReviewMarksService>().markRangeAsReviewed(editor.document, declarationRange)
    }
}
