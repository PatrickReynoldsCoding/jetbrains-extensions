package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import java.awt.Font

private val IS_OUT_OF_SCOPE = Key.create<Boolean>("reviewmarks.isOutOfScope")

object OutOfScopeHighlighters {

    fun replace(project: Project, document: Document, ranges: List<TextRange>) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, true)
        markupModel.allHighlighters
            .filter { it.getUserData(IS_OUT_OF_SCOPE) == true }
            .forEach { markupModel.removeHighlighter(it) }

        val fadedTextAttributes = TextAttributes(FADED_TEXT_COLOR, null, null, null, Font.PLAIN)
        ranges.forEach { range ->
            val highlighter = markupModel.addRangeHighlighter(
                range.startOffset,
                range.endOffset,
                HighlighterLayer.ADDITIONAL_SYNTAX - 1,
                fadedTextAttributes,
                HighlighterTargetArea.EXACT_RANGE,
            )
            highlighter.putUserData(IS_OUT_OF_SCOPE, true)
        }
    }

    fun rangesIn(project: Project, document: Document): List<TextRange> {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return emptyList()
        return markupModel.allHighlighters
            .filter { it.getUserData(IS_OUT_OF_SCOPE) == true && it.isValid }
            .map { TextRange(it.startOffset, it.endOffset) }
    }
}
