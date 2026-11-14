package com.patrickreynolds.reviewmarks

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service

class AddOrEditNoteAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(CommonDataKeys.EDITOR)?.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val project = editor.project ?: return
        val offset = editor.caretModel.offset
        if (!project.service<ReviewMarksService>().canAddNoteAt(editor.document, offset)) {
            HintManager.getInstance().showInformationHint(editor, "Put the caret on a line of code to add a note")
            return
        }
        askForNoteAt(project, editor.document, offset)
    }
}
