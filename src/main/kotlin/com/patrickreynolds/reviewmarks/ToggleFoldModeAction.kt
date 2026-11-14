package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service

class ToggleFoldModeAction : ToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(event: AnActionEvent): Boolean =
        event.project?.service<ReviewMarksService>()?.foldMode ?: false

    override fun setSelected(event: AnActionEvent, foldModeOn: Boolean) {
        event.project?.service<ReviewMarksService>()?.changeFoldMode(foldModeOn)
    }
}
