package com.patrickreynolds.reviewmarks

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.codeInsight.hint.HintManager
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import java.time.LocalDate

private val DOCUMENT_SHOWS_STORED_MARKS = Key.create<Boolean>("reviewmarks.documentShowsStoredMarks")

private const val PROGRESS_WIDGET_DELAY_MILLISECONDS = 300

@Service(Service.Level.PROJECT)
@State(name = "ReviewMarks", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ReviewMarksService(private val project: Project) : PersistentStateComponent<ReviewMarksState>, Disposable {

    private val storedMarksByFileUrl = mutableMapOf<String, List<StoredMark>>()

    private val storedNotesByFileUrl = mutableMapOf<String, List<StoredNote>>()

    private val afterChangesUpdate = SingleAlarm(
        { afterChanges() },
        PROGRESS_WIDGET_DELAY_MILLISECONDS,
        this,
        Alarm.ThreadToUse.SWING_THREAD,
    )

    var focusMode: Boolean = false
        private set

    var foldMode: Boolean = false
        private set

    private var lastStop: StoredStop? = null

    private var scope: StoredScope? = null

    private var reviewLog: List<ReviewLogEntry> = emptyList()

    private var reviewWindow: ReviewWindow? = null

    val scopeBaseRef: String? get() = scope?.baseRef

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun beforeDocumentChange(event: DocumentEvent) {
                    markEditedLinesAsChanged(event)
                }

                override fun documentChanged(event: DocumentEvent) {
                    afterChangesUpdate.request()
                }
            },
            this,
        )
    }

    fun markRangeAsReviewed(document: Document, range: TextRange) {
        logNewlyReviewedLines(document, range)
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
        rememberStop(document, range)
        remindOfPauseIfDue()
        afterChangesUpdate.request()
    }

    fun reviewSummaryToday(): ReviewSummary =
        summarizeReview(reviewLog, LocalDate.now().toString(), linesLeftByFile())

    fun marksContaining(document: Document, offset: Int): List<ReviewMark> =
        ReviewedMarkHighlighters.inDocument(project, document)
            .filter { offset >= it.range.startOffset && offset <= it.range.endOffset }

    fun clearMarksContaining(document: Document, offset: Int) {
        ReviewedMarkHighlighters.remove(project, document, marksContaining(document, offset))
        afterChangesUpdate.request()
    }

    fun progressOf(document: Document): ReviewProgress {
        val marks = ReviewedMarkHighlighters.inDocument(project, document)
        return reviewProgress(
            documentText = document.text,
            reviewedRanges = rangesWithState(marks, MarkState.REVIEWED),
            changedRanges = rangesWithState(marks, MarkState.CHANGED),
            outOfScopeRanges = OutOfScopeHighlighters.rangesIn(project, document),
        )
    }

    fun goToNextRunToReview(editor: Editor) {
        val document = editor.document
        val reviewedRanges = rangesWithState(ReviewedMarkHighlighters.inDocument(project, document), MarkState.REVIEWED)
        val currentLine = editor.caretModel.logicalPosition.line
        val outOfScopeRanges = OutOfScopeHighlighters.rangesIn(project, document)
        val nextRun = nextRunToReview(linesNeedingReview(document.text, reviewedRanges, outOfScopeRanges), currentLine)
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

    fun changeFoldMode(foldModeOn: Boolean) {
        foldMode = foldModeOn
        refoldOpenDocuments()
    }

    fun goToLastStop(): Boolean {
        val stop = lastStop ?: return false
        val file = VirtualFileManager.getInstance().findFileByUrl(stop.fileUrl) ?: return false
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
        OpenFileDescriptor(project, file, stopOffsetIn(document.text, stop)).navigate(true)
        return true
    }

    fun showStoredMarks(document: Document, file: VirtualFile) {
        if (document.getUserData(DOCUMENT_SHOWS_STORED_MARKS) == true) return
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)

        val documentText = document.text
        storedMarksByFileUrl[file.url].orEmpty().forEach { storedMark ->
            val startOffset = locateMarkedText(documentText, storedMark.markedText, storedMark.startOffset)
            if (startOffset != null) {
                val range = TextRange(startOffset, startOffset + storedMark.markedText.length)
                ReviewedMarkHighlighters.add(project, document, range, MarkState.valueOf(storedMark.state), focusMode)
            }
        }
        showStoredNotes(document, file)
        applyScopeTo(document, file)
        storeMarksOf(document, file)
        afterChangesUpdate.request()
    }

    fun canAddNoteAt(document: Document, offset: Int): Boolean = codeRangeOfLineAt(document, offset) != null

    fun noteTextAt(document: Document, offset: Int): String? = noteOnLineAt(document, offset)?.text

    fun setNoteAt(document: Document, offset: Int, noteText: String) {
        val codeRange = codeRangeOfLineAt(document, offset) ?: return
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)
        val existingNote = noteOnLineAt(document, offset)
        when {
            existingNote != null && noteText.isEmpty() -> ReviewNoteHighlighters.remove(project, document, existingNote)
            existingNote != null -> ReviewNoteHighlighters.changeText(existingNote, noteText)
            noteText.isNotEmpty() -> ReviewNoteHighlighters.add(project, document, codeRange, noteText)
        }
        announceNotesChanged()
    }

    fun allNotes(): List<StoredNote> {
        storeOpenDocuments()
        return storedNotesByFileUrl.values.flatten()
    }

    fun goToNote(note: StoredNote) {
        val file = VirtualFileManager.getInstance().findFileByUrl(note.fileUrl) ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val noteOffset = locateMarkedText(document.text, note.anchorText, note.startOffset)
            ?: note.startOffset.coerceIn(0, document.textLength)
        OpenFileDescriptor(project, file, noteOffset).navigate(true)
    }

    fun deleteNote(note: StoredNote) {
        val file = VirtualFileManager.getInstance().findFileByUrl(note.fileUrl)
        val openDocument = file?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
        val liveNote = openDocument?.let { document ->
            ReviewNoteHighlighters.inDocument(project, document).firstOrNull {
                it.text == note.text && document.getText(it.range) == note.anchorText
            }
        }
        if (liveNote != null && openDocument != null && file != null) {
            ReviewNoteHighlighters.remove(project, openDocument, liveNote)
            storeMarksOf(openDocument, file)
        } else {
            storedNotesByFileUrl[note.fileUrl] = storedNotesByFileUrl[note.fileUrl].orEmpty().filter { it != note }
        }
        announceNotesChanged()
    }

    fun startReviewingOnlyChanges(baseRef: String, changedLinesByPath: Map<String, List<LineRange>>) {
        scope = StoredScope(
            baseRef = baseRef,
            files = changedLinesByPath.map { (path, changedLines) -> StoredScopeFile(path, changedLines.toMutableList()) }.toMutableList(),
        )
        applyScopeToOpenDocuments()
    }

    fun stopReviewingOnlyChanges() {
        scope = null
        applyScopeToOpenDocuments()
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
        storeNotesOf(document, file)
    }

    override fun getState(): ReviewMarksState {
        storeOpenDocuments()
        return ReviewMarksState(
            marks = storedMarksByFileUrl.values.flatten().toMutableList(),
            notes = storedNotesByFileUrl.values.flatten().toMutableList(),
            focusMode = focusMode,
            foldMode = foldMode,
            lastStop = lastStop,
            scope = scope,
            reviewLog = reviewLog.toMutableList(),
        )
    }

    override fun loadState(state: ReviewMarksState) {
        storedMarksByFileUrl.clear()
        storedMarksByFileUrl.putAll(state.marks.groupBy { it.fileUrl })
        storedNotesByFileUrl.clear()
        storedNotesByFileUrl.putAll(state.notes.groupBy { it.fileUrl })
        focusMode = state.focusMode
        foldMode = state.foldMode
        lastStop = state.lastStop
        scope = state.scope
        reviewLog = state.reviewLog.toList()
        reviewWindow = null
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

    private fun afterChanges() {
        refreshProgressWidget()
        refoldOpenDocuments()
        announceNotesChanged()
    }

    private fun logNewlyReviewedLines(document: Document, range: TextRange) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val reviewedRangesBefore = rangesWithState(ReviewedMarkHighlighters.inDocument(project, document), MarkState.REVIEWED)
        val newLineCount = newlyReviewedLineCount(
            documentText = document.text,
            markedRange = range,
            reviewedRangesBefore = reviewedRangesBefore,
            outOfScopeRanges = OutOfScopeHighlighters.rangesIn(project, document),
        )
        reviewLog = addToReviewLog(reviewLog, LocalDate.now().toString(), file.url, newLineCount)
    }

    private fun remindOfPauseIfDue() {
        val pauseSettings = service<ReviewMarksSettings>().state
        if (!pauseSettings.pauseRemindersOn) {
            reviewWindow = null
            return
        }
        val pauseCheck = checkForPause(
            windowBeforeBlock = reviewWindow,
            nowMillis = System.currentTimeMillis(),
            minutesBetweenPauses = pauseSettings.minutesBetweenPauses,
            blocksBetweenPauses = pauseSettings.blocksBetweenPauses,
        )
        reviewWindow = pauseCheck.windowAfterBlock
        pauseCheck.reminder?.let { showPauseReminder(project, it) }
    }

    private fun linesLeftByFile(): List<FileLinesLeft> {
        storeOpenDocuments()
        val scopeFileUrls = scope?.files.orEmpty().map { VirtualFileManager.constructUrl("file", it.path) }
        val fileUrlsInReview = (storedMarksByFileUrl.keys + storedNotesByFileUrl.keys + scopeFileUrls).distinct()
        return fileUrlsInReview.mapNotNull { linesLeftIn(it) }.filter { it.lineCount > 0 }
    }

    private fun linesLeftIn(fileUrl: String): FileLinesLeft? {
        val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
        val documentText = FileDocumentManager.getInstance().getDocument(file)?.text ?: return null
        val reviewedRanges = storedMarksByFileUrl[fileUrl].orEmpty()
            .filter { it.state == MarkState.REVIEWED.name }
            .mapNotNull { storedMark ->
                locateMarkedText(documentText, storedMark.markedText, storedMark.startOffset)
                    ?.let { TextRange(it, it + storedMark.markedText.length) }
            }
        val changedLines = scope?.files?.firstOrNull { it.path == file.path }?.changedLines.orEmpty()
        val outOfScopeRanges = if (scope == null) emptyList() else outOfScopeRanges(documentText, changedLines)
        return FileLinesLeft(fileUrl, linesNeedingReview(documentText, reviewedRanges, outOfScopeRanges).size)
    }

    private fun announceNotesChanged() {
        project.messageBus.syncPublisher(ReviewNotesListener.TOPIC).notesChanged()
    }

    private fun storeOpenDocuments() {
        runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
                val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile)
                if (openDocument != null) storeMarksOf(openDocument, openFile)
            }
        }
    }

    private fun showStoredNotes(document: Document, file: VirtualFile) {
        val documentText = document.text
        storedNotesByFileUrl[file.url].orEmpty().forEach { storedNote ->
            val startOffset = locateMarkedText(documentText, storedNote.anchorText, storedNote.startOffset)
            if (startOffset != null) {
                val codeRange = TextRange(startOffset, startOffset + storedNote.anchorText.length)
                ReviewNoteHighlighters.add(project, document, codeRange, storedNote.text)
            }
        }
    }

    private fun storeNotesOf(document: Document, file: VirtualFile) {
        val notesInDocument = ReviewNoteHighlighters.inDocument(project, document).map { note ->
            StoredNote(
                fileUrl = file.url,
                startOffset = note.range.startOffset,
                anchorText = document.getText(note.range),
                text = note.text,
            )
        }
        if (notesInDocument.isEmpty()) {
            storedNotesByFileUrl.remove(file.url)
        } else {
            storedNotesByFileUrl[file.url] = notesInDocument
        }
    }

    private fun noteOnLineAt(document: Document, offset: Int): ReviewNote? {
        val lineNumber = document.getLineNumber(offset)
        return ReviewNoteHighlighters.inDocument(project, document)
            .firstOrNull { document.getLineNumber(it.range.startOffset) == lineNumber }
    }

    private fun codeRangeOfLineAt(document: Document, offset: Int): TextRange? {
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStartOffset, document.getLineEndOffset(lineNumber)))
        val code = lineText.trim()
        if (code.isEmpty()) return null
        val codeStartOffset = lineStartOffset + lineText.indexOf(code)
        return TextRange(codeStartOffset, codeStartOffset + code.length)
    }

    private fun refreshProgressWidget() {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        (statusBar.getWidget(ReviewProgressWidget.WIDGET_ID) as? ReviewProgressWidget)?.refresh()
    }

    private fun applyScopeToOpenDocuments() {
        FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
            val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile) ?: return@forEach
            applyScopeTo(openDocument, openFile)
        }
        afterChangesUpdate.request()
    }

    private fun applyScopeTo(document: Document, file: VirtualFile) {
        val activeScope = scope
        if (activeScope == null) {
            OutOfScopeHighlighters.replace(project, document, emptyList())
            return
        }
        val changedLines = activeScope.files.firstOrNull { it.path == file.path }?.changedLines.orEmpty()
        OutOfScopeHighlighters.replace(project, document, outOfScopeRanges(document.text, changedLines))
    }

    private fun refoldOpenDocuments() {
        FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
            val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile) ?: return@forEach
            val rangesToFold = if (foldMode) {
                rangesWithState(ReviewedMarkHighlighters.inDocument(project, openDocument), MarkState.REVIEWED)
            } else {
                emptyList()
            }
            ReviewedFolds.show(project, openDocument, rangesToFold)
        }
    }

    private fun rememberStop(document: Document, reviewedRange: TextRange) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val lastReviewedLine = document.getLineNumber(maxOf(reviewedRange.startOffset, reviewedRange.endOffset - 1))
        val lineStartOffset = document.getLineStartOffset(lastReviewedLine)
        val lineText = document.getText(TextRange(lineStartOffset, document.getLineEndOffset(lastReviewedLine)))
        val anchorText = lineText.trim()
        lastStop = StoredStop(
            fileUrl = file.url,
            startOffset = lineStartOffset + lineText.indexOf(anchorText),
            anchorText = anchorText,
        )
    }
}
