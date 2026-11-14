package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

fun askForNoteAt(project: Project, document: Document, offset: Int) {
    val reviewMarks = project.service<ReviewMarksService>()
    val dialog = NoteDialog(project, reviewMarks.noteDetailsAt(document, offset), reviewMarks.pullRequest != null)
    if (!dialog.showAndGet()) return
    reviewMarks.setNoteAt(document, offset, dialog.noteDetails())
}
