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

private val BACKGROUND_BY_STATE = mapOf(
    MarkState.REVIEWED to JBColor(Color(0xD8, 0xF0, 0xD8), Color(0x2A, 0x42, 0x2A)),
    MarkState.TO_REVIEW to JBColor(Color(0xD6, 0xE6, 0xFF), Color(0x25, 0x38, 0x4F)),
    MarkState.CHANGED to JBColor(Color(0xFF, 0xE3, 0xA8), Color(0x5C, 0x45, 0x18)),
)

private val STRIPE_BY_STATE = mapOf(
    MarkState.REVIEWED to REVIEWED_ACCENT,
    MarkState.TO_REVIEW to TO_REVIEW_ACCENT,
    MarkState.CHANGED to CHANGED_ACCENT,
)

private val TOOLTIP_BY_STATE = mapOf(
    MarkState.REVIEWED to "Marked as reviewed. A line you edit here turns amber.",
    MarkState.TO_REVIEW to "Marked to review. Mark it as reviewed when you have read it.",
    MarkState.CHANGED to "Changed since you reviewed it. Mark it as reviewed again when you have read it.",
)

data class ReviewMark(val highlighter: RangeHighlighter, val state: MarkState) {
    val range: TextRange get() = TextRange(highlighter.startOffset, highlighter.endOffset)
}

object ReviewedMarkHighlighters {

    fun add(project: Project, document: Document, range: TextRange, state: MarkState) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true)
        val attributes = TextAttributes(null, BACKGROUND_BY_STATE.getValue(state), null, null, Font.PLAIN)
        attributes.errorStripeColor = STRIPE_BY_STATE.getValue(state)
        val highlighter = markupModel.addRangeHighlighter(
            range.startOffset,
            range.endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.putUserData(MARK_STATE, state)
        highlighter.errorStripeTooltip = TOOLTIP_BY_STATE.getValue(state)
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
}
