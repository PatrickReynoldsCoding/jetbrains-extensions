package com.patrickreynolds.reviewmarks

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class MarkAndGoToNextAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.reviewEditor()?.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.reviewEditor() ?: return
        val project = editor.project ?: return
        val reviewMarks = project.service<ReviewMarksService>()

        val rangeToMark = rangeToMarkAt(editor, project)
        if (rangeToMark == null) {
            HintManager.getInstance().showInformationHint(editor, "Select code first, or put the caret inside a method or class")
            return
        }
        reviewMarks.markRangeAsReviewed(editor.document, rangeToMark)
        editor.selectionModel.removeSelection()
        editor.caretModel.moveToOffset(maxOf(rangeToMark.startOffset, rangeToMark.endOffset - 1))
        reviewMarks.goToNextRunToReview(editor)
    }
}
