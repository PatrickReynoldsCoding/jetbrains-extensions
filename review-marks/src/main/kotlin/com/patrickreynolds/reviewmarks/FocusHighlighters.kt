package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import java.awt.Font

private val FOCUS_BLOCK_ID = Key.create<String>("reviewmarks.focusBlockId")

private const val FOCUS_TOOLTIP = "Current focus. Remove it in the Current focus tab."

data class FocusMark(val highlighter: RangeHighlighter, val focusBlockId: String) {
    val range: TextRange get() = TextRange(highlighter.startOffset, highlighter.endOffset)
}

object FocusHighlighters {

    fun add(project: Project, document: Document, range: TextRange, focusBlockId: String) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true)
        val attributes = if (project.service<ReviewMarksService>().highlightsHidden) {
            TextAttributes()
        } else {
            TextAttributes(null, FOCUS_BACKGROUND, null, null, Font.PLAIN).also { it.errorStripeColor = FOCUS_ACCENT }
        }
        val highlighter = markupModel.addRangeHighlighter(
            range.startOffset,
            range.endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX + 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.putUserData(FOCUS_BLOCK_ID, focusBlockId)
        highlighter.errorStripeTooltip = FOCUS_TOOLTIP
    }

    fun inDocument(project: Project, document: Document): List<FocusMark> {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return emptyList()
        return markupModel.allHighlighters.mapNotNull { highlighter ->
            val focusBlockId = highlighter.getUserData(FOCUS_BLOCK_ID)
            if (focusBlockId != null && highlighter.isValid) FocusMark(highlighter, focusBlockId) else null
        }
    }

    fun removeAll(project: Project, document: Document) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return
        markupModel.allHighlighters
            .filter { it.getUserData(FOCUS_BLOCK_ID) != null }
            .forEach { markupModel.removeHighlighter(it) }
    }
}
