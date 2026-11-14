package com.patrickreynolds.reviewmarks

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewMarksServiceTest : BasePlatformTestCase() {

    private val documentText = "line one\nline two\nline three\nline four\nline five\n"
    private val lineTwoStart = documentText.indexOf("line two")
    private val lineThreeStart = documentText.indexOf("line three")
    private val lineThreeEnd = lineThreeStart + "line three".length
    private val lineFourEnd = documentText.indexOf("line four") + "line four".length

    private fun reviewMarks() = project.service<ReviewMarksService>()

    override fun setUp() {
        super.setUp()
        reviewMarks().loadState(ReviewMarksState())
    }

    private fun openDocumentWithLinesTwoToFourMarked(): Document {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineFourEnd))
        return document
    }

    private fun marks(document: Document) = ReviewedMarkHighlighters.inDocument(project, document)
        .map { Triple(it.state, it.range.startOffset, it.range.endOffset) }
        .sortedBy { it.second }

    private fun collectNotifications(): List<Notification> {
        val shownNotifications = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    shownNotifications += notification
                }
            },
        )
        return shownNotifications
    }

    private fun edit(change: () -> Unit) = WriteCommandAction.runWriteCommandAction(project) { change() }

    fun `test a marked block is highlighted as reviewed`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        assertEquals(listOf(Triple(MarkState.REVIEWED, lineTwoStart, lineFourEnd)), marks(document))
    }

    fun `test editing one line turns only that line to changed and keeps the lines around it reviewed`() {
        val document = openDocumentWithLinesTwoToFourMarked()

        edit { document.insertString(lineThreeStart + 5, "X") }

        assertEquals(
            listOf(
                Triple(MarkState.REVIEWED, lineTwoStart, lineThreeStart),
                Triple(MarkState.CHANGED, lineThreeStart, lineThreeEnd + 1),
                Triple(MarkState.REVIEWED, lineThreeEnd + 1, lineFourEnd + 1),
            ),
            marks(document),
        )
    }

    fun `test typing more into a changed line keeps it as one changed line`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(lineThreeStart + 5, "X") }

        edit { document.insertString(lineThreeStart + 6, "Y") }
        edit { document.insertString(lineThreeEnd + 2, "Z") }

        assertEquals(
            listOf(
                Triple(MarkState.REVIEWED, lineTwoStart, lineThreeStart),
                Triple(MarkState.CHANGED, lineThreeStart, lineThreeEnd + 3),
                Triple(MarkState.REVIEWED, lineThreeEnd + 3, lineFourEnd + 3),
            ),
            marks(document),
        )
    }

    fun `test marking the changed line as reviewed turns it green again`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(lineThreeStart + 5, "X") }

        reviewMarks().markRangeAsReviewed(document, TextRange(lineThreeStart, lineThreeEnd + 1))

        val reviewedStates = marks(document).map { it.first }.toSet()
        assertEquals(setOf(MarkState.REVIEWED), reviewedStates)
    }

    fun `test editing above the marked block keeps the mark and moves it with the text`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(0, "new line\n") }
        val shift = "new line\n".length
        assertEquals(
            listOf(Triple(MarkState.REVIEWED, lineTwoStart + shift, lineFourEnd + shift)),
            marks(document),
        )
    }

    fun `test editing below the marked block keeps the mark`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(document.textLength, "line six\n") }
        assertEquals(listOf(Triple(MarkState.REVIEWED, lineTwoStart, lineFourEnd)), marks(document))
    }

    fun `test typing on an empty line inside a marked block marks that typed text as changed`() {
        myFixture.configureByText("Gap.txt", "one\n\ntwo\n")
        val document = myFixture.editor.document
        reviewMarks().markRangeAsReviewed(document, TextRange(0, 8))

        edit { document.insertString(4, "new") }

        val changedTexts = ReviewedMarkHighlighters.inDocument(project, document)
            .filter { it.state == MarkState.CHANGED }
            .map { document.getText(it.range) }
        assertEquals(listOf("new"), changedTexts)
    }

    fun `test clearing at the caret removes only the mark under the caret`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(lineThreeStart + 5, "X") }

        reviewMarks().clearMarksContaining(document, lineThreeStart + 2)

        assertEquals(
            listOf(
                Triple(MarkState.REVIEWED, lineTwoStart, lineThreeStart),
                Triple(MarkState.REVIEWED, lineThreeEnd + 1, lineFourEnd + 1),
            ),
            marks(document),
        )
    }

    fun `test the saved state holds the marked text, where it starts and whether it changed`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(lineThreeStart + 5, "X") }

        val savedMarks = reviewMarks().state.marks.sortedBy { it.startOffset }

        assertEquals(listOf("REVIEWED", "CHANGED", "REVIEWED"), savedMarks.map { it.state })
        assertEquals("line Xthree", savedMarks[1].markedText)
        assertEquals(lineThreeStart, savedMarks[1].startOffset)
    }

    fun `test a saved mark comes back when the file is opened and its text is unchanged`() {
        val file = myFixture.addFileToProject("Saved.txt", documentText).virtualFile
        reviewMarks().loadState(
            ReviewMarksState(mutableListOf(StoredMark(file.url, lineTwoStart, "line two", "CHANGED"))),
        )

        myFixture.openFileInEditor(file)

        assertEquals(
            listOf(Triple(MarkState.CHANGED, lineTwoStart, lineTwoStart + "line two".length)),
            marks(myFixture.editor.document),
        )
    }

    fun `test a saved mark follows its text when lines were added above it while the file was closed`() {
        val textWithLineAdded = "added line\n$documentText"
        val file = myFixture.addFileToProject("Moved.txt", textWithLineAdded).virtualFile
        reviewMarks().loadState(ReviewMarksState(mutableListOf(StoredMark(file.url, lineTwoStart, "line two"))))

        myFixture.openFileInEditor(file)

        val movedStart = textWithLineAdded.indexOf("line two")
        assertEquals(
            listOf(Triple(MarkState.REVIEWED, movedStart, movedStart + "line two".length)),
            marks(myFixture.editor.document),
        )
    }

    fun `test a saved mark is dropped when its text was edited while the file was closed`() {
        val file = myFixture.addFileToProject("Edited.txt", documentText.replace("line two", "line 2")).virtualFile
        reviewMarks().loadState(ReviewMarksState(mutableListOf(StoredMark(file.url, lineTwoStart, "line two"))))

        myFixture.openFileInEditor(file)

        assertEquals(emptyList<Triple<MarkState, Int, Int>>(), marks(myFixture.editor.document))
    }

    fun `test focus mode fades the text of reviewed code and the state is saved`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        val reviewedMark = ReviewedMarkHighlighters.inDocument(project, document).single()
        assertNotNull(reviewedMark.highlighter.getTextAttributes(myFixture.editor.colorsScheme)?.backgroundColor)

        reviewMarks().changeFocusMode(true)

        val fadedMark = ReviewedMarkHighlighters.inDocument(project, document).single()
        val fadedAttributes = fadedMark.highlighter.getTextAttributes(myFixture.editor.colorsScheme)
        assertNull(fadedAttributes?.backgroundColor)
        assertNotNull(fadedAttributes?.foregroundColor)
        assertTrue(reviewMarks().state.focusMode)
    }

    fun `test progress counts the reviewed code lines`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        assertEquals(
            ReviewProgress(reviewedLineCount = 3, codeLineCount = 5, changedLineCount = 0),
            reviewMarks().progressOf(document),
        )
    }

    fun `test going to the next code to review moves the caret to the first line that is not reviewed`() {
        openDocumentWithLinesTwoToFourMarked()
        myFixture.editor.caretModel.moveToOffset(0)

        reviewMarks().goToNextRunToReview(myFixture.editor)

        assertEquals(4, myFixture.editor.caretModel.logicalPosition.line)
    }

    fun `test marking the method around the caret marks the whole method`() {
        val javaText = "class Sample {\n    int total(int a, int b) {\n        int sum = a + b;\n        return sum;\n    }\n}\n"
        myFixture.configureByText("Sample.java", javaText)
        myFixture.editor.caretModel.moveToOffset(javaText.indexOf("int sum"))

        val range = findEnclosingDeclarationRange(myFixture.file, myFixture.editor.document, myFixture.editor.caretModel.offset)

        val methodStart = javaText.indexOf("    int total")
        val methodEnd = javaText.indexOf("    }\n") + "    }".length
        assertEquals(TextRange(methodStart, methodEnd), range)
    }

    fun `test fold mode folds reviewed lines into one line and unfolds them when turned off`() {
        openDocumentWithLinesTwoToFourMarked()

        reviewMarks().changeFoldMode(true)

        val foldedRegions = myFixture.editor.foldingModel.allFoldRegions.filter { !it.isExpanded }
        assertEquals(1, foldedRegions.size)
        assertEquals(lineTwoStart, foldedRegions.single().startOffset)
        assertEquals(lineFourEnd, foldedRegions.single().endOffset)
        assertEquals("✓ 3 reviewed lines", foldedRegions.single().placeholderText)

        reviewMarks().changeFoldMode(false)

        assertEquals(0, myFixture.editor.foldingModel.allFoldRegions.size)
    }

    fun `test going back to where I stopped opens the end of the last reviewed line`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        myFixture.editor.caretModel.moveToOffset(0)

        val wentBack = reviewMarks().goToLastStop()

        assertTrue(wentBack)
        val editorAfterGoingBack = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertEquals(lineFourEnd, editorAfterGoingBack.caretModel.offset)
        assertEquals(document, editorAfterGoingBack.document)
    }

    fun `test going back to where I stopped does nothing before anything was reviewed`() {
        myFixture.configureByText("Fresh.txt", documentText)
        assertFalse(reviewMarks().goToLastStop())
    }

    fun `test the last stop and fold mode are saved`() {
        openDocumentWithLinesTwoToFourMarked()
        reviewMarks().changeFoldMode(true)

        val savedState = reviewMarks().state

        assertTrue(savedState.foldMode)
        assertEquals("line four", savedState.lastStop?.anchorText)
    }

    fun `test reviewing only branch changes fades everything else and counts only the changed lines`() {
        val file = myFixture.configureByText("Scoped.txt", documentText).virtualFile
        val document = myFixture.editor.document

        reviewMarks().startReviewingOnlyChanges("origin/main", mapOf(file.path to listOf(LineRange(2, 3))))

        assertEquals(
            listOf(TextRange(0, lineThreeStart - 1), TextRange(documentText.indexOf("line five"), documentText.length)),
            OutOfScopeHighlighters.rangesIn(project, document),
        )
        assertEquals(
            ReviewProgress(reviewedLineCount = 0, codeLineCount = 2, changedLineCount = 0),
            reviewMarks().progressOf(document),
        )
        assertEquals("origin/main", reviewMarks().scopeBaseRef)
    }

    fun `test reviewing a changed line in scope raises the progress`() {
        val file = myFixture.configureByText("Scoped.txt", documentText).virtualFile
        val document = myFixture.editor.document
        reviewMarks().startReviewingOnlyChanges("origin/main", mapOf(file.path to listOf(LineRange(2, 3))))

        reviewMarks().markRangeAsReviewed(document, TextRange(lineThreeStart, lineThreeEnd))

        assertEquals(
            ReviewProgress(reviewedLineCount = 1, codeLineCount = 2, changedLineCount = 0),
            reviewMarks().progressOf(document),
        )
    }

    fun `test a file that is not part of the branch changes is entirely out of scope`() {
        myFixture.configureByText("Other.txt", documentText)
        val document = myFixture.editor.document

        reviewMarks().startReviewingOnlyChanges("origin/main", emptyMap())

        assertEquals(listOf(TextRange(0, documentText.length)), OutOfScopeHighlighters.rangesIn(project, document))
        assertEquals("", reviewMarks().progressOf(document).statusText())
    }

    fun `test stopping the branch review counts every line again and the scope is saved while it is on`() {
        val file = myFixture.configureByText("Scoped.txt", documentText).virtualFile
        val document = myFixture.editor.document
        reviewMarks().startReviewingOnlyChanges("origin/main", mapOf(file.path to listOf(LineRange(2, 3))))
        assertEquals("origin/main", reviewMarks().state.scope?.baseRef)

        reviewMarks().stopReviewingOnlyChanges()

        assertEquals(emptyList<TextRange>(), OutOfScopeHighlighters.rangesIn(project, document))
        assertEquals(5, reviewMarks().progressOf(document).codeLineCount)
        assertNull(reviewMarks().scopeBaseRef)
    }

    fun `test a note puts a gutter icon on its line and shows the note as the tooltip`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document

        reviewMarks().setNoteAt(document, lineThreeStart + 2, "ask why this retries")

        val note = ReviewNoteHighlighters.inDocument(project, document).single()
        assertEquals("ask why this retries", note.text)
        assertEquals("line three", document.getText(note.range))
        assertEquals("ask why this retries", note.highlighter.gutterIconRenderer?.tooltipText)
    }

    fun `test setting the note again changes its text and an empty note deletes it`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, "first")

        reviewMarks().setNoteAt(document, lineThreeStart + 4, "second")
        assertEquals(listOf("second"), ReviewNoteHighlighters.inDocument(project, document).map { it.text })
        assertEquals("second", reviewMarks().noteTextAt(document, lineThreeStart))

        reviewMarks().setNoteAt(document, lineThreeStart, "")
        assertEquals(emptyList<ReviewNote>(), ReviewNoteHighlighters.inDocument(project, document))
    }

    fun `test a note can only be added on a line of code`() {
        myFixture.configureByText("Gap.txt", "one\n\ntwo\n")
        val document = myFixture.editor.document

        assertFalse(reviewMarks().canAddNoteAt(document, 4))
        assertTrue(reviewMarks().canAddNoteAt(document, 0))
    }

    fun `test a note stays on its line when lines are added above it`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, "keep")

        edit { document.insertString(0, "new line\n") }

        val note = ReviewNoteHighlighters.inDocument(project, document).single()
        assertEquals("line three", document.getText(note.range))
    }

    fun `test saved state holds the notes and listing them shows the notes of open files`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, "ask Sam")

        val savedNotes = reviewMarks().state.notes
        val listedNotes = reviewMarks().allNotes()

        assertEquals(listOf("ask Sam"), savedNotes.map { it.text })
        assertEquals(listOf("line three"), savedNotes.map { it.anchorText })
        assertEquals(lineThreeStart, savedNotes.single().startOffset)
        assertEquals(savedNotes, listedNotes)
    }

    fun `test a saved note comes back when the file is opened and its line is unchanged`() {
        val file = myFixture.addFileToProject("SavedNotes.txt", documentText).virtualFile
        reviewMarks().loadState(
            ReviewMarksState(notes = mutableListOf(StoredNote(file.url, lineThreeStart, "line three", "ask Sam"))),
        )

        myFixture.openFileInEditor(file)

        val note = ReviewNoteHighlighters.inDocument(project, myFixture.editor.document).single()
        assertEquals("ask Sam", note.text)
        assertEquals(TextRange(lineThreeStart, lineThreeEnd), note.range)
    }

    fun `test a saved note is dropped when its line was edited while the file was closed`() {
        val file = myFixture.addFileToProject("EditedNotes.txt", documentText.replace("line three", "line 3")).virtualFile
        reviewMarks().loadState(
            ReviewMarksState(notes = mutableListOf(StoredNote(file.url, lineThreeStart, "line three", "ask Sam"))),
        )

        myFixture.openFileInEditor(file)

        assertEquals(emptyList<ReviewNote>(), ReviewNoteHighlighters.inDocument(project, myFixture.editor.document))
    }

    fun `test deleting a note from the list removes it from the open file`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, "ask Sam")

        reviewMarks().deleteNote(reviewMarks().allNotes().single())

        assertEquals(emptyList<ReviewNote>(), ReviewNoteHighlighters.inDocument(project, document))
        assertEquals(emptyList<StoredNote>(), reviewMarks().allNotes())
    }

    fun `test today's summary counts each reviewed line once and lists what is left`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))

        val summary = reviewMarks().reviewSummaryToday()

        assertEquals(3, summary.reviewedLineCountToday)
        assertEquals(1, summary.fileCountReviewedToday)
        assertEquals(listOf(2), summary.filesWithLinesLeft.map { it.lineCount })
    }

    fun `test the review log is saved with the state`() {
        openDocumentWithLinesTwoToFourMarked()
        assertEquals(listOf(3), reviewMarks().state.reviewLog.map { it.reviewedLineCount })
    }

    fun `test a pause reminder appears after enough reviewed blocks when reminders are on`() {
        val pauseSettings = service<ReviewMarksSettings>().state
        val previousSettings = Triple(pauseSettings.pauseRemindersOn, pauseSettings.minutesBetweenPauses, pauseSettings.blocksBetweenPauses)
        val shownNotifications = collectNotifications()
        try {
            pauseSettings.pauseRemindersOn = true
            pauseSettings.minutesBetweenPauses = 0
            pauseSettings.blocksBetweenPauses = 2
            myFixture.configureByText("Pause.txt", documentText)
            val document = myFixture.editor.document

            reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))
            assertEquals(0, shownNotifications.size)
            reviewMarks().markRangeAsReviewed(document, TextRange(lineThreeStart, lineFourEnd))

            assertEquals(1, shownNotifications.size)
            assertTrue(shownNotifications.single().content.startsWith("You have reviewed 2 blocks"))
        } finally {
            pauseSettings.pauseRemindersOn = previousSettings.first
            pauseSettings.minutesBetweenPauses = previousSettings.second
            pauseSettings.blocksBetweenPauses = previousSettings.third
        }
    }

    fun `test no pause reminder appears when reminders are off`() {
        val shownNotifications = collectNotifications()
        myFixture.configureByText("NoPause.txt", documentText)
        val document = myFixture.editor.document

        repeat(5) { reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd)) }

        assertEquals(0, shownNotifications.size)
    }

    fun `test the reviewed blocks list each file with its reviewed line range`() {
        openDocumentWithLinesTwoToFourMarked()

        val reviewedBlocks = reviewMarks().reviewedBlocks()

        val fileBlocks = reviewedBlocks.single()
        assertEquals("Example.txt", fileBlocks.fileName)
        assertEquals(listOf(Triple(2, 4, "line two")), fileBlocks.rows.map { Triple(it.firstLine, it.lastLine, it.preview) })
    }

    fun `test the blocks to review list the changed lines first and then the unread blocks`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        edit { document.insertString(lineThreeStart + 5, "X") }

        val fileBlocks = reviewMarks().blocksToReview(myFixture.file.virtualFile.url).single()

        assertEquals(
            listOf(BlockKind.CHANGED to 3, BlockKind.UNREAD to 1, BlockKind.UNREAD to 5),
            fileBlocks.rows.map { it.kind to it.firstLine },
        )
    }

    fun `test clearing a reviewed block removes the marks inside it`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        val row = reviewMarks().reviewedBlocks().single().rows.single()

        reviewMarks().clearReviewedBlock(myFixture.file.virtualFile.url, TextRange(row.startOffset, row.endOffset))

        assertEquals(emptyList<Triple<MarkState, Int, Int>>(), marks(document))
        assertEquals(emptyList<FileBlocks>(), reviewMarks().reviewedBlocks())
    }

    fun `test clearing all marks keeps the notes`() {
        val document = openDocumentWithLinesTwoToFourMarked()
        reviewMarks().setNoteAt(document, lineThreeStart, "ask Sam")

        reviewMarks().clearAllMarks()

        assertEquals(emptyList<Triple<MarkState, Int, Int>>(), marks(document))
        assertEquals(listOf("ask Sam"), reviewMarks().allNotes().map { it.text })
    }

    fun `test marking the declaration around the caret finds nothing in plain text`() {
        myFixture.configureByText("Plain.txt", documentText)
        val range = findEnclosingDeclarationRange(myFixture.file, myFixture.editor.document, 3)
        assertNull(range)
    }
}
