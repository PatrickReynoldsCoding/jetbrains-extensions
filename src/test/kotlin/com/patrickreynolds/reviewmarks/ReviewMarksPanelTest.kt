package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import javax.swing.tree.DefaultMutableTreeNode

class ReviewMarksPanelTest : BasePlatformTestCase() {

    private val documentText = "line one\nline two\nline three\nline four\nline five\n"

    private fun reviewMarks() = project.service<ReviewMarksService>()

    override fun setUp() {
        super.setUp()
        reviewMarks().loadState(ReviewMarksState())
    }

    private fun openPanelWithLinesTwoToFourMarked(): ReviewMarksPanel {
        myFixture.configureByText("Example.txt", documentText)
        val start = documentText.indexOf("line two")
        val end = documentText.indexOf("line four") + "line four".length
        reviewMarks().markRangeAsReviewed(myFixture.editor.document, TextRange(start, end))
        return ReviewMarksPanel(project, testRootDisposable)
    }

    private fun tabs(panel: ReviewMarksPanel) = UIUtil.findComponentOfType(panel, JBTabbedPane::class.java)!!

    private fun fileNodeCount(tree: Tree) = (tree.model.root as DefaultMutableTreeNode).childCount

    fun `test the panel has the four tabs and counts the notes in the notes tab title`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().setNoteAt(myFixture.editor.document, 0, "ask Sam")

        val panel = ReviewMarksPanel(project, testRootDisposable)

        val tabTitles = (0 until tabs(panel).tabCount).map { tabs(panel).getTitleAt(it) }
        assertEquals(listOf("To review", "Reviewed", "Notes (1)", "Today"), tabTitles)
    }

    fun `test the to review tab lists the code that is not reviewed in the open file`() {
        val panel = openPanelWithLinesTwoToFourMarked()

        val toReviewTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first()

        assertEquals(1, fileNodeCount(toReviewTree))
        val fileNode = (toReviewTree.model.root as DefaultMutableTreeNode).getChildAt(0) as DefaultMutableTreeNode
        assertEquals(2, fileNode.childCount)
    }

    fun `test the reviewed tab lists the reviewed blocks of each file`() {
        val panel = openPanelWithLinesTwoToFourMarked()

        tabs(panel).selectedIndex = 1

        val reviewedTree = UIUtil.findComponentsOfType(panel, Tree::class.java).last()
        assertEquals(1, fileNodeCount(reviewedTree))
        val fileNode = (reviewedTree.model.root as DefaultMutableTreeNode).getChildAt(0) as DefaultMutableTreeNode
        val block = (fileNode.getChildAt(0) as DefaultMutableTreeNode).userObject as BlockOfFile
        assertEquals(2, block.row.firstLine)
        assertEquals(4, block.row.lastLine)
    }

    fun `test the check boxes show the focus modes and the branch review and follow changes made elsewhere`() {
        val panel = openPanelWithLinesTwoToFourMarked()
        val foldCheckBox = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first { it.text == "Fold reviewed" }
        assertFalse(foldCheckBox.isSelected)

        reviewMarks().changeFoldMode(true)
        panel.refresh()

        assertTrue(foldCheckBox.isSelected)
    }

    fun `test the pause check box shows the saved reminder setting`() {
        val pauseSettings = service<ReviewMarksSettings>().state
        val previouslyOn = pauseSettings.pauseRemindersOn
        try {
            pauseSettings.pauseRemindersOn = true
            val panel = openPanelWithLinesTwoToFourMarked()

            val pauseCheckBox = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first { it.text == "Remind me to pause" }

            assertTrue(pauseCheckBox.isSelected)
        } finally {
            pauseSettings.pauseRemindersOn = previouslyOn
        }
    }
}
