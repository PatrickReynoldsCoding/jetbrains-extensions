package com.patrickreynolds.reviewmarks

import com.intellij.codeInsight.hint.HintManager
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
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import java.time.LocalDate

private val DOCUMENT_SHOWS_STORED_MARKS = Key.create<Boolean>("reviewmarks.documentShowsStoredMarks")

private const val AFTER_CHANGES_DELAY_MILLISECONDS = 300

@Service(Service.Level.PROJECT)
@State(name = "ReviewMarks", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ReviewMarksService(private val project: Project) : PersistentStateComponent<ReviewMarksState>, Disposable {

    private val storedMarksByFileUrl = mutableMapOf<String, List<StoredMark>>()

    private val storedNotesByFileUrl = mutableMapOf<String, List<StoredNote>>()

    private val afterChangesUpdate = SingleAlarm(
        { afterChanges() },
        AFTER_CHANGES_DELAY_MILLISECONDS,
        this,
        Alarm.ThreadToUse.SWING_THREAD,
    )

    var foldMode: Boolean = false
        private set

    var pullRequest: PullRequestSession? = null
        private set

    private val agentNotesFile = AgentNotesFile(project)

    private var lastStop: StoredStop? = null

    private var reviewLog: List<ReviewLogEntry> = emptyList()

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
        replaceMarksIn(document, range, MarkState.REVIEWED)
        rememberStop(document, range)
        afterChangesUpdate.request()
    }

    fun markRangeToReview(document: Document, range: TextRange) {
        replaceMarksIn(document, range, MarkState.TO_REVIEW)
        afterChangesUpdate.request()
    }

    fun markChangedLinesToReview(changedLinesByFile: Map<VirtualFile, List<LineRange>>): Int {
        var markedFileCount = 0
        changedLinesByFile.forEach { (file, changedLines) ->
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@forEach
            showStoredMarks(document, file)

            val rangesAlreadyMarked = ReviewedMarkHighlighters.inDocument(project, document).map { it.range }
            val unmarkedChangedRanges = changedLines
                .mapNotNull { textRangeOfLines(document.text, it) }
                .flatMap { withoutRanges(it, rangesAlreadyMarked) }
                .mapNotNull { trimmedToCode(document.text, it) }
            unmarkedChangedRanges.forEach { ReviewedMarkHighlighters.add(project, document, it, MarkState.TO_REVIEW) }
            storeMarksOf(document, file)
            if (unmarkedChangedRanges.isNotEmpty()) markedFileCount++
        }
        afterChangesUpdate.request()
        return markedFileCount
    }

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
            toReviewRanges = rangesWithState(marks, MarkState.TO_REVIEW),
            changedRanges = rangesWithState(marks, MarkState.CHANGED),
        )
    }

    fun goToNextRunToReview(editor: Editor) {
        val document = editor.document
        val marks = ReviewedMarkHighlighters.inDocument(project, document)
        val rangesToReview = rangesWithState(marks, MarkState.TO_REVIEW) + rangesWithState(marks, MarkState.CHANGED)
        val currentLine = editor.caretModel.logicalPosition.line
        val nextRun = nextRunToReview(linesToReview(document.text, rangesToReview), currentLine)
        if (nextRun == null) {
            HintManager.getInstance().showInformationHint(editor, "Nothing marked to review in this file")
            return
        }
        editor.caretModel.moveToLogicalPosition(LogicalPosition(nextRun.firstLine, 0))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        if (nextRun.wrappedToTop) {
            HintManager.getInstance().showInformationHint(editor, "Nothing more below. Continued from the top")
        }
    }

    fun changeFoldMode(foldModeOn: Boolean) {
        foldMode = foldModeOn
        refoldOpenDocuments()
        afterChangesUpdate.request()
    }

    fun goToLastStop(): Boolean {
        val stop = lastStop ?: return false
        val file = VirtualFileManager.getInstance().findFileByUrl(stop.fileUrl) ?: return false
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
        OpenFileDescriptor(project, file, stopOffsetIn(document.text, stop)).navigate(true)
        return true
    }

    fun reviewSummaryToday(): ReviewSummary =
        summarizeReview(reviewLog, LocalDate.now().toString(), linesLeftByFile())

    fun reviewedBlocks(): List<FileBlocks> {
        storeOpenDocuments()
        return storedMarksByFileUrl.keys.mapNotNull { fileUrl ->
            val fileRanges = rangesOfFile(fileUrl) ?: return@mapNotNull null
            val rows = blockRows(BlockKind.REVIEWED, fileRanges.documentText, fileRanges.reviewed)
            if (rows.isEmpty()) null else FileBlocks(fileUrl, rows)
        }.sortedBy { it.fileName }
    }

    fun blocksToReview(): List<FileBlocks> {
        storeOpenDocuments()
        return storedMarksByFileUrl.keys.mapNotNull { fileUrl ->
            val fileRanges = rangesOfFile(fileUrl) ?: return@mapNotNull null
            val rows = toReviewBlockRows(fileRanges.documentText, fileRanges.toReview, fileRanges.changed)
            if (rows.isEmpty()) null else FileBlocks(fileUrl, rows)
        }.sortedBy { it.fileName }
    }

    fun goToBlock(fileUrl: String, offset: Int) {
        val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return
        OpenFileDescriptor(project, file, offset).navigate(true)
    }

    fun clearMarkedBlock(fileUrl: String, blockRange: TextRange) {
        storeOpenDocuments()
        val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
        val openDocument = file?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
        if (openDocument != null && file != null && openDocument.getUserData(DOCUMENT_SHOWS_STORED_MARKS) == true) {
            val marksInBlock = ReviewedMarkHighlighters.inDocument(project, openDocument).filter { blockRange.contains(it.range) }
            ReviewedMarkHighlighters.remove(project, openDocument, marksInBlock)
            storeMarksOf(openDocument, file)
        } else {
            val documentText = rangesOfFile(fileUrl)?.documentText.orEmpty()
            val marksToKeep = storedMarksByFileUrl[fileUrl].orEmpty().filterNot { storedMark ->
                val markStart = locateMarkedText(documentText, storedMark.markedText, storedMark.startOffset)
                markStart != null && blockRange.contains(TextRange(markStart, markStart + storedMark.markedText.length))
            }
            if (marksToKeep.isEmpty()) storedMarksByFileUrl.remove(fileUrl) else storedMarksByFileUrl[fileUrl] = marksToKeep
        }
        afterChangesUpdate.request()
    }

    fun clearAllMarks() {
        FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
            val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile) ?: return@forEach
            ReviewedMarkHighlighters.remove(project, openDocument, ReviewedMarkHighlighters.inDocument(project, openDocument))
        }
        storedMarksByFileUrl.clear()
        storeOpenDocuments()
        afterChangesUpdate.request()
    }

    fun showStoredMarks(document: Document, file: VirtualFile) {
        if (document.getUserData(DOCUMENT_SHOWS_STORED_MARKS) == true) return
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)

        val documentText = document.text
        storedMarksByFileUrl[file.url].orEmpty().forEach { storedMark ->
            val startOffset = locateMarkedText(documentText, storedMark.markedText, storedMark.startOffset)
            if (startOffset != null) {
                val range = TextRange(startOffset, startOffset + storedMark.markedText.length)
                ReviewedMarkHighlighters.add(project, document, range, MarkState.valueOf(storedMark.state))
            }
        }
        showStoredNotes(document, file)
        storeMarksOf(document, file)
        showFloatingNotesOf(document)
        afterChangesUpdate.request()
    }

    fun canAddNoteAt(document: Document, offset: Int): Boolean = codeRangeOfLineAt(document, offset) != null

    fun noteDetailsAt(document: Document, offset: Int): NoteDetails? = noteOnLineAt(document, offset)?.details

    fun setNoteAt(document: Document, offset: Int, details: NoteDetails) {
        val codeRange = codeRangeOfLineAt(document, offset) ?: return
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)
        val existingNote = noteOnLineAt(document, offset)
        when {
            existingNote != null && details.text.isEmpty() -> ReviewNoteHighlighters.remove(project, document, existingNote)
            existingNote != null -> ReviewNoteHighlighters.changeDetails(existingNote, details)
            details.text.isNotEmpty() -> ReviewNoteHighlighters.add(project, document, codeRange, details)
        }
        refreshNoteInlays()
        announceReviewStateChanged()
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
            refreshNoteInlays()
        } else {
            storedNotesByFileUrl[note.fileUrl] = storedNotesByFileUrl[note.fileUrl].orEmpty().filter { it != note }
        }
        announceReviewStateChanged()
    }

    fun startPullRequestReview(session: PullRequestSession, changedLinesByFile: Map<VirtualFile, List<LineRange>>): Int {
        pullRequest = session
        val markedFileCount = markChangedLinesToReview(changedLinesByFile)
        announceReviewStateChanged()
        return markedFileCount
    }

    fun endPullRequestReview() {
        pullRequest = null
        announceReviewStateChanged()
    }

    fun pullRequestComments(): List<ReviewComment> {
        val session = pullRequest ?: return emptyList()
        return allNotes().filter { noteKindNamed(it.kind) == NoteKind.PR_COMMENT }.mapNotNull { note ->
            val file = VirtualFileManager.getInstance().findFileByUrl(note.fileUrl) ?: return@mapNotNull null
            val relativePath = relativePathIn(session.repositoryRoot, file.path) ?: return@mapNotNull null
            val documentText = FileDocumentManager.getInstance().getDocument(file)?.text ?: return@mapNotNull null
            val noteOffset = locateMarkedText(documentText, note.anchorText, note.startOffset) ?: note.startOffset
            ReviewComment(relativePath, lineNumberAt(documentText, noteOffset), note.text)
        }
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
            foldMode = foldMode,
            lastStop = lastStop,
            reviewLog = reviewLog.toMutableList(),
            pullRequest = pullRequest,
        )
    }

    override fun loadState(state: ReviewMarksState) {
        storedMarksByFileUrl.clear()
        storedMarksByFileUrl.putAll(state.marks.groupBy { it.fileUrl })
        storedNotesByFileUrl.clear()
        storedNotesByFileUrl.putAll(state.notes.groupBy { it.fileUrl })
        foldMode = state.foldMode
        lastStop = state.lastStop
        reviewLog = state.reviewLog.toList()
        pullRequest = state.pullRequest
    }

    override fun dispose() = Unit

    private fun replaceMarksIn(document: Document, range: TextRange, state: MarkState) {
        document.putUserData(DOCUMENT_SHOWS_STORED_MARKS, true)
        ReviewedMarkHighlighters.inDocument(project, document)
            .filter { it.range.intersectsStrict(range) }
            .forEach { overlappedMark ->
                ReviewedMarkHighlighters.remove(project, document, listOf(overlappedMark))
                withoutRange(overlappedMark.range, range).forEach { remainingRange ->
                    ReviewedMarkHighlighters.add(project, document, remainingRange, overlappedMark.state)
                }
            }
        ReviewedMarkHighlighters.add(project, document, range, state)
    }

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
                ReviewedMarkHighlighters.add(project, event.document, it, MarkState.REVIEWED)
            }
            ReviewedMarkHighlighters.add(project, event.document, split.changedLines, MarkState.CHANGED)
            split.unchangedAfter?.let {
                ReviewedMarkHighlighters.add(project, event.document, it, MarkState.REVIEWED)
            }
        }
    }

    private fun afterChanges() {
        refreshProgressWidget()
        refoldOpenDocuments()
        refreshNoteInlays()
        announceReviewStateChanged()
    }

    private fun refreshProgressWidget() {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        (statusBar.getWidget(ReviewProgressWidget.WIDGET_ID) as? ReviewProgressWidget)?.refresh()
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

    private fun refreshNoteInlays() {
        FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
            val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile) ?: return@forEach
            showFloatingNotesOf(openDocument)
        }
    }

    private fun showFloatingNotesOf(document: Document) {
        val floatingNotes = ReviewNoteHighlighters.inDocument(project, document).filter { it.details.floating }
        NoteInlays.show(project, document, floatingNotes)
    }

    private fun announceReviewStateChanged() {
        project.messageBus.syncPublisher(ReviewStateListener.TOPIC).reviewStateChanged()
        saveAgentNotes()
    }

    private fun saveAgentNotes() {
        val entries = allNotes().filter { noteKindNamed(it.kind) == NoteKind.AGENT_NOTE }.mapNotNull { note ->
            val file = VirtualFileManager.getInstance().findFileByUrl(note.fileUrl) ?: return@mapNotNull null
            val documentText = FileDocumentManager.getInstance().getDocument(file)?.text ?: return@mapNotNull null
            val noteOffset = locateMarkedText(documentText, note.anchorText, note.startOffset) ?: note.startOffset
            val lineNumber = lineNumberAt(documentText, noteOffset)
            AgentNoteEntry(
                relativePath = project.basePath?.let { relativePathIn(it, file.path) } ?: file.path,
                lineNumber = lineNumber,
                codeLine = documentText.lines().getOrElse(lineNumber - 1) { "" }.trim(),
                noteText = note.text,
            )
        }
        agentNotesFile.save(entries)
    }

    private fun storeOpenDocuments() {
        runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { openFile ->
                val openDocument = FileDocumentManager.getInstance().getCachedDocument(openFile)
                if (openDocument != null) storeMarksOf(openDocument, openFile)
            }
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

    private fun logNewlyReviewedLines(document: Document, range: TextRange) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val reviewedRangesBefore = rangesWithState(ReviewedMarkHighlighters.inDocument(project, document), MarkState.REVIEWED)
        val newLineCount = newlyReviewedLineCount(document.text, range, reviewedRangesBefore)
        reviewLog = addToReviewLog(reviewLog, LocalDate.now().toString(), file.url, newLineCount)
    }

    private fun linesLeftByFile(): List<FileLinesLeft> {
        storeOpenDocuments()
        return (storedMarksByFileUrl.keys + storedNotesByFileUrl.keys).distinct()
            .mapNotNull { linesLeftIn(it) }
            .filter { it.lineCount > 0 }
    }

    private fun linesLeftIn(fileUrl: String): FileLinesLeft? {
        val fileRanges = rangesOfFile(fileUrl) ?: return null
        val lineCount = linesToReview(fileRanges.documentText, fileRanges.toReview + fileRanges.changed).size
        return FileLinesLeft(fileUrl, lineCount)
    }

    private fun rangesOfFile(fileUrl: String): FileRanges? {
        val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
        val documentText = FileDocumentManager.getInstance().getDocument(file)?.text ?: return null

        fun storedRangesWithState(state: MarkState): List<TextRange> = storedMarksByFileUrl[fileUrl].orEmpty()
            .filter { it.state == state.name }
            .mapNotNull { storedMark ->
                locateMarkedText(documentText, storedMark.markedText, storedMark.startOffset)
                    ?.let { TextRange(it, it + storedMark.markedText.length) }
            }

        return FileRanges(
            documentText = documentText,
            reviewed = storedRangesWithState(MarkState.REVIEWED),
            toReview = storedRangesWithState(MarkState.TO_REVIEW),
            changed = storedRangesWithState(MarkState.CHANGED),
        )
    }

    private fun showStoredNotes(document: Document, file: VirtualFile) {
        val documentText = document.text
        storedNotesByFileUrl[file.url].orEmpty().forEach { storedNote ->
            val startOffset = locateMarkedText(documentText, storedNote.anchorText, storedNote.startOffset)
            if (startOffset != null) {
                val codeRange = TextRange(startOffset, startOffset + storedNote.anchorText.length)
                val details = NoteDetails(storedNote.text, storedNote.floating, noteKindNamed(storedNote.kind))
                ReviewNoteHighlighters.add(project, document, codeRange, details)
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
                floating = note.details.floating,
                kind = note.details.kind.name,
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
}
