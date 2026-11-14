package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

fun askForNewNote(project: Project, document: Document, noteRange: TextRange, kind: NoteKind) {
    val floatingByDefault = service<ReviewMarksSettings>().state.notesFloatByDefault
    val dialog = NoteDialog(project, NoteDetails("", floatingByDefault, kind), linesText(document, noteRange))
    if (!dialog.showAndGet()) return
    val details = dialog.noteDetails()
    if (details.text.isEmpty()) return
    project.service<ReviewMarksService>().addNote(document, noteRange, details)
}

fun askToEditNote(project: Project, document: Document, note: ReviewNote) {
    val dialog = NoteDialog(project, note.details, linesText(document, note.range))
    if (!dialog.showAndGet()) return
    project.service<ReviewMarksService>().editNote(document, note, dialog.noteDetails())
}

private fun linesText(document: Document, range: TextRange): String {
    val firstLine = document.getLineNumber(range.startOffset) + 1
    val lastLine = document.getLineNumber(range.endOffset) + 1
    return if (firstLine == lastLine) "On line $firstLine" else "On lines $firstLine–$lastLine"
}
