package com.patrickreynolds.reviewmarks

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewMarksServiceTest : BasePlatformTestCase() {

    private val documentText = "line one\nline two\nline three\nline four\nline five\n"
    private val lineTwoStart = documentText.indexOf("line two")
    private val lineThreeStart = documentText.indexOf("line three")
    private val lineThreeEnd = lineThreeStart + "line three".length
    private val lineFourEnd = documentText.indexOf("line four") + "line four".length

    private val plainNote = NoteDetails("ask Sam", floating = false, includeInPullRequest = true)

    private fun reviewMarks() = project.service<ReviewMarksService>()

    override fun setUp() {
        super.setUp()
        reviewMarks().loadState(ReviewMarksState())
    }

    private fun openDocumentWithLinesTwoToFourReviewed(): Document {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineFourEnd))
        return document
    }

    private fun openDocumentWithLinesTwoToFourToReview(): Document {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().markRangeToReview(document, TextRange(lineTwoStart, lineFourEnd))
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

    private fun floatingNoteInlayCount() =
        myFixture.editor.inlayModel.getBlockElementsInRange(0, myFixture.editor.document.textLength, EditorCustomElementRenderer::class.java).size

    private fun pullRequestSession() = PullRequestSession(
        number = 7,
        title = "Fix retries",
        htmlUrl = "https://github.com/acme/widgets/pull/7",
        host = "github.com",
        apiUrl = "https://api.github.com",
        owner = "acme",
        repository = "widgets",
        headSha = "abc123",
        repositoryRoot = "/src",
        accountName = "octo",
    )

    fun `test a block marked as reviewed is highlighted as reviewed`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        assertEquals(listOf(Triple(MarkState.REVIEWED, lineTwoStart, lineFourEnd)), marks(document))
    }

    fun `test a block marked to review is highlighted and listed in the blocks to review`() {
        val document = openDocumentWithLinesTwoToFourToReview()

        assertEquals(listOf(Triple(MarkState.TO_REVIEW, lineTwoStart, lineFourEnd)), marks(document))
        val fileBlocks = reviewMarks().blocksToReview().single()
        assertEquals(listOf(Triple(BlockKind.TO_REVIEW, 2, 4)), fileBlocks.rows.map { Triple(it.kind, it.firstLine, it.lastLine) })
    }

    fun `test code that is not marked is not listed to review`() {
        myFixture.configureByText("Example.txt", documentText)
        assertEquals(emptyList<FileBlocks>(), reviewMarks().blocksToReview())
    }

    fun `test marking to-review code as reviewed turns that part reviewed and leaves the rest to review`() {
        val document = openDocumentWithLinesTwoToFourToReview()

        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))

        assertEquals(
            listOf(
                Triple(MarkState.REVIEWED, lineTwoStart, lineThreeEnd),
                Triple(MarkState.TO_REVIEW, lineThreeEnd, lineFourEnd),
            ),
            marks(document),
        )
    }

    fun `test marking reviewed code to review turns that part back to to review`() {
        val document = openDocumentWithLinesTwoToFourReviewed()

        reviewMarks().markRangeToReview(document, TextRange(lineThreeStart, lineFourEnd))

        assertEquals(
            listOf(
                Triple(MarkState.REVIEWED, lineTwoStart, lineThreeStart),
                Triple(MarkState.TO_REVIEW, lineThreeStart, lineFourEnd),
            ),
            marks(document),
        )
    }

    fun `test progress counts reviewed lines out of the lines that are marked`() {
        val document = openDocumentWithLinesTwoToFourToReview()
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))

        assertEquals(
            ReviewProgress(reviewedLineCount = 2, markedLineCount = 3, changedLineCount = 0),
            reviewMarks().progressOf(document),
        )
    }

    fun `test progress is empty before anything is marked`() {
        myFixture.configureByText("Example.txt", documentText)
        assertEquals("", reviewMarks().progressOf(myFixture.editor.document).statusText())
    }

    fun `test editing one line turns only that line to changed and keeps the lines around it reviewed`() {
        val document = openDocumentWithLinesTwoToFourReviewed()

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
        val document = openDocumentWithLinesTwoToFourReviewed()
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
        val document = openDocumentWithLinesTwoToFourReviewed()
        edit { document.insertString(lineThreeStart + 5, "X") }

        reviewMarks().markRangeAsReviewed(document, TextRange(lineThreeStart, lineThreeEnd + 1))

        assertEquals(setOf(MarkState.REVIEWED), marks(document).map { it.first }.toSet())
    }

    fun `test editing inside code marked to review keeps it to review`() {
        val document = openDocumentWithLinesTwoToFourToReview()

        edit { document.insertString(lineThreeStart + 5, "X") }

        assertEquals(listOf(Triple(MarkState.TO_REVIEW, lineTwoStart, lineFourEnd + 1)), marks(document))
    }

    fun `test editing above the marked block keeps the mark and moves it with the text`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        edit { document.insertString(0, "new line\n") }
        val shift = "new line\n".length
        assertEquals(
            listOf(Triple(MarkState.REVIEWED, lineTwoStart + shift, lineFourEnd + shift)),
            marks(document),
        )
    }

    fun `test editing below the marked block keeps the mark`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        edit { document.insertString(document.textLength, "line six\n") }
        assertEquals(listOf(Triple(MarkState.REVIEWED, lineTwoStart, lineFourEnd)), marks(document))
    }

    fun `test typing on an empty line inside a reviewed block marks that typed text as changed`() {
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
        val document = openDocumentWithLinesTwoToFourReviewed()
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

    fun `test the saved state holds the marked text, where it starts and the kind of mark`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        edit { document.insertString(lineThreeStart + 5, "X") }

        val savedMarks = reviewMarks().state.marks.sortedBy { it.startOffset }

        assertEquals(listOf("REVIEWED", "CHANGED", "REVIEWED"), savedMarks.map { it.state })
        assertEquals("line Xthree", savedMarks[1].markedText)
        assertEquals(lineThreeStart, savedMarks[1].startOffset)
    }

    fun `test a saved mark comes back when the file is opened and its text is unchanged`() {
        val file = myFixture.addFileToProject("Saved.txt", documentText).virtualFile
        reviewMarks().loadState(
            ReviewMarksState(mutableListOf(StoredMark(file.url, lineTwoStart, "line two", "TO_REVIEW"))),
        )

        myFixture.openFileInEditor(file)

        assertEquals(
            listOf(Triple(MarkState.TO_REVIEW, lineTwoStart, lineTwoStart + "line two".length)),
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

    fun `test fold mode folds reviewed lines into one line and unfolds them when turned off`() {
        openDocumentWithLinesTwoToFourReviewed()

        reviewMarks().changeFoldMode(true)

        val foldedRegions = myFixture.editor.foldingModel.allFoldRegions.filter { !it.isExpanded }
        assertEquals(1, foldedRegions.size)
        assertEquals(lineTwoStart, foldedRegions.single().startOffset)
        assertEquals(lineFourEnd, foldedRegions.single().endOffset)
        assertEquals("✓ 3 reviewed lines", foldedRegions.single().placeholderText)

        reviewMarks().changeFoldMode(false)

        assertEquals(0, myFixture.editor.foldingModel.allFoldRegions.size)
    }

    fun `test code marked to review is not folded`() {
        openDocumentWithLinesTwoToFourToReview()

        reviewMarks().changeFoldMode(true)

        assertEquals(0, myFixture.editor.foldingModel.allFoldRegions.size)
    }

    fun `test going back to where I stopped opens the end of the last reviewed line`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
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
        openDocumentWithLinesTwoToFourReviewed()
        reviewMarks().changeFoldMode(true)

        val savedState = reviewMarks().state

        assertTrue(savedState.foldMode)
        assertEquals("line four", savedState.lastStop?.anchorText)
    }

    fun `test going to the next code to review skips reviewed code and goes to code marked to review`() {
        val document = openDocumentWithLinesTwoToFourToReview()
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))
        myFixture.editor.caretModel.moveToOffset(0)

        reviewMarks().goToNextRunToReview(myFixture.editor)

        assertEquals(3, myFixture.editor.caretModel.logicalPosition.line)
    }

    fun `test a note puts a gutter icon on its line and shows the note as the tooltip`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document

        reviewMarks().setNoteAt(document, lineThreeStart + 2, plainNote)

        val note = ReviewNoteHighlighters.inDocument(project, document).single()
        assertEquals("ask Sam", note.text)
        assertEquals("line three", document.getText(note.range))
        assertEquals("ask Sam", note.highlighter.gutterIconRenderer?.tooltipText)
        assertEquals(0, floatingNoteInlayCount())
    }

    fun `test a floating note shows above its line and stops when the option is turned off`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document

        reviewMarks().setNoteAt(document, lineThreeStart, plainNote.copy(floating = true))
        assertEquals(1, floatingNoteInlayCount())

        reviewMarks().setNoteAt(document, lineThreeStart, plainNote.copy(floating = false))
        assertEquals(0, floatingNoteInlayCount())
    }

    fun `test deleting a floating note removes its inlay`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, plainNote.copy(floating = true))

        reviewMarks().setNoteAt(document, lineThreeStart, plainNote.copy(text = "", floating = true))

        assertEquals(0, floatingNoteInlayCount())
    }

    fun `test setting the note again changes its details and an empty note deletes it`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, plainNote.copy(text = "first"))

        reviewMarks().setNoteAt(document, lineThreeStart + 4, plainNote.copy(text = "second"))
        assertEquals(listOf("second"), ReviewNoteHighlighters.inDocument(project, document).map { it.text })
        assertEquals("second", reviewMarks().noteDetailsAt(document, lineThreeStart)?.text)

        reviewMarks().setNoteAt(document, lineThreeStart, plainNote.copy(text = ""))
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
        reviewMarks().setNoteAt(document, lineThreeStart, plainNote)

        edit { document.insertString(0, "new line\n") }

        val note = ReviewNoteHighlighters.inDocument(project, document).single()
        assertEquals("line three", document.getText(note.range))
    }

    fun `test saved state holds the notes with their options and listing them shows the notes of open files`() {
        myFixture.configureByText("Notes.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().setNoteAt(document, lineThreeStart, NoteDetails("ask Sam", floating = true, includeInPullRequest = false))

        val savedNotes = reviewMarks().state.notes
        val listedNotes = reviewMarks().allNotes()

        assertEquals(listOf(StoredNote(myFixture.file.virtualFile.url, lineThreeStart, "line three", "ask Sam", true, false)), savedNotes)
        assertEquals(savedNotes, listedNotes)
    }

    fun `test a saved note comes back with its options when the file is opened and its line is unchanged`() {
        val file = myFixture.addFileToProject("SavedNotes.txt", documentText).virtualFile
        reviewMarks().loadState(
            ReviewMarksState(notes = mutableListOf(StoredNote(file.url, lineThreeStart, "line three", "ask Sam", floating = true, includeInPullRequest = false))),
        )

        myFixture.openFileInEditor(file)

        val note = ReviewNoteHighlighters.inDocument(project, myFixture.editor.document).single()
        assertEquals(NoteDetails("ask Sam", floating = true, includeInPullRequest = false), note.details)
        assertEquals(TextRange(lineThreeStart, lineThreeEnd), note.range)
        assertEquals(1, floatingNoteInlayCount())
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
        reviewMarks().setNoteAt(document, lineThreeStart, plainNote)

        reviewMarks().deleteNote(reviewMarks().allNotes().single())

        assertEquals(emptyList<ReviewNote>(), ReviewNoteHighlighters.inDocument(project, document))
        assertEquals(emptyList<StoredNote>(), reviewMarks().allNotes())
    }

    fun `test today's summary counts each reviewed line once and lists the code still marked to review`() {
        val document = openDocumentWithLinesTwoToFourToReview()
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineThreeEnd))

        val summary = reviewMarks().reviewSummaryToday()

        assertEquals(2, summary.reviewedLineCountToday)
        assertEquals(1, summary.fileCountReviewedToday)
        assertEquals(listOf(1), summary.filesWithLinesLeft.map { it.lineCount })
    }

    fun `test the review log is saved with the state`() {
        openDocumentWithLinesTwoToFourReviewed()
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
        openDocumentWithLinesTwoToFourReviewed()

        val fileBlocks = reviewMarks().reviewedBlocks().single()

        assertEquals("Example.txt", fileBlocks.fileName)
        assertEquals(listOf(Triple(2, 4, "line two")), fileBlocks.rows.map { Triple(it.firstLine, it.lastLine, it.preview) })
    }

    fun `test the blocks to review list the changed lines first and then the code marked to review`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        reviewMarks().markRangeToReview(document, TextRange(0, "line one".length))
        edit { document.insertString(lineThreeStart + 5, "X") }

        val fileBlocks = reviewMarks().blocksToReview().single()

        assertEquals(
            listOf(BlockKind.CHANGED to 3, BlockKind.TO_REVIEW to 1),
            fileBlocks.rows.map { it.kind to it.firstLine },
        )
    }

    fun `test clearing a block removes the marks inside it`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        val row = reviewMarks().reviewedBlocks().single().rows.single()

        reviewMarks().clearMarkedBlock(myFixture.file.virtualFile.url, TextRange(row.startOffset, row.endOffset))

        assertEquals(emptyList<Triple<MarkState, Int, Int>>(), marks(document))
        assertEquals(emptyList<FileBlocks>(), reviewMarks().reviewedBlocks())
    }

    fun `test clearing all marks keeps the notes`() {
        val document = openDocumentWithLinesTwoToFourReviewed()
        reviewMarks().markRangeToReview(document, TextRange(0, "line one".length))
        reviewMarks().setNoteAt(document, lineThreeStart, plainNote)

        reviewMarks().clearAllMarks()

        assertEquals(emptyList<Triple<MarkState, Int, Int>>(), marks(document))
        assertEquals(listOf("ask Sam"), reviewMarks().allNotes().map { it.text })
    }

    fun `test marking the changed lines to review marks only the lines that are not marked yet`() {
        val file = myFixture.configureByText("Example.txt", documentText).virtualFile
        val document = myFixture.editor.document
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineTwoStart + "line two".length))

        val markedFileCount = reviewMarks().markChangedLinesToReview(mapOf(file to listOf(LineRange(1, 2), LineRange(4, 4))))

        assertEquals(1, markedFileCount)
        assertEquals(
            listOf(
                Triple(MarkState.REVIEWED, lineTwoStart, lineTwoStart + "line two".length),
                Triple(MarkState.TO_REVIEW, lineTwoStart + "line two".length + 1, lineThreeEnd),
                Triple(MarkState.TO_REVIEW, documentText.indexOf("line five"), documentText.indexOf("line five") + "line five".length),
            ),
            marks(document),
        )
    }

    fun `test marking changed lines in a file that is not open shows the marks when it is opened`() {
        val file = myFixture.addFileToProject("Closed.txt", documentText).virtualFile

        reviewMarks().markChangedLinesToReview(mapOf(file to listOf(LineRange(1, 2))))

        assertEquals(
            listOf(StoredMark(file.url, lineTwoStart, "line two\nline three", "TO_REVIEW")),
            reviewMarks().state.marks,
        )
    }

    fun `test starting a pull request review marks its changed lines and keeps the session`() {
        val file = myFixture.configureByText("Example.txt", documentText).virtualFile

        val markedFileCount = reviewMarks().startPullRequestReview(pullRequestSession(), mapOf(file to listOf(LineRange(1, 2))))

        assertEquals(1, markedFileCount)
        assertEquals(7, reviewMarks().pullRequest?.number)
        assertEquals(listOf(Triple(MarkState.TO_REVIEW, lineTwoStart, lineThreeEnd)), marks(myFixture.editor.document))
        assertEquals(7, reviewMarks().state.pullRequest?.number)
    }

    fun `test notes become review comments on their line with a path relative to the repository`() {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().startPullRequestReview(pullRequestSession(), emptyMap())
        reviewMarks().setNoteAt(document, lineThreeStart, NoteDetails("Why retry here?", floating = false, includeInPullRequest = true))
        reviewMarks().setNoteAt(document, lineTwoStart, NoteDetails("private reminder", floating = false, includeInPullRequest = false))

        val comments = reviewMarks().pullRequestComments()

        assertEquals(listOf(ReviewComment("Example.txt", 3, "Why retry here?")), comments)
    }

    fun `test there are no review comments when no pull request is being reviewed`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().setNoteAt(myFixture.editor.document, lineThreeStart, plainNote)

        assertEquals(emptyList<ReviewComment>(), reviewMarks().pullRequestComments())
    }

    fun `test ending the pull request review keeps the marks and notes`() {
        val file = myFixture.configureByText("Example.txt", documentText).virtualFile
        val document = myFixture.editor.document
        reviewMarks().startPullRequestReview(pullRequestSession(), mapOf(file to listOf(LineRange(1, 2))))
        reviewMarks().setNoteAt(document, lineThreeStart, plainNote)

        reviewMarks().endPullRequestReview()

        assertNull(reviewMarks().pullRequest)
        assertEquals(1, marks(document).size)
        assertEquals(1, ReviewNoteHighlighters.inDocument(project, document).size)
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

    fun `test marking the declaration around the caret finds nothing in plain text`() {
        myFixture.configureByText("Plain.txt", documentText)
        val range = findEnclosingDeclarationRange(myFixture.file, myFixture.editor.document, 3)
        assertNull(range)
    }
}
