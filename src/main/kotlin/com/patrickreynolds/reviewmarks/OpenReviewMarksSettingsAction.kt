package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

class OpenReviewMarksSettingsAction : DumbAwareAction("Review Marks Settings", "Open the Review Marks settings", AllIcons.General.GearPlain) {

    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.project, ReviewMarksConfigurable::class.java)
    }
}
