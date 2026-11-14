package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class StopReviewingOnlyBranchChangesAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val scopeBaseRef = event.project?.service<ReviewMarksService>()?.scopeBaseRef
        event.presentation.isEnabledAndVisible = scopeBaseRef != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<ReviewMarksService>()?.stopReviewingOnlyChanges()
    }
}
