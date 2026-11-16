package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Separator
import java.io.File
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
        service<ReviewMarksSettings>().loadState(ReviewMarksSettingsState())
    }

    private fun openPanelWithLinesTwoToFourToReview(): ReviewMarksPanel {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().markRangeToReview(myFixture.editor.document, TextRange(lineTwoStart, lineFourEnd))
        return ReviewMarksPanel(project, testRootDisposable)
    }

    private fun tabs(panel: ReviewMarksPanel) = UIUtil.findComponentOfType(panel, JBTabbedPane::class.java)!!

    private fun buttonsOf(panel: ReviewMarksPanel) = UIUtil.findComponentsOfType(panel, JButton::class.java)

    private fun isVisibleInPanel(button: JButton) = button.isVisible && button.parent.isVisible

    private fun itemsIn(tree: Tree): List<Any> =
        (tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().toList()
            .mapNotNull { (it as DefaultMutableTreeNode).userObject }

    private fun fileNodeCount(tree: Tree) = itemsIn(tree).filterIsInstance<FileRow>().size

    private fun blocksIn(tree: Tree) = itemsIn(tree).filterIsInstance<BlockOfFile>()

    private fun selectAllBlocks(tree: Tree) {
        val blockPaths = (tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().toList()
            .map { it as DefaultMutableTreeNode }
            .filter { it.userObject is BlockOfFile }
            .map { TreeUtil.getPathFromRoot(it) }
        tree.selectionPaths = blockPaths.toTypedArray()
    }

    fun `test the panel has the five tabs and counts the notes and focus blocks in the titles`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().addNote(myFixture.editor.document, TextRange(0, "line one".length), NoteDetails("ask Sam", floating = false, kind = NoteKind.PR_COMMENT))

        val panel = ReviewMarksPanel(project, testRootDisposable)

        val tabTitles = (0 until tabs(panel).tabCount).map { tabs(panel).getTitleAt(it) }
        assertEquals(listOf("To review", "Reviewed", "Notes (1)", "Current focus (0)", "Today"), tabTitles)
    }

    fun `test the to review tab lists only the code marked to review`() {
        val panel = openPanelWithLinesTwoToFourToReview()

        val toReviewTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first()

        assertEquals(1, fileNodeCount(toReviewTree))
        assertEquals(listOf(BlockKind.TO_REVIEW), blocksIn(toReviewTree).map { it.row.kind })
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

        val reviewedTree = UIUtil.findComponentsOfType(panel, Tree::class.java)[1]
        assertEquals(1, fileNodeCount(reviewedTree))
        val block = blocksIn(reviewedTree).single()
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

    fun `test start reviewing shows until the pomodoro starts, then pause and finish show`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val timer = project.service<PomodoroTimer>()
        val startButton = buttonsOf(panel).first { it.text == "Start reviewing!" }
        val pauseButton = buttonsOf(panel).first { it.text == "Pause" }
        val finishButton = buttonsOf(panel).first { it.text == "Finish" }
        try {
            assertTrue(startButton.isVisible)
            assertFalse(pauseButton.isVisible || finishButton.isVisible)

            startButton.doClick()
            panel.refresh()
            assertFalse(startButton.isVisible)
            assertTrue(pauseButton.isVisible && finishButton.isVisible)

            pauseButton.doClick()
            panel.refresh()
            assertEquals("Resume", pauseButton.text)
            assertTrue(timer.pomodoroState is PomodoroState.Paused)

            finishButton.doClick()
            panel.refresh()
            assertTrue(startButton.isVisible)
            assertNull(timer.pomodoroState)
        } finally {
            timer.finish(0)
        }
    }

    fun `test start break shows when a pomodoro waits for its break, then finish break shows during it`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val timer = project.service<PomodoroTimer>()
        val startBreakButton = buttonsOf(panel).first { it.text == "Start break" }
        val finishBreakButton = buttonsOf(panel).first { it.text == "Finish break" }
        try {
            timer.start(0)
            timer.checkNow(Long.MAX_VALUE / 2)
            panel.refresh()
            assertEquals(PomodoroState.AwaitingBreak, timer.pomodoroState)
            assertTrue(startBreakButton.isVisible)
            assertFalse(finishBreakButton.isVisible)

            startBreakButton.doClick()
            panel.refresh()
            assertEquals(PomodoroPhase.ON_BREAK, timer.pomodoroState?.phase)
            assertFalse(startBreakButton.isVisible)
            assertTrue(finishBreakButton.isVisible)

            finishBreakButton.doClick()
            panel.refresh()
            assertEquals(PomodoroPhase.WORKING, timer.pomodoroState?.phase)
            assertFalse(startBreakButton.isVisible || finishBreakButton.isVisible)
        } finally {
            timer.finish(0)
        }
    }

    private fun openPanelWithTwoBlocksToReview(): ReviewMarksPanel {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        reviewMarks().markRangeToReview(document, TextRange(0, "line one".length))
        reviewMarks().markRangeToReview(document, TextRange(lineFourEnd - "line four".length, lineFourEnd))
        return ReviewMarksPanel(project, testRootDisposable)
    }

    private fun blockTrees(panel: ReviewMarksPanel) = UIUtil.findComponentsOfType(panel, CheckboxTree::class.java)

    private fun nodesOf(tree: Tree): List<DefaultMutableTreeNode> =
        (tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().toList().map { it as DefaultMutableTreeNode }

    private fun tick(tree: CheckboxTree, node: DefaultMutableTreeNode) = tree.setNodeState(node as CheckedTreeNode, true)

    private fun buttonStartingWith(panel: ReviewMarksPanel, label: String) = buttonsOf(panel).first { it.text.startsWith(label) && it.isEnabled }

    fun `test ticking blocks and pressing mark reviewed marks just those blocks as reviewed`() {
        val panel = openPanelWithTwoBlocksToReview()
        val toReviewTree = blockTrees(panel).first()
        val firstBlockNode = nodesOf(toReviewTree).first { it.userObject is BlockOfFile }

        tick(toReviewTree, firstBlockNode)
        val markReviewedButton = buttonStartingWith(panel, "Mark reviewed")
        assertEquals("Mark reviewed (1)", markReviewedButton.text)
        markReviewedButton.doClick()

        assertEquals(1, blocksIn(toReviewTree).size)
        assertEquals(1, reviewMarks().reviewedBlocks().single().rows.size)
    }

    fun `test ticking a file ticks all its blocks so remove clears every mark in it`() {
        val panel = openPanelWithTwoBlocksToReview()
        val toReviewTree = blockTrees(panel).first()
        val fileNode = nodesOf(toReviewTree).first { it.userObject is FileRow }

        tick(toReviewTree, fileNode)
        val removeButton = buttonStartingWith(panel, "Remove")
        assertEquals("Remove (2)", removeButton.text)
        removeButton.doClick()

        assertEquals(emptyList<BlockOfFile>(), blocksIn(toReviewTree))
        assertEquals("Cleared 2 blocks", reviewMarks().undoDescription)
    }

    fun `test selecting a folder row without ticking acts on every block inside it`() {
        val panel = openPanelWithTwoBlocksToReview()
        val toReviewTree = blockTrees(panel).first()
        val folderOrFileNode = nodesOf(toReviewTree).last { it.userObject is FolderRow || it.userObject is FileRow }

        toReviewTree.selectionPath = TreeUtil.getPathFromRoot(folderOrFileNode)
        buttonStartingWith(panel, "Mark reviewed").doClick()

        assertEquals(emptyList<BlockOfFile>(), blocksIn(toReviewTree))
        assertEquals(2, reviewMarks().reviewedBlocks().single().rows.size)
    }

    fun `test the reviewed tab can send ticked blocks back to review and undo brings them back`() {
        myFixture.configureByText("Example.txt", documentText)
        reviewMarks().markRangeAsReviewed(myFixture.editor.document, TextRange(lineTwoStart, lineFourEnd))
        val panel = ReviewMarksPanel(project, testRootDisposable)
        tabs(panel).selectedIndex = 1
        val reviewedTree = blockTrees(panel)[1]

        tick(reviewedTree, nodesOf(reviewedTree).first { it.userObject is BlockOfFile })
        buttonStartingWith(panel, "Mark to review").doClick()

        assertEquals(emptyList<FileBlocks>(), reviewMarks().reviewedBlocks())
        assertEquals("Marked a block to review", reviewMarks().undoDescription)
        reviewMarks().undoLastClear()
        assertEquals(1, reviewMarks().reviewedBlocks().size)
    }

    fun `test the right click menu offers open, mark reviewed, remove and tick all for the to review tab`() {
        val panel = openPanelWithTwoBlocksToReview()
        val toReviewTree = blockTrees(panel).first()
        toReviewTree.selectionPath = TreeUtil.getPathFromRoot(nodesOf(toReviewTree).first { it.userObject is BlockOfFile })

        val menuLabels = panel.blockMenuItems(toReviewTree, BlockTab.TO_REVIEW).map { it.text }

        assertEquals(listOf("Open", "Mark reviewed: 1 block", "Remove: 1 block", "Add to focus: 1 block", "Tick all", "Untick all"), menuLabels)
    }

    fun `test the notes tab groups the notes under a header for each kind`() {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        listOf(
            NoteDetails("ask Sam", floating = false, kind = NoteKind.NOTE),
            NoteDetails("Why retry?", floating = false, kind = NoteKind.PR_COMMENT),
            NoteDetails("Rename this", floating = true, kind = NoteKind.AGENT_NOTE),
            NoteDetails("Add a test", floating = true, kind = NoteKind.AGENT_NOTE),
        ).forEach { details -> reviewMarks().addNote(document, reviewMarks().noteRangeFor(document, 0, 0)!!, details) }
        val panel = ReviewMarksPanel(project, testRootDisposable)

        tabs(panel).selectedIndex = 2

        val notesTree = UIUtil.findComponentsOfType(panel, Tree::class.java)[2]
        val headers = itemsIn(notesTree).filterIsInstance<NoteKindHeader>()
        assertEquals(
            listOf(NoteKindHeader(NoteKind.NOTE, 1), NoteKindHeader(NoteKind.PR_COMMENT, 1), NoteKindHeader(NoteKind.AGENT_NOTE, 2)),
            headers.sortedBy { it.kind },
        )
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

    fun `test the panel has a labelled button for each daily action and each kind of comment`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val buttonLabels = buttonsOf(panel).map { it.text }

        assertTrue(
            buttonLabels.containsAll(
                listOf(
                    "To review", "Reviewed", "Reviewed & next", "Clear mark",
                    "Next to review", "Where I stopped",
                    "Note", "PR comment", "Agent note",
                    "Review from git ▾",
                ),
            ),
        )
    }

    fun `test the review from git menu has its four actions`() {
        val fromGitMenu = ActionManager.getInstance().getAction("ReviewMarks.FromGitMenu") as ActionGroup
        val actionIds = fromGitMenu.getChildren(null).filter { it !is Separator }.map { ActionManager.getInstance().getId(it)!! }

        assertEquals(
            listOf(
                "ReviewMarks.MarkBranchChanges",
                "ReviewMarks.MarkUncommittedChanges",
                "ReviewMarks.ReviewBranchChanges",
                "ReviewMarks.StartPullRequestReview",
            ),
            actionIds,
        )
    }

    fun `test the notes tab shows each note's lines and Claude's reply, and clears the agent notes Claude resolved`() {
        myFixture.configureByText("Example.txt", documentText)
        val document = myFixture.editor.document
        val linesTwoToFour = reviewMarks().noteRangeFor(document, lineTwoStart, lineFourEnd)!!
        reviewMarks().addNote(document, linesTwoToFour, NoteDetails("Rename this", floating = false, kind = NoteKind.AGENT_NOTE))
        val noteId = reviewMarks().allNotes().single().id
        val repliesFile = File(project.basePath!!, AGENT_REPLIES_FILE_PATH)
        repliesFile.parentFile.mkdirs()
        repliesFile.writeText("""[{"noteId": "$noteId", "reply": "Renamed it to retryCount.", "resolved": true}]""")
        try {
            val panel = ReviewMarksPanel(project, testRootDisposable)
            tabs(panel).selectedIndex = 2
            val notesTree = UIUtil.findComponentsOfType(panel, Tree::class.java)[2]

            assertEquals("2-4", itemsIn(notesTree).filterIsInstance<NoteRow>().single().linesText)
            assertEquals("Renamed it to retryCount.", itemsIn(notesTree).filterIsInstance<AgentReplyRow>().single().agentReply.reply)

            buttonsOf(panel).first { it.text == "Clear resolved agent notes (1)" }.doClick()

            assertEquals(emptyList<StoredNote>(), reviewMarks().allNotes())
        } finally {
            repliesFile.delete()
        }
    }

    private fun selectedItemOf(tree: Tree) = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject

    fun `test the sidebar highlights the block under the caret when the editor is clicked`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val toReviewTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first()
        assertTrue(selectedItemOf(toReviewTree) is FileRow)

        myFixture.editor.caretModel.moveToOffset(lineTwoStart + 2)
        panel.syncSidebarWithEditor()

        assertSame(blocksIn(toReviewTree).single(), selectedItemOf(toReviewTree))
    }

    fun `test the sidebar highlights the open file when the caret is outside every block`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val toReviewTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first()

        myFixture.editor.caretModel.moveToOffset(0)
        panel.syncSidebarWithEditor()

        assertEquals("Example.txt", (selectedItemOf(toReviewTree) as FileRow).name)
    }

    fun `test the sidebar does not follow the editor when the setting is off`() {
        val settingsState = service<ReviewMarksSettings>().state
        settingsState.syncSidebarWithEditor = false
        try {
            val panel = openPanelWithLinesTwoToFourToReview()
            val toReviewTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first()

            myFixture.editor.caretModel.moveToOffset(lineTwoStart + 2)
            panel.syncSidebarWithEditor()

            assertNull(selectedItemOf(toReviewTree))
        } finally {
            settingsState.syncSidebarWithEditor = true
        }
    }

    private fun tabTitles(panel: ReviewMarksPanel) = (0 until tabs(panel).tabCount).map { tabs(panel).getTitleAt(it) }

    private fun menuItemNamed(panel: ReviewMarksPanel, tab: SidebarTab, label: String) =
        panel.tabMenuItems(tab).first { it.text == label }

    fun `test hiding a tab from its right click menu removes it from the sidebar`() {
        val panel = ReviewMarksPanel(project, testRootDisposable)

        menuItemNamed(panel, SidebarTab.TODAY, "Hide this tab").doClick()

        assertEquals(listOf("To review", "Reviewed", "Notes (0)", "Current focus (0)"), tabTitles(panel))
    }

    fun `test a hidden tab can be shown again from the menu of another tab`() {
        val panel = ReviewMarksPanel(project, testRootDisposable)
        menuItemNamed(panel, SidebarTab.TODAY, "Hide this tab").doClick()

        val showMenu = menuItemNamed(panel, SidebarTab.TO_REVIEW, "Show hidden tab") as javax.swing.JMenu
        showMenu.getItem(0).doClick()

        assertEquals(listOf("To review", "Reviewed", "Notes (0)", "Current focus (0)", "Today"), tabTitles(panel))
    }

    fun `test a tab moves right and the new order is kept in the settings`() {
        val panel = ReviewMarksPanel(project, testRootDisposable)

        menuItemNamed(panel, SidebarTab.TO_REVIEW, "Move right").doClick()

        assertEquals(listOf("Reviewed", "To review", "Notes (0)", "Current focus (0)", "Today"), tabTitles(panel))
        assertEquals(
            listOf("REVIEWED", "TO_REVIEW", "NOTES", "CURRENT_FOCUS", "TODAY"),
            service<ReviewMarksSettings>().state.sidebarTabOrder,
        )
    }

    fun `test the last visible tab cannot be hidden`() {
        service<ReviewMarksSettings>().state.hiddenSidebarTabs = mutableListOf("REVIEWED", "NOTES", "CURRENT_FOCUS", "TODAY")
        val panel = ReviewMarksPanel(project, testRootDisposable)

        assertFalse(menuItemNamed(panel, SidebarTab.TO_REVIEW, "Hide this tab").isEnabled)
    }

    fun `test adding a block to focus lists it in the current focus tab and highlights it in the editor`() {
        val panel = openPanelWithLinesTwoToFourToReview()
        val toReviewTree = blockTrees(panel).first()
        selectAllBlocks(toReviewTree)

        panel.blockMenuItems(toReviewTree, BlockTab.TO_REVIEW).first { it.text.startsWith("Add to focus") }.doClick()

        tabs(panel).selectedIndex = shownTabIndex(panel, "Current focus")
        val focusTree = UIUtil.findComponentsOfType(panel, Tree::class.java).first { itemsIn(it).any { item -> item is FocusRow } }
        assertEquals(listOf("Example.txt"), itemsIn(focusTree).filterIsInstance<FocusRow>().map { it.fileName })
        assertEquals(1, FocusHighlighters.inDocument(project, myFixture.editor.document).size)
    }

    private fun shownTabIndex(panel: ReviewMarksPanel, titleStart: String) =
        tabTitles(panel).indexOfFirst { it.startsWith(titleStart) }

    fun `test the setting hides the up, down, nest and un-nest buttons of the focus tab`() {
        service<ReviewMarksSettings>().state.showFocusMoveButtons = false
        val panel = ReviewMarksPanel(project, testRootDisposable)

        val visibleLabels = buttonsOf(panel).filter { it.isVisible }.map { it.text }

        assertTrue(visibleLabels.containsAll(listOf("Reviewed", "Remove", "Clear all")))
        assertTrue(listOf("Up", "Down", "Nest", "Un-nest").none { it in visibleLabels })
    }

    fun `test the right click menu hides the four move buttons and then offers to show them again`() {
        val panel = ReviewMarksPanel(project, testRootDisposable)

        panel.focusMoveButtonsMenuItem().doClick()

        assertFalse(service<ReviewMarksSettings>().state.showFocusMoveButtons)
        assertTrue(buttonsOf(panel).filter { it.text in listOf("Up", "Down", "Nest", "Un-nest") }.none { it.isVisible })
        assertEquals("Show Up, Down, Nest and Un-nest buttons", panel.focusMoveButtonsMenuItem().text)

        panel.focusMoveButtonsMenuItem().doClick()

        assertTrue(buttonsOf(panel).filter { it.text in listOf("Up", "Down", "Nest", "Un-nest") }.all { it.isVisible })
    }
}
