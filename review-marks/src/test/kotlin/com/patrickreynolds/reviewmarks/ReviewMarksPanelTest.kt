package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.tree.DefaultMutableTreeNode

class ReviewMarksPanelTest : BasePlatformTestCase() {

    private val documentText = "line one\nline two\nline three\nline four\nline five\n"
    private val lineTwoStart = documentText.indexOf("line two")
    private val lineFourEnd = documentText.indexOf("line four") + "line four".length

    private fun reviewMarks() = project.service<ReviewMarksService>()

    override fun setUp() {
        super.setUp()
        reviewMarks().loadState(ReviewMarksState())
    }

    private fun openPanelWithLinesTwoToFourToReview(): ReviewMarksPanel {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().markRangeToReview(myFixture.editor.document, TextRange(lineTwoStart, lineFourEnd))
        return ReviewMarksPanel(project, testRootDisposable)
    }

    private fun tabs(panel: ReviewMarksPanel) = UIUtil.findComponentOfType(panel, JBTabbedPane::class.java)!!

    private fun buttonsOf(panel: ReviewMarksPanel) = UIUtil.findComponentsOfType(panel, JButton::class.java)

    private fun fileNodeCount(tree: Tree) = (tree.model.root as DefaultMutableTreeNode).childCount

    fun `test the panel has the four tabs and counts the notes in the notes tab title`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().setNoteAt(myFixture.editor.document, 0, NoteDetails("ask Sam", floating = false, includeInPullRequest = true))

        val panel = ReviewMarksPanel(project, testRootDisposable)

        val tabTitles = (0 until tabs(panel).tabCount).map { tabs(panel).getTitleAt(it) }
        assertEquals(listOf("To review", "Reviewed", "Notes (1)", "Today"), tabTitles)
    }

    fun `test the to review tab lists only the code marked to review`() {
        val panel = openPanelWithLinesTwoToFourToReview()

        val toReviewTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first()

        assertEquals(1, fileNodeCount(toReviewTree))
        val fileNode = (toReviewTree.model.root as DefaultMutableTreeNode).getChildAt(0) as DefaultMutableTreeNode
        assertEquals(1, fileNode.childCount)
        val block = (fileNode.getChildAt(0) as DefaultMutableTreeNode).userObject as BlockOfFile
        assertEquals(BlockKind.TO_REVIEW, block.row.kind)
    }

    fun `test the to review tab is empty when nothing is marked even if the file has code`() {
        myFixture.configureByText("Example.txt", documentText)

        val panel = ReviewMarksPanel(project, testRootDisposable)

        assertEquals(0, fileNodeCount(UIUtil.findComponentsOfType(panel, Tree::class.java).first()))
    }

    fun `test the reviewed tab lists the reviewed blocks of each file`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().markRangeAsReviewed(myFixture.editor.document, TextRange(lineTwoStart, lineFourEnd))
        val panel = ReviewMarksPanel(project, testRootDisposable)

        tabs(panel).selectedIndex = 1

        val reviewedTree = UIUtil.findComponentsOfType(panel, Tree::class.java).last()
        assertEquals(1, fileNodeCount(reviewedTree))
        val fileNode = (reviewedTree.model.root as DefaultMutableTreeNode).getChildAt(0) as DefaultMutableTreeNode
        val block = (fileNode.getChildAt(0) as DefaultMutableTreeNode).userObject as BlockOfFile
        assertEquals(2, block.row.firstLine)
        assertEquals(4, block.row.lastLine)
    }

    fun `test the fold check box follows changes made elsewhere and there is no fade check box`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val checkBoxes = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java)
        val foldCheckBox = checkBoxes.first { it.text == "Fold reviewed" }
        assertFalse(foldCheckBox.isSelected)

        reviewMarks().changeFoldMode(true)
        panel.refresh()

        assertTrue(foldCheckBox.isSelected)
        assertEquals(emptyList<String>(), checkBoxes.map { it.text }.filter { it.contains("Fade") })
    }

    fun `test the pause check box shows the saved reminder setting`() {
        val pauseSettings = service<ReviewMarksSettings>().state
        val previouslyOn = pauseSettings.pauseRemindersOn
        try {
            pauseSettings.pauseRemindersOn = true
            val panel = openPanelWithLinesTwoToFourToReview()

            val pauseCheckBox = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first { it.text == "Remind me to pause" }

            assertTrue(pauseCheckBox.isSelected)
        } finally {
            pauseSettings.pauseRemindersOn = previouslyOn
        }
    }

    fun `test the post review and end PR buttons only show while a pull request is being reviewed`() {
        val file = myFixture.configureByText("Example.txt", documentText).virtualFile
        val panel = ReviewMarksPanel(project, testRootDisposable)
        val postReviewButton = buttonsOf(panel).first { it.text == "Post review…" }
        assertFalse(postReviewButton.isVisible)

        reviewMarks().startPullRequestReview(
            PullRequestSession(number = 7, title = "Fix retries", owner = "acme", repository = "widgets", repositoryRoot = "/src", accountName = "octo"),
            mapOf(file to listOf(LineRange(1, 1))),
        )
        panel.refresh()
        assertTrue(postReviewButton.isVisible)
        assertTrue(buttonsOf(panel).first { it.text == "End PR" }.isVisible)

        reviewMarks().endPullRequestReview()
        panel.refresh()
        assertFalse(postReviewButton.isVisible)
    }

    fun `test the panel has the branch and pull request buttons`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val buttonLabels = buttonsOf(panel).map { it.text }
        assertTrue(buttonLabels.containsAll(listOf("To review", "Reviewed", "Reviewed & next", "Clear", "Branch…", "PR review…")))
    }
}
