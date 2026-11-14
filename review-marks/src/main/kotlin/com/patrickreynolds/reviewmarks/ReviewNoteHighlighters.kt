package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Rectangle
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon

private val NOTE_DETAILS = Key.create<NoteDetails>("reviewmarks.noteDetails")
private val NOTE_ID = Key.create<String>("reviewmarks.noteId")
private val NOTE_ADDED_ORDER = Key.create<Long>("reviewmarks.noteAddedOrder")
private val notesAddedSoFar = AtomicLong()

private const val NOTE_BAR_WIDTH = 2
private const val NOTE_BAR_GAP = 1
private const val NOTE_ID_LENGTH = 8

data class NoteDetails(
    val text: String,
    val floating: Boolean,
    val kind: NoteKind,
)

data class ReviewNote(val highlighter: RangeHighlighter) {
    val details: NoteDetails get() = highlighter.getUserData(NOTE_DETAILS) ?: NoteDetails("", floating = false, kind = NoteKind.NOTE)
    val text: String get() = details.text
    val id: String get() = highlighter.getUserData(NOTE_ID).orEmpty()
    val range: TextRange get() = TextRange(highlighter.startOffset, highlighter.endOffset)
}

val ICON_BY_NOTE_KIND: Map<NoteKind, Icon> = NoteKind.entries.associateWith { kind ->
    IconUtil.colorize(AllIcons.General.Balloon, NOTE_ACCENT_BY_KIND.getValue(kind))
}

object ReviewNoteHighlighters {

    fun add(project: Project, document: Document, codeRange: TextRange, details: NoteDetails, noteId: String) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true)
        val attributes = TextAttributes()
        attributes.errorStripeColor = NOTE_ACCENT_BY_KIND.getValue(details.kind)
        val highlighter = markupModel.addRangeHighlighter(
            codeRange.startOffset,
            codeRange.endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            attributes,
            HighlighterTargetArea.LINES_IN_RANGE,
        )
        highlighter.putUserData(NOTE_DETAILS, details)
        highlighter.putUserData(NOTE_ID, noteId)
        highlighter.putUserData(NOTE_ADDED_ORDER, notesAddedSoFar.incrementAndGet())
        highlighter.gutterIconRenderer = NoteGutterIcon(project, document, highlighter)
        highlighter.lineMarkerRenderer = NoteBar(highlighter)
    }

    fun inDocument(project: Project, document: Document): List<ReviewNote> {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return emptyList()
        return markupModel.allHighlighters
            .filter { it.getUserData(NOTE_DETAILS) != null && it.isValid }
            .sortedWith(compareBy({ it.startOffset }, { it.getUserData(NOTE_ADDED_ORDER) ?: 0L }))
            .map { ReviewNote(it) }
    }

    fun changeDetails(note: ReviewNote, newDetails: NoteDetails) {
        note.highlighter.putUserData(NOTE_DETAILS, newDetails)
        note.highlighter.textAttributes?.errorStripeColor = NOTE_ACCENT_BY_KIND.getValue(newDetails.kind)
    }

    fun remove(project: Project, document: Document, note: ReviewNote) {
        DocumentMarkupModel.forDocument(document, project, false)?.removeHighlighter(note.highlighter)
    }
}

private class NoteBar(private val highlighter: RangeHighlighter) : LineMarkerRenderer {
    override fun paint(editor: Editor, graphics: Graphics, area: Rectangle) {
        val kind = highlighter.getUserData(NOTE_DETAILS)?.kind ?: return
        val project = editor.project ?: return
        val notesSharingLines = notesSharingLinesWith(project, editor.document)
        val barSlot = notesSharingLines.indexOfFirst { it.highlighter === highlighter }.coerceAtLeast(0)
        graphics.color = NOTE_ACCENT_BY_KIND.getValue(kind)
        graphics.fillRect(area.x + barSlot * JBUI.scale(NOTE_BAR_WIDTH + NOTE_BAR_GAP), area.y, JBUI.scale(NOTE_BAR_WIDTH), area.height)
    }

    private fun notesSharingLinesWith(project: Project, document: Document): List<ReviewNote> {
        val firstLine = document.getLineNumber(highlighter.startOffset)
        val lastLine = document.getLineNumber(highlighter.endOffset)
        return ReviewNoteHighlighters.inDocument(project, document).filter { note ->
            document.getLineNumber(note.range.startOffset) <= lastLine && document.getLineNumber(note.range.endOffset) >= firstLine
        }
    }
}

private class NoteGutterIcon(
    private val project: Project,
    private val document: Document,
    private val highlighter: RangeHighlighter,
) : GutterIconRenderer() {

    private fun details(): NoteDetails? = highlighter.getUserData(NOTE_DETAILS)

    override fun getIcon(): Icon = ICON_BY_NOTE_KIND.getValue(details()?.kind ?: NoteKind.NOTE)

    override fun getTooltipText(): String? = details()?.let { "${it.kind.label}: ${it.text}" }

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            askToEditNote(project, document, ReviewNote(highlighter))
        }
    }

    override fun equals(other: Any?): Boolean = other is NoteGutterIcon && other.highlighter === highlighter

    override fun hashCode(): Int = System.identityHashCode(highlighter)
}

fun newNoteId(): String = UUID.randomUUID().toString().take(NOTE_ID_LENGTH)
