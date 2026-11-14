package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

private val MARK_STATE = Key.create<MarkState>("reviewmarks.markState")

private val REVIEWED_BACKGROUND = JBColor(Color(0xD8, 0xF0, 0xD8), Color(0x2A, 0x42, 0x2A))
private val REVIEWED_STRIPE = JBColor(Color(0x5C, 0xB8, 0x5C), Color(0x4A, 0x8F, 0x4A))
internal val FADED_TEXT_COLOR = JBColor(Color(0x9A, 0x9A, 0x9A), Color(0x70, 0x70, 0x70))

private val CHANGED_BACKGROUND = JBColor(Color(0xFF, 0xE3, 0xA8), Color(0x5C, 0x45, 0x18))
private val CHANGED_STRIPE = JBColor(Color(0xE0, 0xA0, 0x30), Color(0xC0, 0x88, 0x20))

private const val REVIEWED_TOOLTIP = "Marked as reviewed. A line you edit here turns amber."
private const val CHANGED_TOOLTIP = "Changed since you reviewed it. Mark it as reviewed again when you have read it."

data class ReviewMark(val highlighter: RangeHighlighter, val state: MarkState) {
    val range: TextRange get() = TextRange(highlighter.startOffset, highlighter.endOffset)
}

object ReviewedMarkHighlighters {

    fun add(project: Project, document: Document, range: TextRange, state: MarkState, focusMode: Boolean) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true)
        val highlighter = markupModel.addRangeHighlighter(
            range.startOffset,
            range.endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            textAttributesFor(state, focusMode),
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.putUserData(MARK_STATE, state)
        highlighter.errorStripeTooltip = if (state == MarkState.REVIEWED) REVIEWED_TOOLTIP else CHANGED_TOOLTIP
        if (state == MarkState.CHANGED) {
            highlighter.isGreedyToLeft = true
            highlighter.isGreedyToRight = true
        }
    }

    fun inDocument(project: Project, document: Document): List<ReviewMark> {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return emptyList()
        return markupModel.allHighlighters.mapNotNull { highlighter ->
            val state = highlighter.getUserData(MARK_STATE)
            if (state != null && highlighter.isValid) ReviewMark(highlighter, state) else null
        }
    }

    fun remove(project: Project, document: Document, marks: List<ReviewMark>) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return
        marks.forEach { markupModel.removeHighlighter(it.highlighter) }
    }

    private fun textAttributesFor(state: MarkState, focusMode: Boolean): TextAttributes {
        val attributes = when {
            state == MarkState.CHANGED -> TextAttributes(null, CHANGED_BACKGROUND, null, null, Font.PLAIN)
            focusMode -> TextAttributes(FADED_TEXT_COLOR, null, null, null, Font.PLAIN)
            else -> TextAttributes(null, REVIEWED_BACKGROUND, null, null, Font.PLAIN)
        }
        attributes.errorStripeColor = if (state == MarkState.CHANGED) CHANGED_STRIPE else REVIEWED_STRIPE
        return attributes
    }
}
