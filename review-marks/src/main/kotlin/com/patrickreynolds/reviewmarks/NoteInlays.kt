package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D

private const val HORIZONTAL_PADDING = 8
private const val CORNER_ARC = 6
private const val MOST_NOTE_TEXT_LENGTH = 200

private val NOTE_BACKGROUND = JBColor(Color(0xFF, 0xF4, 0xC2), Color(0x4A, 0x44, 0x20))
private val NOTE_TEXT_COLOR = JBColor(Color(0x5A, 0x4A, 0x00), Color(0xF0, 0xE6, 0xB0))

fun floatingNoteText(noteText: String): String {
    val lines = noteText.lines()
    val firstLine = lines.first().take(MOST_NOTE_TEXT_LENGTH)
    val wasShortened = lines.size > 1 || lines.first().length > MOST_NOTE_TEXT_LENGTH
    return "💬 " + if (wasShortened) "$firstLine…" else firstLine
}

private class NoteInlayRenderer(val shownText: String) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int =
        inlay.editor.contentComponent.getFontMetrics(noteFont(inlay)).stringWidth(shownText) + 2 * HORIZONTAL_PADDING

    override fun paint(inlay: Inlay<*>, graphics2D: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = NOTE_BACKGROUND
        graphics2D.fillRoundRect(
            targetRegion.x.toInt(),
            targetRegion.y.toInt() + 1,
            targetRegion.width.toInt(),
            targetRegion.height.toInt() - 2,
            CORNER_ARC,
            CORNER_ARC,
        )
        graphics2D.font = noteFont(inlay)
        graphics2D.color = NOTE_TEXT_COLOR
        val fontMetrics = graphics2D.fontMetrics
        val baseline = targetRegion.y.toInt() + (targetRegion.height.toInt() + fontMetrics.ascent - fontMetrics.descent) / 2
        graphics2D.drawString(shownText, targetRegion.x.toInt() + HORIZONTAL_PADDING, baseline)
    }

    private fun noteFont(inlay: Inlay<*>): Font =
        inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN).deriveFont(Font.ITALIC)
}

object NoteInlays {

    fun show(project: Project, document: Document, floatingNotes: List<ReviewNote>) {
        val inlaysToShow = floatingNotes
            .map { note -> document.getLineStartOffset(document.getLineNumber(note.range.startOffset)) to floatingNoteText(note.text) }
            .sortedBy { it.first }

        EditorFactory.getInstance().getEditors(document, project).forEach { editor ->
            val shownInlays = editor.inlayModel.getBlockElementsInRange(0, document.textLength, NoteInlayRenderer::class.java)
            val shownOffsetsAndTexts = shownInlays
                .map { it.offset to it.renderer.shownText }
                .sortedBy { it.first }
            if (shownOffsetsAndTexts != inlaysToShow) {
                shownInlays.forEach { Disposer.dispose(it) }
                inlaysToShow.forEach { (offset, shownText) ->
                    editor.inlayModel.addBlockElement(offset, true, true, 0, NoteInlayRenderer(shownText))
                }
            }
        }
    }
}
