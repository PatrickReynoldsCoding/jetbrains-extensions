package com.patrickreynolds.reviewmarks

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange

private val IS_REVIEWED_FOLD = Key.create<Boolean>("reviewmarks.isReviewedFold")

object ReviewedFolds {

    fun show(project: Project, document: Document, reviewedRanges: List<TextRange>) {
        val rangesToFold = reviewedRanges
            .mapNotNull { foldableRange(document.immutableCharSequence, it) }
            .sortedBy { it.startOffset }

        EditorFactory.getInstance().getEditors(document, project).forEach { editor ->
            val foldedRanges = reviewedFoldRegions(editor)
                .map { TextRange(it.startOffset, it.endOffset) }
                .sortedBy { it.startOffset }
            if (foldedRanges != rangesToFold) replaceFolds(editor, rangesToFold)
        }
    }

    private fun replaceFolds(editor: Editor, rangesToFold: List<TextRange>) {
        val foldingModel = editor.foldingModel
        foldingModel.runBatchFoldingOperation {
            reviewedFoldRegions(editor).forEach { foldingModel.removeFoldRegion(it) }
            rangesToFold.forEach { range ->
                val region = foldingModel.addFoldRegion(range.startOffset, range.endOffset, placeholderFor(editor.document, range))
                region?.putUserData(IS_REVIEWED_FOLD, true)
                region?.isExpanded = false
            }
        }
    }

    private fun reviewedFoldRegions(editor: Editor): List<FoldRegion> =
        editor.foldingModel.allFoldRegions.filter { it.getUserData(IS_REVIEWED_FOLD) == true }

    private fun placeholderFor(document: Document, range: TextRange): String {
        val lineCount = document.getLineNumber(range.endOffset) - document.getLineNumber(range.startOffset) + 1
        return "✓ $lineCount reviewed lines"
    }
}
