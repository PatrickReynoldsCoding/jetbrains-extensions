package com.patrickreynolds.reviewmarks

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager

class AddToFocusAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.reviewEditor()?.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.reviewEditor() ?: return
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return

        val rangeToMark = rangeToMarkAt(editor, project)?.let { trimmedToCode(editor.document.text, it) }
        if (rangeToMark == null) {
            HintManager.getInstance().showInformationHint(editor, "Select code first, or put the caret inside a method or class")
            return
        }
        val addedCount = project.service<ReviewMarksService>().addToFocus(listOf(file.url to rangeToMark))
        if (addedCount == 0) HintManager.getInstance().showInformationHint(editor, "Already in Current focus")
        editor.selectionModel.removeSelection()
    }
}
