package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange

class MarkSelectionAsReviewedAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor?.project != null && editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val project = editor.project ?: return
        val selection = editor.selectionModel
        project.service<ReviewMarksService>().markRangeAsReviewed(
            document = editor.document,
            range = TextRange(selection.selectionStart, selection.selectionEnd),
        )
        selection.removeSelection()
    }
}
