package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class UndoLastClearAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val undoDescription = event.project?.service<ReviewMarksService>()?.undoDescription
        event.presentation.isEnabledAndVisible = undoDescription != null
        if (undoDescription != null) event.presentation.text = "Undo: $undoDescription"
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<ReviewMarksService>()?.undoLastClear()
    }
}
