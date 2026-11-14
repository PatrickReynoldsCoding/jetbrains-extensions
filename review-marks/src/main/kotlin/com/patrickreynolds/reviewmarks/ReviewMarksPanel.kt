package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.ui.tree.TreeModelAdapter
import java.awt.Color
import javax.swing.JMenuItem
import javax.swing.event.TreeModelEvent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.Timer
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeSelectionModel

private const val TO_REVIEW_TAB = 0
private const val REVIEWED_TAB = 1
private const val NOTES_TAB = 2
private const val TODAY_TAB = 3

private const val ACTION_PLACE = "ReviewMarksToolWindow"
private const val FROM_GIT_MENU_ID = "ReviewMarks.FromGitMenu"
private const val POMODORO_CLOCK_MILLISECONDS = 15_000
private const val REMOVE_LABEL = "Remove"
private const val FIRST_TICK_MENU_ITEM = 3

enum class BlockTab(val changeStateLabel: String, val changeStateColor: Color) {
    TO_REVIEW("Mark reviewed", REVIEWED_ACCENT),
    REVIEWED("Mark to review", TO_REVIEW_ACCENT),
}

class ReviewMarksPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : JBPanel<ReviewMarksPanel>(BorderLayout()) {

    private val reviewMarks = project.service<ReviewMarksService>()

    private val foldCheckBox = JBCheckBox("Fold reviewed")
    private val pullRequestLabel = JBLabel()
    private val pullRequestRow = createPullRequestRow()
    private val fileNameLabel = JBLabel()
    private val progressLabel = JBLabel()
    private val allMarkedCodeLabel = JBLabel()
    private val undoButton = JButton("Undo", AllIcons.Actions.Undo)
    private val clearResolvedButton = JButton("Clear resolved agent notes")
    private val progressBar = JProgressBar(0, 100)
    private val tabs = JBTabbedPane()
    private val toReviewTree = createBlockTree()
    private val reviewedTree = createBlockTree()
    private val notesTree = createTree(NoteTreeCellRenderer())
    private val toReviewHeadingLabel = JBLabel()
    private val reviewedHeadingLabel = JBLabel()
    private val todayText = JBTextArea()
    private val pomodoroTimer = project.service<PomodoroTimer>()
    private val startReviewingButton = JButton("Start reviewing!", AllIcons.Actions.Execute)
    private val pauseOrResumeButton = JButton("Pause", AllIcons.Actions.Pause)
    private val finishButton = JButton("Finish", AllIcons.Actions.Suspend)
    private val pomodoroStatusLabel = JBLabel()
    private val collapsedNoteKinds = mutableSetOf<NoteKind>()

    private var refreshingFromState = false

    init {
        add(createTopPanel(), BorderLayout.NORTH)
        add(createTabs(), BorderLayout.CENTER)
        add(createPomodoroFooter(), BorderLayout.SOUTH)
        listenForChanges(parentDisposable)
        refresh()
    }

    fun refresh() {
        refreshingFromState = true
        try {
            foldCheckBox.isSelected = reviewMarks.foldMode
            refreshPullRequestBanner()
            refreshPomodoro()
            refreshProgress()
            refreshAllMarkedCode()
            refreshUndo()
            refreshNotesTabTitle()
            refreshSelectedTab()
        } finally {
            refreshingFromState = false
        }
    }

    private fun createTopPanel(): JBPanel<*> {
        val topPanel = JBPanel<JBPanel<*>>()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
        topPanel.border = JBUI.Borders.empty(4, 6)
        addSection(
            topPanel,
            "Mark",
            actionButton("To review", DotIcon(TO_REVIEW_ACCENT), "ReviewMarks.MarkAsToReview"),
            actionButton("Reviewed", DotIcon(REVIEWED_ACCENT), "ReviewMarks.MarkSelectionAsReviewed"),
            actionButton("Reviewed & next", DotIcon(REVIEWED_ACCENT), "ReviewMarks.MarkAndGoToNext"),
            actionButton("Clear mark", AllIcons.Actions.Close, "ReviewMarks.ClearReviewedMark"),
        )
        addSection(
            topPanel,
            "Go to",
            actionButton("Next to review", AllIcons.Actions.MoveDown, "ReviewMarks.GoToNextRunToReview"),
            actionButton("Where I stopped", AllIcons.Actions.Back, "ReviewMarks.GoBackToLastStop"),
        )
        addSection(
            topPanel,
            "Comment on selection",
            actionButton(NoteKind.NOTE.label, ICON_BY_NOTE_KIND.getValue(NoteKind.NOTE), "ReviewMarks.AddNote"),
            actionButton(NoteKind.PR_COMMENT.label, ICON_BY_NOTE_KIND.getValue(NoteKind.PR_COMMENT), "ReviewMarks.AddPullRequestComment"),
            actionButton(NoteKind.AGENT_NOTE.label, ICON_BY_NOTE_KIND.getValue(NoteKind.AGENT_NOTE), "ReviewMarks.AddAgentNote"),
        )
        addSection(topPanel, "Review from git", fromGitButton())
        topPanel.add(pullRequestRow)
        topPanel.add(checkBoxRow(foldCheckBox))
        topPanel.add(fileNameLabel)
        topPanel.add(progressLabel)
        topPanel.add(progressBar)
        topPanel.add(allMarkedCodeLabel)
        undoButton.addActionListener { reviewMarks.undoLastClear() }
        topPanel.add(buttonRow(undoButton))
        topPanel.components.forEach { (it as JComponent).alignmentX = Component.LEFT_ALIGNMENT }

        foldCheckBox.addActionListener { if (!refreshingFromState) reviewMarks.changeFoldMode(foldCheckBox.isSelected) }
        return topPanel
    }

    private fun addSection(topPanel: JBPanel<*>, heading: String, vararg buttons: JButton) {
        val headingLabel = JBLabel(heading)
        headingLabel.font = JBUI.Fonts.smallFont()
        headingLabel.foreground = UIUtil.getContextHelpForeground()
        headingLabel.border = JBUI.Borders.empty(6, 2, 0, 0)
        topPanel.add(headingLabel)
        val row = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT, 4, 2))
        buttons.forEach { row.add(it) }
        topPanel.add(row)
    }

    private fun actionButton(label: String, icon: Icon, actionId: String): JButton {
        val button = JButton(label, icon)
        val description = ActionManager.getInstance().getAction(actionId)?.templatePresentation?.description.orEmpty()
        val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(actionId)
        button.toolTipText = if (shortcutText.isEmpty()) description else "$description ($shortcutText)"
        button.addActionListener { runAction(actionId) }
        return button
    }

    private fun fromGitButton(): JButton {
        val button = JButton("Review from git ▾", AllIcons.Vcs.Branch)
        button.toolTipText = "Mark all branch changes, uncommitted changes, changes since a branch, or a GitHub PR to review."
        button.addActionListener {
            val fromGitMenu = ActionManager.getInstance().getAction(FROM_GIT_MENU_ID) as ActionGroup
            JBPopupFactory.getInstance()
                .createActionGroupPopup(null, fromGitMenu, DataManager.getInstance().getDataContext(button), JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
                .showUnderneathOf(button)
        }
        return button
    }

    private fun createPullRequestRow(): JBPanel<*> {
        val postReviewButton = JButton("Post review…")
        val endPullRequestButton = JButton("End PR")
        postReviewButton.addActionListener { runAction("ReviewMarks.PostPullRequestReview") }
        endPullRequestButton.addActionListener { runAction("ReviewMarks.EndPullRequestReview") }
        val row = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT, 4, 2))
        row.add(pullRequestLabel)
        row.add(postReviewButton)
        row.add(endPullRequestButton)
        return row
    }

    private fun buttonRow(vararg buttons: JButton): JBPanel<*> {
        val row = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT, 0, 2))
        buttons.forEach { row.add(it) }
        return row
    }

    private fun checkBoxRow(vararg checkBoxes: JBCheckBox): JBPanel<*> {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 2))
        checkBoxes.forEach { row.add(it) }
        return row
    }

    private fun createTabs(): JBTabbedPane {
        tabs.addTab("To review", createBlocksTab(toReviewTree, toReviewHeadingLabel, BlockTab.TO_REVIEW))
        tabs.addTab("Reviewed", createBlocksTab(reviewedTree, reviewedHeadingLabel, BlockTab.REVIEWED))
        tabs.addTab("Notes", createNotesTab())
        tabs.addTab("Today", JBScrollPane(todayText))
        todayText.isEditable = false
        todayText.lineWrap = true
        todayText.wrapStyleWord = true
        todayText.border = JBUI.Borders.empty(8)
        tabs.addChangeListener { refreshSelectedTab() }

        toReviewTree.emptyText.text = "Nothing marked to review. Select code and press To review."
        reviewedTree.emptyText.text = "Nothing marked as reviewed yet."
        listOf(toReviewTree, reviewedTree).forEach { tree ->
            onOpenBlock(tree)
            onDeleteKey(tree) { removeBlocks(tree) }
        }
        return tabs
    }

    private fun createBlocksTab(tree: CheckboxTree, headingLabel: JBLabel, blockTab: BlockTab): JBPanel<*> {
        val changeStateButton = JButton(blockTab.changeStateLabel, DotIcon(blockTab.changeStateColor))
        changeStateButton.toolTipText = "${blockTab.changeStateLabel}: the ticked blocks, or the selected rows if none are ticked."
        changeStateButton.addActionListener { changeStateOfBlocks(tree, blockTab) }
        val removeButton = JButton(REMOVE_LABEL, AllIcons.Actions.Close)
        removeButton.toolTipText = "Remove the marks of the ticked blocks, or the selected rows if none are ticked. Undo brings them back."
        removeButton.addActionListener { removeBlocks(tree) }
        val buttons = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.RIGHT, 4, 0))
        buttons.add(changeStateButton)
        buttons.add(removeButton)
        if (blockTab == BlockTab.REVIEWED) {
            val clearAllButton = JButton("Clear all")
            clearAllButton.addActionListener { confirmAndClearAllMarks() }
            buttons.add(clearAllButton)
        }
        fun refreshButtonCounts() {
            val blockCount = targetBlocks(tree).size
            val countText = if (blockCount == 0) "" else " ($blockCount)"
            changeStateButton.text = blockTab.changeStateLabel + countText
            removeButton.text = REMOVE_LABEL + countText
            changeStateButton.isEnabled = blockCount > 0
            removeButton.isEnabled = blockCount > 0
        }
        tree.addCheckboxTreeListener(object : CheckboxTreeListener {
            override fun nodeStateChanged(node: CheckedTreeNode) = refreshButtonCounts()
        })
        tree.addTreeSelectionListener { refreshButtonCounts() }
        tree.model.addTreeModelListener(object : TreeModelAdapter() {
            override fun process(event: TreeModelEvent, type: EventType) = refreshButtonCounts()
        })
        refreshButtonCounts()
        onRightClick(tree) { event -> showBlockMenu(tree, blockTab, event) }

        val headingRow = JBPanel<JBPanel<*>>(BorderLayout())
        headingRow.border = JBUI.Borders.empty(2, 6)
        headingRow.add(headingLabel, BorderLayout.NORTH)
        headingRow.add(buttons, BorderLayout.CENTER)

        val blocksTab = JBPanel<JBPanel<*>>(BorderLayout())
        blocksTab.add(headingRow, BorderLayout.NORTH)
        blocksTab.add(JBScrollPane(tree), BorderLayout.CENTER)
        return blocksTab
    }

    fun blockMenuItems(tree: CheckboxTree, blockTab: BlockTab): List<JMenuItem> {
        val blockCount = targetBlocks(tree).size
        val countText = if (blockCount == 1) "1 block" else "$blockCount blocks"
        val openItem = JMenuItem("Open", AllIcons.Actions.EditSource)
        openItem.addActionListener { openSelectedBlock(tree) }
        openItem.isEnabled = selectedItems<BlockOfFile>(tree).isNotEmpty()
        val changeStateItem = JMenuItem("${blockTab.changeStateLabel}: $countText", DotIcon(blockTab.changeStateColor))
        changeStateItem.addActionListener { changeStateOfBlocks(tree, blockTab) }
        val removeItem = JMenuItem("$REMOVE_LABEL: $countText", AllIcons.Actions.Close)
        removeItem.addActionListener { removeBlocks(tree) }
        val tickAllItem = JMenuItem("Tick all")
        tickAllItem.addActionListener { setAllTicked(tree, true) }
        val untickAllItem = JMenuItem("Untick all")
        untickAllItem.addActionListener { setAllTicked(tree, false) }
        listOf(changeStateItem, removeItem).forEach { it.isEnabled = blockCount > 0 }
        return listOf(openItem, changeStateItem, removeItem, tickAllItem, untickAllItem)
    }

    private fun showBlockMenu(tree: CheckboxTree, blockTab: BlockTab, event: MouseEvent) {
        val clickedRow = tree.getClosestRowForLocation(event.x, event.y)
        if (clickedRow >= 0 && !tree.isRowSelected(clickedRow)) tree.setSelectionRow(clickedRow)
        val menu = JBPopupMenu()
        blockMenuItems(tree, blockTab).forEachIndexed { itemIndex, item ->
            if (itemIndex == FIRST_TICK_MENU_ITEM) menu.addSeparator()
            menu.add(item)
        }
        menu.show(tree, event.x, event.y)
    }

    private fun onRightClick(tree: Tree, showMenu: (MouseEvent) -> Unit) {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (event.isPopupTrigger) showMenu(event)
            }

            override fun mouseReleased(event: MouseEvent) {
                if (event.isPopupTrigger) showMenu(event)
            }
        })
    }

    private fun setAllTicked(tree: CheckboxTree, ticked: Boolean) {
        val root = tree.model.root as? CheckedTreeNode ?: return
        tree.setNodeState(root, ticked)
        tree.repaint()
    }

    private fun confirmAndClearAllMarks() {
        val confirmed = Messages.showYesNoDialog(
            project,
            "Remove every mark in this project (to review, reviewed and changed)? Notes are kept.",
            "Clear All Marks",
            null,
        ) == Messages.YES
        if (confirmed) reviewMarks.clearAllMarks()
    }

    private fun createNotesTab(): JBPanel<*> {
        notesTree.emptyText.text = "No notes yet. Select code and press Note, PR comment or Agent note."
        notesTree.addTreeExpansionListener(object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent) {
                noteKindOf(event)?.let { collapsedNoteKinds.remove(it) }
            }

            override fun treeCollapsed(event: TreeExpansionEvent) {
                noteKindOf(event)?.let { collapsedNoteKinds.add(it) }
            }
        })
        notesTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) selectedItems<NoteRow>(notesTree).firstOrNull()?.let { reviewMarks.goToNote(it.note) }
            }
        })
        notesTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_ENTER) selectedItems<NoteRow>(notesTree).firstOrNull()?.let { reviewMarks.goToNote(it.note) }
            }
        })
        onDeleteKey(notesTree) { deleteSelectedNotes() }

        val deleteSelectedButton = JButton("Delete selected")
        deleteSelectedButton.toolTipText = "Delete the selected comments. You can also press Delete. Undo brings them back."
        deleteSelectedButton.addActionListener { deleteSelectedNotes() }
        clearResolvedButton.toolTipText = "Delete the agent notes that Claude marked as done."
        clearResolvedButton.addActionListener { clearResolvedAgentNotes() }
        val headingRow = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.RIGHT, 4, 0))
        headingRow.border = JBUI.Borders.empty(2, 6)
        headingRow.add(clearResolvedButton)
        headingRow.add(deleteSelectedButton)

        val notesTab = JBPanel<JBPanel<*>>(BorderLayout())
        notesTab.add(headingRow, BorderLayout.NORTH)
        notesTab.add(JBScrollPane(notesTree), BorderLayout.CENTER)
        return notesTab
    }

    private fun noteKindOf(event: TreeExpansionEvent): NoteKind? =
        ((event.path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? NoteKindHeader)?.kind

    private fun createPomodoroFooter(): JBPanel<*> {
        val footer = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT, 4, 2))
        footer.border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
        startReviewingButton.toolTipText = "Start a Pomodoro: review, then a break, then repeat. Set the minutes in Settings."
        startReviewingButton.addActionListener { pomodoroTimer.start(System.currentTimeMillis()) }
        pauseOrResumeButton.addActionListener { pomodoroTimer.pauseOrResume(System.currentTimeMillis()) }
        finishButton.addActionListener {
            pomodoroTimer.finish(System.currentTimeMillis())?.let { summary -> showReviewMarksMessage(project, summary) }
        }
        footer.add(startReviewingButton)
        footer.add(pauseOrResumeButton)
        footer.add(finishButton)
        footer.add(pomodoroStatusLabel)
        footer.add(ActionLink("Settings…") { openSettings() })
        return footer
    }

    private fun refreshPomodoro() {
        val pomodoroState = pomodoroTimer.pomodoroState
        startReviewingButton.isVisible = pomodoroState == null
        pauseOrResumeButton.isVisible = pomodoroState != null
        finishButton.isVisible = pomodoroState != null
        val paused = pomodoroState is PomodoroState.Paused
        pauseOrResumeButton.text = if (paused) "Resume" else "Pause"
        pauseOrResumeButton.icon = if (paused) AllIcons.Actions.Resume else AllIcons.Actions.Pause
        pomodoroStatusLabel.text = pomodoroTimer.statusText(System.currentTimeMillis())
    }

    private fun listenForChanges(parentDisposable: Disposable) {
        val connection = project.messageBus.connect(parentDisposable)
        connection.subscribe(ReviewStateListener.TOPIC, ReviewStateListener { refreshIfShowing() })
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    refreshIfShowing()
                }
            },
        )
        addHierarchyListener { refreshIfShowing() }
        val pomodoroClock = Timer(POMODORO_CLOCK_MILLISECONDS) {
            if (!isShowing) return@Timer
            refreshPomodoro()
            if (tabs.selectedIndex == NOTES_TAB) refreshSelectedTab()
        }
        pomodoroClock.start()
        Disposer.register(parentDisposable) { pomodoroClock.stop() }
    }

    private fun refreshIfShowing() {
        if (isShowing) refresh()
    }

    private fun refreshPullRequestBanner() {
        val session = reviewMarks.pullRequest
        pullRequestRow.isVisible = session != null
        if (session != null) {
            val commentCount = reviewMarks.pullRequestComments().size
            pullRequestLabel.text = "PR #${session.number} · $commentCount comments"
            pullRequestLabel.toolTipText = "${session.title} (${session.owner}/${session.repository} as ${session.accountName})"
        }
    }

    private fun refreshProgress() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            fileNameLabel.text = "No file open"
            progressLabel.text = ""
            progressBar.value = 0
            return
        }
        val progress = reviewMarks.progressOf(editor.document)
        fileNameLabel.text = editor.virtualFile?.name ?: "This file"
        progressLabel.text = if (progress.markedLineCount == 0) "No code marked yet" else progress.statusText()
        progressBar.value = progress.percentReviewed
    }

    private fun refreshAllMarkedCode() {
        val progress = reviewMarks.progressOfAllMarkedCode()
        val scopeText = reviewMarks.pullRequest?.let { "All marked code (with PR #${it.number})" } ?: "All marked code"
        allMarkedCodeLabel.text = if (progress.markedLineCount == 0) "" else "$scopeText: ${progress.statusText()}"
    }

    private fun refreshUndo() {
        val undoDescription = reviewMarks.undoDescription
        undoButton.parent?.isVisible = undoDescription != null
        undoButton.text = "Undo: ${undoDescription.orEmpty()}"
    }

    private fun refreshNotesTabTitle() {
        tabs.setTitleAt(NOTES_TAB, "Notes (${reviewMarks.allNotes().size})")
    }

    private fun refreshSelectedTab() {
        when (tabs.selectedIndex) {
            TO_REVIEW_TAB -> showBlocks(toReviewTree, toReviewHeadingLabel, reviewMarks.blocksToReview())
            REVIEWED_TAB -> showBlocks(reviewedTree, reviewedHeadingLabel, reviewMarks.reviewedBlocks())
            NOTES_TAB -> showNotes(reviewMarks.allNotes())
            TODAY_TAB -> todayText.text = reviewMarks.reviewSummaryToday().text()
        }
    }

    private fun showBlocks(tree: Tree, headingLabel: JBLabel, filesWithBlocks: List<FileBlocks>) {
        headingLabel.text = "${filesWithBlocks.sumOf { it.codeLineCount }} lines in ${filesWithBlocks.size} files"
        val root = uncheckedNode(null)
        val fileTree = fileTreeOf(filesWithBlocks.map { relativePathOf(it.fileUrl) to it })
        fileTree.forEach { root.add(nodeOf(it)) }
        (tree.model as DefaultTreeModel).setRoot(root)
        TreeUtil.expandAll(tree)
    }

    private fun nodeOf(entry: FileTreeEntry<FileBlocks>): CheckedTreeNode = when (entry) {
        is FileTreeEntry.Folder -> {
            val folderNode = uncheckedNode(FolderRow(entry.name, codeLineCountIn(entry)))
            entry.children.forEach { folderNode.add(nodeOf(it)) }
            folderNode
        }
        is FileTreeEntry.File -> {
            val fileNode = uncheckedNode(FileRow(entry.name, entry.item))
            entry.item.rows.forEach { row -> fileNode.add(uncheckedNode(BlockOfFile(entry.item.fileUrl, row))) }
            fileNode
        }
    }

    private fun uncheckedNode(item: Any?): CheckedTreeNode {
        val node = CheckedTreeNode(item)
        node.isChecked = false
        return node
    }

    private fun codeLineCountIn(entry: FileTreeEntry<FileBlocks>): Int = when (entry) {
        is FileTreeEntry.Folder -> entry.children.sumOf { codeLineCountIn(it) }
        is FileTreeEntry.File -> entry.item.codeLineCount
    }

    private fun relativePathOf(fileUrl: String): String {
        val path = VirtualFileManager.extractPath(fileUrl)
        return project.basePath?.let { relativePathIn(it, path) } ?: path
    }

    private fun showNotes(notes: List<StoredNote>) {
        val agentReplies = reviewMarks.agentReplies()
        val resolvedAgentNoteCount = resolvedAgentNotes(notes, agentReplies).size
        clearResolvedButton.isVisible = resolvedAgentNoteCount > 0
        clearResolvedButton.text = "Clear resolved agent notes ($resolvedAgentNoteCount)"
        val root = DefaultMutableTreeNode()
        val notesByKind = notes.groupBy { noteKindNamed(it.kind) }
        NoteKind.entries.forEach { kind ->
            val notesOfKind = notesByKind[kind].orEmpty()
            if (notesOfKind.isEmpty()) return@forEach
            val headerNode = DefaultMutableTreeNode(NoteKindHeader(kind, notesOfKind.size))
            notesOfKind.forEach { note ->
                val agentReply = agentReplies[note.id]
                val noteNode = DefaultMutableTreeNode(NoteRow(note, reviewMarks.noteLinesText(note), agentReply))
                agentReply?.let { noteNode.add(DefaultMutableTreeNode(AgentReplyRow(it))) }
                headerNode.add(noteNode)
            }
            root.add(headerNode)
        }
        (notesTree.model as DefaultTreeModel).setRoot(root)
        for (headerIndex in 0 until root.childCount) {
            val headerNode = root.getChildAt(headerIndex) as DefaultMutableTreeNode
            val kind = (headerNode.userObject as NoteKindHeader).kind
            if (kind in collapsedNoteKinds) continue
            notesTree.expandPath(TreeUtil.getPathFromRoot(headerNode))
            for (noteIndex in 0 until headerNode.childCount) {
                notesTree.expandPath(TreeUtil.getPathFromRoot(headerNode.getChildAt(noteIndex)))
            }
        }
    }

    private fun resolvedAgentNotes(notes: List<StoredNote>, agentReplies: Map<String, AgentReply>): List<StoredNote> =
        notes.filter { noteKindNamed(it.kind) == NoteKind.AGENT_NOTE && agentReplies[it.id]?.resolved == true }

    private fun clearResolvedAgentNotes() {
        reviewMarks.deleteNotes(resolvedAgentNotes(reviewMarks.allNotes(), reviewMarks.agentReplies()))
        refresh()
    }

    private fun createBlockTree(): CheckboxTree {
        val tree = CheckboxTree(BlockTreeCellRenderer(), uncheckedNode(null))
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        return tree
    }

    private fun createTree(renderer: TreeCellRenderer): Tree {
        val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode()))
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = renderer
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        return tree
    }

    private inline fun <reified T> selectedItems(tree: Tree): List<T> {
        val selectedNodes = tree.selectionPaths.orEmpty().mapNotNull { it.lastPathComponent as? DefaultMutableTreeNode }
        return selectedNodes
            .flatMap { selectedNode -> selectedNode.depthFirstEnumeration().toList() }
            .mapNotNull { (it as DefaultMutableTreeNode).userObject as? T }
            .distinct()
    }

    private fun targetBlocks(tree: CheckboxTree): List<BlockOfFile> {
        val tickedBlocks = tree.getCheckedNodes(BlockOfFile::class.java, null).toList()
        return tickedBlocks.ifEmpty { selectedItems<BlockOfFile>(tree) }
    }

    private fun blockRangesOf(blocks: List<BlockOfFile>) =
        blocks.map { block -> block.fileUrl to TextRange(block.row.startOffset, block.row.endOffset) }

    private fun changeStateOfBlocks(tree: CheckboxTree, blockTab: BlockTab) {
        val blocks = targetBlocks(tree)
        when (blockTab) {
            BlockTab.TO_REVIEW -> reviewMarks.markBlocksAsReviewed(blockRangesOf(blocks))
            BlockTab.REVIEWED -> reviewMarks.markBlocksToReview(blockRangesOf(blocks))
        }
        refresh()
    }

    private fun removeBlocks(tree: CheckboxTree) {
        reviewMarks.clearMarkedBlocks(blockRangesOf(targetBlocks(tree)))
        refresh()
    }

    private fun deleteSelectedNotes() {
        val notes = selectedItems<NoteRow>(notesTree).map { it.note }
        if (notes.isEmpty()) return
        if (notes.size > 1) {
            val confirmed = Messages.showYesNoDialog(project, "Delete ${notes.size} notes?", "Delete Notes", null) == Messages.YES
            if (!confirmed) return
        }
        reviewMarks.deleteNotes(notes)
        refresh()
    }

    private fun onOpenBlock(tree: Tree) {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) openSelectedBlock(tree)
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_ENTER) openSelectedBlock(tree)
            }
        })
    }

    private fun onDeleteKey(tree: Tree, deleteSelected: () -> Unit) {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_DELETE || event.keyCode == KeyEvent.VK_BACK_SPACE) deleteSelected()
            }
        })
    }

    private fun openSelectedBlock(tree: Tree) {
        val block = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? BlockOfFile ?: return
        reviewMarks.goToBlock(block.fileUrl, block.row.startOffset)
    }

    private fun runAction(actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId)
        ActionUtil.invokeAction(action, DataManager.getInstance().getDataContext(this), ACTION_PLACE, null, null)
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ReviewMarksConfigurable::class.java)
    }
}
