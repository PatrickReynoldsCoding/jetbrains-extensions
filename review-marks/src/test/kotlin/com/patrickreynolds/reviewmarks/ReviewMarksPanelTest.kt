package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
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

    private fun isVisibleInPanel(button: JButton) = button.isVisible && button.parent.isVisible

    private fun fileNodeCount(tree: Tree) = (tree.model.root as DefaultMutableTreeNode).childCount

    fun `test the panel has the four tabs and counts the notes in the notes tab title`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().setNoteAt(myFixture.editor.document, 0, NoteDetails("ask Sam", floating = false, kind = NoteKind.PR_COMMENT))

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

    fun `test the pomodoro check box shows the saved reminder setting`() {
        val pauseSettings = service<ReviewMarksSettings>().state
        val previouslyOn = pauseSettings.pauseRemindersOn
        try {
            pauseSettings.pauseRemindersOn = true
            val panel = openPanelWithLinesTwoToFourToReview()

            val pauseCheckBox = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first { it.text == "Pomodoro breaks" }

            assertTrue(pauseCheckBox.isSelected)
        } finally {
            pauseSettings.pauseRemindersOn = previouslyOn
        }
    }

    fun `test the post review and end PR buttons only show while a pull request is being reviewed`() {
        val file = myFixture.configureByText("Example.txt", documentText).virtualFile
        val panel = ReviewMarksPanel(project, testRootDisposable)
        val postReviewButton = buttonsOf(panel).first { it.text == "Post review…" }
        assertFalse(isVisibleInPanel(postReviewButton))

        reviewMarks().startPullRequestReview(
            PullRequestSession(number = 7, title = "Fix retries", owner = "acme", repository = "widgets", repositoryRoot = "/src", accountName = "octo"),
            mapOf(file to listOf(LineRange(1, 1))),
        )
        panel.refresh()
        assertTrue(isVisibleInPanel(postReviewButton))
        assertTrue(isVisibleInPanel(buttonsOf(panel).first { it.text == "End PR" }))

        reviewMarks().endPullRequestReview()
        panel.refresh()
        assertFalse(isVisibleInPanel(postReviewButton))
    }

    fun `test the toolbar has the daily actions and the review from git menu with its four actions`() {
        val toolbarActionIds = flattenedActionIds(ActionManager.getInstance().getAction("ReviewMarks.Toolbar") as ActionGroup)

        assertEquals(
            listOf(
                "ReviewMarks.MarkAsToReview",
                "ReviewMarks.MarkSelectionAsReviewed",
                "ReviewMarks.MarkAndGoToNext",
                "ReviewMarks.ClearReviewedMark",
                "ReviewMarks.GoToNextRunToReview",
                "ReviewMarks.GoBackToLastStop",
                "ReviewMarks.AddOrEditNote",
                "ReviewMarks.FromGitMenu",
            ),
            toolbarActionIds,
        )
        val fromGitActionIds = flattenedActionIds(ActionManager.getInstance().getAction("ReviewMarks.FromGitMenu") as ActionGroup)
        assertEquals(
            listOf(
                "ReviewMarks.MarkBranchChanges",
                "ReviewMarks.MarkUncommittedChanges",
                "ReviewMarks.ReviewBranchChanges",
                "ReviewMarks.StartPullRequestReview",
            ),
            fromGitActionIds,
        )
    }

    fun `test every toolbar action has an icon because the toolbar shows icons only`() {
        val toolbar = ActionManager.getInstance().getAction("ReviewMarks.Toolbar") as ActionGroup
        val actionsWithoutIcon = flattenedActionIds(toolbar)
            .filter { ActionManager.getInstance().getAction(it).templatePresentation.icon == null }

        assertEquals(emptyList<String>(), actionsWithoutIcon)
    }

    fun `test the panel shows the toolbar`() {
        val panel = openPanelWithLinesTwoToFourToReview()

        assertTrue(UIUtil.findComponentsOfType(panel, ActionToolbarImpl::class.java).isNotEmpty())
    }

    private fun flattenedActionIds(group: ActionGroup): List<String> =
        group.getChildren(null).filter { it !is Separator }.map { ActionManager.getInstance().getId(it)!! }
}
