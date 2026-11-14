package com.patrickreynolds.reviewmarks

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

abstract class AddNoteOfKindAction(private val kind: NoteKind) : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.reviewEditor()?.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.reviewEditor() ?: return
        val project = editor.project ?: return
        val selection = editor.selectionModel
        val noteRange = project.service<ReviewMarksService>().noteRangeFor(editor.document, selection.selectionStart, selection.selectionEnd)
        if (noteRange == null) {
            HintManager.getInstance().showInformationHint(editor, "Select code, or put the caret on a line of code, to add a ${kind.label.lowercase()}")
            return
        }
        askForNewNote(project, editor.document, noteRange, kind)
    }
}

class AddNoteAction : AddNoteOfKindAction(NoteKind.NOTE)

class AddPullRequestCommentAction : AddNoteOfKindAction(NoteKind.PR_COMMENT)

class AddAgentNoteAction : AddNoteOfKindAction(NoteKind.AGENT_NOTE)
