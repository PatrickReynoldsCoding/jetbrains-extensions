package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

fun askForNoteAt(project: Project, document: Document, offset: Int) {
    val reviewMarks = project.service<ReviewMarksService>()
    val noteText = Messages.showInputDialog(
        project,
        "Note for this line. Leave it empty to delete the note.",
        "Review Note",
        null,
        reviewMarks.noteTextAt(document, offset).orEmpty(),
        null,
    ) ?: return
    reviewMarks.setNoteAt(document, offset, noteText.trim())
}
