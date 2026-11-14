package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import javax.swing.Icon

private val NOTE_TEXT = Key.create<String>("reviewmarks.noteText")

data class ReviewNote(val highlighter: RangeHighlighter) {
    val text: String get() = highlighter.getUserData(NOTE_TEXT).orEmpty()
    val range: TextRange get() = TextRange(highlighter.startOffset, highlighter.endOffset)
}

object ReviewNoteHighlighters {

    fun add(project: Project, document: Document, codeRange: TextRange, noteText: String) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true)
        val highlighter = markupModel.addRangeHighlighter(
            codeRange.startOffset,
            codeRange.endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            TextAttributes(),
            HighlighterTargetArea.LINES_IN_RANGE,
        )
        highlighter.putUserData(NOTE_TEXT, noteText)
        highlighter.gutterIconRenderer = NoteGutterIcon(project, document, highlighter)
    }

    fun inDocument(project: Project, document: Document): List<ReviewNote> {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return emptyList()
        return markupModel.allHighlighters
            .filter { it.getUserData(NOTE_TEXT) != null && it.isValid }
            .map { ReviewNote(it) }
    }

    fun changeText(note: ReviewNote, newText: String) {
        note.highlighter.putUserData(NOTE_TEXT, newText)
    }

    fun remove(project: Project, document: Document, note: ReviewNote) {
        DocumentMarkupModel.forDocument(document, project, false)?.removeHighlighter(note.highlighter)
    }
}

private class NoteGutterIcon(
    private val project: Project,
    private val document: Document,
    private val highlighter: RangeHighlighter,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = AllIcons.General.Balloon

    override fun getTooltipText(): String? = highlighter.getUserData(NOTE_TEXT)

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            askForNoteAt(project, document, highlighter.startOffset)
        }
    }

    override fun equals(other: Any?): Boolean = other is NoteGutterIcon && other.highlighter === highlighter

    override fun hashCode(): Int = System.identityHashCode(highlighter)
}
