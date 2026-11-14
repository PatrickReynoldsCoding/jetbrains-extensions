package com.patrickreynolds.reviewmarks

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewMarksServiceTest : BasePlatformTestCase() {

    private val documentText = "line one\nline two\nline three\nline four\nline five\n"
    private val lineTwoStart = documentText.indexOf("line two")
    private val lineThreeStart = documentText.indexOf("line three")
    private val lineThreeEnd = lineThreeStart + "line three".length
    private val lineFourEnd = documentText.indexOf("line four") + "line four".length

    private fun reviewMarks() = project.service<ReviewMarksService>()

    private fun openDocumentWithLinesTwoToFourMarked(): Document {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().markRangeAsReviewed(document, TextRange(lineTwoStart, lineFourEnd))
        return document
    }

    private fun marks(document: Document) = ReviewedMarkHighlighters.inDocument(project, document)
        .map { Triple(it.state, it.range.startOffset, it.range.endOffset) }
        .sortedBy { it.second }

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

    fun `test marking the declaration around the caret finds nothing in plain text`() {
        myFixture.configureByText("Plain.txt", documentText)
        val range = findEnclosingDeclarationRange(myFixture.file, myFixture.editor.document, 3)
        assertNull(range)
    }
}
