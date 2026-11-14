package com.patrickreynolds.reviewmarks

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.codeInsight.hint.HintManager
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm

private val DOCUMENT_SHOWS_STORED_MARKS = Key.create<Boolean>("reviewmarks.documentShowsStoredMarks")

private const val PROGRESS_WIDGET_DELAY_MILLISECONDS = 300

@Service(Service.Level.PROJECT)
@State(name = "ReviewMarks", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ReviewMarksService(private val project: Project) : PersistentStateComponent<ReviewMarksState>, Disposable {

    private val storedMarksByFileUrl = mutableMapOf<String, List<StoredMark>>()

    private val progressWidgetUpdate = SingleAlarm(
        { refreshProgressWidget() },
        PROGRESS_WIDGET_DELAY_MILLISECONDS,
        this,
        Alarm.ThreadToUse.SWING_THREAD,
    )

    var focusMode: Boolean = false
        private set

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun beforeDocumentChange(event: DocumentEvent) {
                    markEditedLinesAsChanged(event)
                }

                override fun documentChanged(event: DocumentEvent) {
                    progressWidgetUpdate.request()
                }
            },
            this,
        )
    }

    fun markRangeAsReviewed(document: Document, range: TextRange) {
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)
        ReviewedMarkHighlighters.inDocument(project, document)
            .filter { it.range.intersectsStrict(range) }
            .forEach { overlappedMark ->
                ReviewedMarkHighlighters.remove(project, document, listOf(overlappedMark))
                withoutRange(overlappedMark.range, range).forEach { remainingRange ->
                    ReviewedMarkHighlighters.add(project, document, remainingRange, overlappedMark.state, focusMode)
                }
            }
        ReviewedMarkHighlighters.add(project, document, range, MarkState.REVIEWED, focusMode)
        progressWidgetUpdate.request()
    }

    fun marksContaining(document: Document, offset: Int): List<ReviewMark> =
        ReviewedMarkHighlighters.inDocument(project, document)
            .filter { offset >= it.range.startOffset && offset <= it.range.endOffset }

    fun clearMarksContaining(document: Document, offset: Int) {
        ReviewedMarkHighlighters.remove(project, document, marksContaining(document, offset))
        progressWidgetUpdate.request()
    }

    fun progressOf(document: Document): ReviewProgress {
        val marks = ReviewedMarkHighlighters.inDocument(project, document)
        return reviewProgress(
            documentText = document.text,
            reviewedRanges = rangesWithState(marks, MarkState.REVIEWED),
            changedRanges = rangesWithState(marks, MarkState.CHANGED),
        )
    }

    fun goToNextRunToReview(editor: Editor) {
        val document = editor.document
        val reviewedRanges = rangesWithState(ReviewedMarkHighlighters.inDocument(project, document), MarkState.REVIEWED)
        val currentLine = editor.caretModel.logicalPosition.line
        val nextRun = nextRunToReview(linesNeedingReview(document.text, reviewedRanges), currentLine)
        if (nextRun == null) {
            HintManager.getInstance().showInformationHint(editor, "Everything in this file is reviewed")
            return
        }
        editor.caretModel.moveToLogicalPosition(LogicalPosition(nextRun.firstLine, 0))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        if (nextRun.wrappedToTop) {
            HintManager.getInstance().showInformationHint(editor, "Nothing more below. Continued from the top")
        }
    }

    fun changeFocusMode(focusModeOn: Boolean) {
        focusMode = focusModeOn
        FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
            val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile) ?: return@forEach
            val marks = ReviewedMarkHighlighters.inDocument(project, openDocument)
            ReviewedMarkHighlighters.remove(project, openDocument, marks)
            marks.forEach { ReviewedMarkHighlighters.add(project, openDocument, it.range, it.state, focusMode) }
        }
    }

    fun showStoredMarks(document: Document, file: VirtualFile) {
        if (document.getUserData(DOCUMENT_SHOWS_STORED_MARKS) == true) return
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)

        val storedMarks = storedMarksByFileUrl[file.url] ?: return
        val documentText = document.text
        storedMarks.forEach { storedMark ->
            val startOffset = locateMarkedText(documentText, storedMark.markedText, storedMark.startOffset)
            if (startOffset != null) {
                val range = TextRange(startOffset, startOffset + storedMark.markedText.length)
                ReviewedMarkHighlighters.add(project, document, range, MarkState.valueOf(storedMark.state), focusMode)
            }
        }
        storeMarksOf(document, file)
        progressWidgetUpdate.request()
    }

    fun storeMarksOf(document: Document, file: VirtualFile) {
        if (document.getUserData(DOCUMENT_SHOWS_STORED_MARKS) != true) return

        val marksInDocument = ReviewedMarkHighlighters.inDocument(project, document).map { mark ->
            StoredMark(
                fileUrl = file.url,
                startOffset = mark.range.startOffset,
                markedText = document.getText(mark.range),
                state = mark.state.name,
            )
        }
        if (marksInDocument.isEmpty()) {
            storedMarksByFileUrl.remove(file.url)
        } else {
            storedMarksByFileUrl[file.url] = marksInDocument
        }
    }

    override fun getState(): ReviewMarksState {
        runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
                val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile)
                if (openDocument != null) storeMarksOf(openDocument, openFile)
            }
        }
        return ReviewMarksState(
            marks = storedMarksByFileUrl.values.flatten().toMutableList(),
            focusMode = focusMode,
        )
    }

    override fun loadState(state: ReviewMarksState) {
        storedMarksByFileUrl.clear()
        storedMarksByFileUrl.putAll(state.marks.groupBy { it.fileUrl })
        focusMode = state.focusMode
    }

    override fun dispose() = Unit

    private fun rangesWithState(marks: List<ReviewMark>, state: MarkState): List<TextRange> =
        marks.filter { it.state == state }.map { it.range }

    private fun markEditedLinesAsChanged(event: DocumentEvent) {
        val reviewedMarksChangedByEdit = ReviewedMarkHighlighters.inDocument(project, event.document)
            .filter { it.state == MarkState.REVIEWED }
            .filter { mark ->
                editChangesMarkedBlock(
                    editOffset = event.offset,
                    removedLength = event.oldLength,
                    markedBlockStart = mark.range.startOffset,
                    markedBlockEnd = mark.range.endOffset,
                )
            }
        reviewedMarksChangedByEdit.forEach { reviewedMark ->
            val split = splitMarkedBlockAroundEdit(
                documentTextBeforeEdit = event.document.immutableCharSequence,
                markedBlock = reviewedMark.range,
                editOffset = event.offset,
                removedLength = event.oldLength,
            )
            ReviewedMarkHighlighters.remove(project, event.document, listOf(reviewedMark))
            split.unchangedBefore?.let {
                ReviewedMarkHighlighters.add(project, event.document, it, MarkState.REVIEWED, focusMode)
            }
            ReviewedMarkHighlighters.add(project, event.document, split.changedLines, MarkState.CHANGED, focusMode)
            split.unchangedAfter?.let {
                ReviewedMarkHighlighters.add(project, event.document, it, MarkState.REVIEWED, focusMode)
            }
        }
    }

    private fun refreshProgressWidget() {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        (statusBar.getWidget(ReviewProgressWidget.WIDGET_ID) as? ReviewProgressWidget)?.refresh()
    }
}
