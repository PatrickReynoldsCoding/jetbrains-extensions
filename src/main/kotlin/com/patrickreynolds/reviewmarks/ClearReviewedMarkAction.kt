package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service

class ClearReviewedMarkAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val project = editor?.project
        event.presentation.isEnabledAndVisible = project != null &&
            project.service<ReviewMarksService>().marksContaining(editor.document, editor.caretModel.offset).isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val project = editor.project ?: return
        project.service<ReviewMarksService>().clearMarksContaining(editor.document, editor.caretModel.offset)
    }
}
