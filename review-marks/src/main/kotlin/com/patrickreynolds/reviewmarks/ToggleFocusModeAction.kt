package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service

class ToggleFocusModeAction : ToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(event: AnActionEvent): Boolean =
        event.project?.service<ReviewMarksService>()?.focusMode ?: false

    override fun setSelected(event: AnActionEvent, focusModeOn: Boolean) {
        event.project?.service<ReviewMarksService>()?.changeFocusMode(focusModeOn)
    }
}
