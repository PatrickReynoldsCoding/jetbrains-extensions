package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D

private const val HORIZONTAL_PADDING = 8
private const val CORNER_ARC = 6
private const val MOST_NOTE_TEXT_LENGTH = 200

private const val BAR_WIDTH = 3

fun floatingNoteText(kind: NoteKind, noteText: String): String {
    val lines = noteText.lines()
    val firstLine = lines.first().take(MOST_NOTE_TEXT_LENGTH)
    val wasShortened = lines.size > 1 || lines.first().length > MOST_NOTE_TEXT_LENGTH
    return "${kind.label}: " + if (wasShortened) "$firstLine…" else firstLine
}

private data class FloatingNote(val offset: Int, val kind: NoteKind, val shownText: String)

private class NoteInlayRenderer(val kind: NoteKind, val shownText: String) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int =
        inlay.editor.contentComponent.getFontMetrics(noteFont(inlay)).stringWidth(shownText) + 2 * HORIZONTAL_PADDING

    override fun paint(inlay: Inlay<*>, graphics2D: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = NOTE_BACKGROUND_BY_KIND.getValue(kind)
        graphics2D.fillRoundRect(
            targetRegion.x.toInt(),
            targetRegion.y.toInt() + 1,
            targetRegion.width.toInt(),
            targetRegion.height.toInt() - 2,
            CORNER_ARC,
            CORNER_ARC,
        )
        graphics2D.color = NOTE_ACCENT_BY_KIND.getValue(kind)
        graphics2D.fillRect(targetRegion.x.toInt(), targetRegion.y.toInt() + 1, BAR_WIDTH, targetRegion.height.toInt() - 2)
        graphics2D.font = noteFont(inlay)
        graphics2D.color = NOTE_TEXT_BY_KIND.getValue(kind)
        val fontMetrics = graphics2D.fontMetrics
        val baseline = targetRegion.y.toInt() + (targetRegion.height.toInt() + fontMetrics.ascent - fontMetrics.descent) / 2
        graphics2D.drawString(shownText, targetRegion.x.toInt() + HORIZONTAL_PADDING, baseline)
    }

    private fun noteFont(inlay: Inlay<*>): Font =
        inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN).deriveFont(Font.ITALIC)
}

object NoteInlays {

    fun show(project: Project, document: Document, floatingNotes: List<ReviewNote>) {
        val notesToShow = floatingNotes.map { note ->
            FloatingNote(
                offset = document.getLineStartOffset(document.getLineNumber(note.range.startOffset)),
                kind = note.details.kind,
                shownText = floatingNoteText(note.details.kind, note.text),
            )
        }

        EditorFactory.getInstance().getEditors(document, project).forEach { editor ->
            val shownInlays = editor.inlayModel.getBlockElementsInRange(0, document.textLength, NoteInlayRenderer::class.java)
            val shownNotes = shownInlays.map { FloatingNote(it.offset, it.renderer.kind, it.renderer.shownText) }
            if (shownNotes.sortedBy { it.offset } != notesToShow) {
                shownInlays.forEach { Disposer.dispose(it) }
                notesToShow.forEachIndexed { orderOnPage, note ->
                    editor.inlayModel.addBlockElement(note.offset, true, true, -orderOnPage, NoteInlayRenderer(note.kind, note.shownText))
                }
            }
        }
    }
}
