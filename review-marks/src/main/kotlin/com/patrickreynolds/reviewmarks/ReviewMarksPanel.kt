package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
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
import javax.swing.DropMode
import javax.swing.JMenu
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

private const val ACTION_PLACE = "ReviewMarksToolWindow"
private const val FROM_GIT_MENU_ID = "ReviewMarks.FromGitMenu"
private const val POMODORO_CLOCK_MILLISECONDS = 15_000
private const val REMOVE_LABEL = "Remove"
private const val ADD_TO_FOCUS_LABEL = "Add to focus"
private const val FIRST_TICK_MENU_ITEM = 4

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
    private val hideHighlightsCheckBox = JBCheckBox("Hide highlights")
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
    private val startBreakButton = JButton("Start break", AllIcons.Actions.Execute)
    private val finishBreakButton = JButton("Finish break", AllIcons.Actions.Checked)
    private val pomodoroStatusLabel = JBLabel()
    private val collapsedNoteKinds = mutableSetOf<NoteKind>()
    private val focusTree = createTree(FocusTreeCellRenderer())
    private val focusHeadingLabel = JBLabel()
    private val focusMoveButtons = mutableListOf<JButton>()
    private val tabContents = mutableMapOf<SidebarTab, JComponent>()
    private var shownTabs: List<SidebarTab> = emptyList()
    private var rebuildingTabs = false

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
            hideHighlightsCheckBox.isSelected = reviewMarks.highlightsHidden
            refreshPullRequestBanner()
            refreshPomodoro()
            refreshProgress()
            refreshAllMarkedCode()
            refreshUndo()
            showSidebarTabs()
            focusMoveButtons.forEach { it.isVisible = service<ReviewMarksSettings>().state.showFocusMoveButtons }
            refreshTabTitles()
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
            actionButton("Focus", DotIcon(FOCUS_ACCENT), "ReviewMarks.AddToFocus"),
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
        topPanel.add(checkBoxRow(foldCheckBox, hideHighlightsCheckBox))
        topPanel.add(fileNameLabel)
        topPanel.add(progressLabel)
        topPanel.add(progressBar)
        topPanel.add(allMarkedCodeLabel)
        undoButton.addActionListener { reviewMarks.undoLastClear() }
        topPanel.add(buttonRow(undoButton))
        topPanel.components.forEach { (it as JComponent).alignmentX = Component.LEFT_ALIGNMENT }

        foldCheckBox.addActionListener { if (!refreshingFromState) reviewMarks.changeFoldMode(foldCheckBox.isSelected) }
        hideHighlightsCheckBox.toolTipText = "Hide the colours of the marks and of Current focus in the editor. The marks are kept and the sidebar still lists them."
        hideHighlightsCheckBox.addActionListener { if (!refreshingFromState) reviewMarks.changeHighlightsHidden(hideHighlightsCheckBox.isSelected) }
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
        tabContents[SidebarTab.TO_REVIEW] = createBlocksTab(toReviewTree, toReviewHeadingLabel, BlockTab.TO_REVIEW)
        tabContents[SidebarTab.REVIEWED] = createBlocksTab(reviewedTree, reviewedHeadingLabel, BlockTab.REVIEWED)
        tabContents[SidebarTab.NOTES] = createNotesTab()
        tabContents[SidebarTab.CURRENT_FOCUS] = createFocusTab()
        tabContents[SidebarTab.TODAY] = JBScrollPane(todayText)
        todayText.isEditable = false
        todayText.lineWrap = true
        todayText.wrapStyleWord = true
        todayText.border = JBUI.Borders.empty(8)
        showSidebarTabs()
        tabs.addChangeListener { if (!rebuildingTabs) refreshSelectedTab() }
        onRightClickOnTabHeader()

        toReviewTree.emptyText.text = "Nothing marked to review. Select code and press To review."
        reviewedTree.emptyText.text = "Nothing marked as reviewed yet."
        listOf(toReviewTree, reviewedTree).forEach { tree ->
            onOpenBlock(tree)
            onDeleteKey(tree) { removeBlocks(tree) }
        }
        return tabs
    }

    private fun selectedTab(): SidebarTab? = shownTabs.getOrNull(tabs.selectedIndex)

    private fun showSidebarTabs() {
        val settingsState = service<ReviewMarksSettings>().state
        val tabsToShow = visibleSidebarTabs(settingsState.sidebarTabOrder, settingsState.hiddenSidebarTabs)
        if (tabsToShow == shownTabs) return
        val tabSelectedBefore = selectedTab()
        rebuildingTabs = true
        try {
            tabs.removeAll()
            tabsToShow.forEach { tabs.addTab(it.title, tabContents.getValue(it)) }
            tabs.selectedIndex = tabsToShow.indexOf(tabSelectedBefore).coerceAtLeast(0)
        } finally {
            rebuildingTabs = false
        }
        shownTabs = tabsToShow
        refreshTabTitles()
    }

    private fun refreshTabTitles() {
        shownTabs.forEachIndexed { tabIndex, tab ->
            tabs.setTitleAt(
                tabIndex,
                when (tab) {
                    SidebarTab.NOTES -> "Notes (${reviewMarks.allNotes().size})"
                    SidebarTab.CURRENT_FOCUS -> "Current focus (${reviewMarks.focusBlockCount})"
                    else -> tab.title
                },
            )
        }
    }

    fun tabMenuItems(tab: SidebarTab): List<JMenuItem> {
        val settingsState = service<ReviewMarksSettings>().state
        val hideItem = JMenuItem("Hide this tab")
        hideItem.isEnabled = shownTabs.size > 1
        hideItem.addActionListener {
            settingsState.hiddenSidebarTabs = sidebarTabsHiddenAfterHiding(tab, settingsState.sidebarTabOrder, settingsState.hiddenSidebarTabs).toMutableList()
            refresh()
        }
        val moveLeftItem = JMenuItem("Move left")
        moveLeftItem.isEnabled = shownTabs.indexOf(tab) > 0
        moveLeftItem.addActionListener { moveTab(tab, -1) }
        val moveRightItem = JMenuItem("Move right")
        moveRightItem.isEnabled = shownTabs.indexOf(tab) < shownTabs.size - 1
        moveRightItem.addActionListener { moveTab(tab, 1) }
        val hiddenTabs = sidebarTabsInOrder(settingsState.sidebarTabOrder).filter { it.name in settingsState.hiddenSidebarTabs }
        val showMenu = JMenu("Show hidden tab")
        showMenu.isEnabled = hiddenTabs.isNotEmpty()
        hiddenTabs.forEach { hiddenTab ->
            val showItem = JMenuItem(hiddenTab.title)
            showItem.addActionListener {
                settingsState.hiddenSidebarTabs = settingsState.hiddenSidebarTabs.filter { it != hiddenTab.name }.toMutableList()
                refresh()
            }
            showMenu.add(showItem)
        }
        val settingsItem = JMenuItem("Tab settings…")
        settingsItem.addActionListener { openSettings() }
        return listOf(hideItem, moveLeftItem, moveRightItem, showMenu, settingsItem)
    }

    private fun moveTab(tab: SidebarTab, visibleTabsToShift: Int) {
        val settingsState = service<ReviewMarksSettings>().state
        settingsState.sidebarTabOrder =
            sidebarTabOrderAfterMoving(settingsState.sidebarTabOrder, tab, visibleTabsToShift, settingsState.hiddenSidebarTabs).toMutableList()
        refresh()
    }

    private fun onRightClickOnTabHeader() {
        val showMenuOnPopupTrigger = { event: MouseEvent ->
            val tabIndex = tabs.indexAtLocation(event.x, event.y)
            val tab = shownTabs.getOrNull(tabIndex)
            if (event.isPopupTrigger && tab != null) {
                val menu = JBPopupMenu()
                tabMenuItems(tab).forEach { menu.add(it) }
                menu.show(tabs, event.x, event.y)
            }
        }
        tabs.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = showMenuOnPopupTrigger(event)

            override fun mouseReleased(event: MouseEvent) = showMenuOnPopupTrigger(event)
        })
    }

    private fun createFocusTab(): JBPanel<*> {
        focusTree.emptyText.text = "Nothing in focus. Tick blocks in To review or Reviewed and press Add to focus."
        focusTree.dragEnabled = true
        focusTree.dropMode = DropMode.ON_OR_INSERT
        focusTree.transferHandler = FocusTreeTransferHandler(
            focusTree = focusTree,
            selectedFocusBlockIds = { selectedFocusBlockIds() },
            moveFocusBlocks = { movedBlockIds, newParentId, indexAmongNewSiblings ->
                reviewMarks.moveFocusBlocks(movedBlockIds, newParentId, indexAmongNewSiblings)
                refresh()
            },
        )
        focusTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) openSelectedFocusBlock()
            }
        })
        onDeleteKey(focusTree) { removeSelectedFromFocus() }

        val moveUpButton = focusButton("Up", AllIcons.Actions.MoveUp, "Move the selected blocks up among the blocks next to them.") { reviewMarks.moveFocusBlocksUp(selectedFocusBlockIds()) }
        val moveDownButton = focusButton("Down", AllIcons.Actions.MoveDown, "Move the selected blocks down among the blocks next to them.") { reviewMarks.moveFocusBlocksDown(selectedFocusBlockIds()) }
        val nestButton = focusButton("Nest", AllIcons.Actions.Forward, "Put the selected blocks under the block above them. You can also drag a block onto another.") { reviewMarks.indentFocusBlocks(selectedFocusBlockIds()) }
        val unnestButton = focusButton("Un-nest", AllIcons.Actions.Back, "Move the selected blocks out of the block they are under.") { reviewMarks.outdentFocusBlocks(selectedFocusBlockIds()) }
        val markReviewedButton = focusButton("Reviewed", DotIcon(REVIEWED_ACCENT), "Mark the selected blocks, and the blocks under them, as reviewed and take them out of focus. Undo brings them back.") { reviewMarks.markFocusBlocksAsReviewedAndRemoveThem(selectedFocusBlockIds()) }
        val removeButton = focusButton(REMOVE_LABEL, AllIcons.Actions.Close, "Remove the selected blocks, and the blocks under them, from focus. Undo brings them back.") { removeSelectedFromFocus() }
        val clearAllButton = focusButton("Clear all", null, "Remove every block from focus. Undo brings them back.") { reviewMarks.clearFocus() }
        focusMoveButtons.addAll(listOf(moveUpButton, moveDownButton, nestButton, unnestButton))
        focusMoveButtons.forEach { onRightClick(it) { event -> showFocusMoveButtonsMenu(it, event) } }
        onRightClick(focusTree) { event -> showFocusMoveButtonsMenu(focusTree, event) }
        val selectionButtons = listOf(markReviewedButton, moveUpButton, moveDownButton, nestButton, unnestButton, removeButton)
        focusTree.addTreeSelectionListener { selectionButtons.forEach { it.isEnabled = focusTree.selectionCount > 0 } }
        selectionButtons.forEach { it.isEnabled = false }

        val buttons = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT, 2, 0))
        (selectionButtons + clearAllButton).forEach { buttons.add(compact(it)) }
        val headingRow = JBPanel<JBPanel<*>>(BorderLayout())
        headingRow.border = JBUI.Borders.empty(2, 6)
        headingRow.add(focusHeadingLabel, BorderLayout.NORTH)
        headingRow.add(buttons, BorderLayout.CENTER)

        val focusTab = JBPanel<JBPanel<*>>(BorderLayout())
        focusTab.add(headingRow, BorderLayout.NORTH)
        focusTab.add(JBScrollPane(focusTree), BorderLayout.CENTER)
        return focusTab
    }

    private fun focusButton(label: String, icon: Icon?, tooltip: String, change: () -> Unit): JButton {
        val button = if (icon == null) JButton(label) else JButton(label, icon)
        button.toolTipText = tooltip
        button.addActionListener {
            change()
            refresh()
        }
        return button
    }

    fun focusMoveButtonsMenuItem(): JMenuItem {
        val settingsState = service<ReviewMarksSettings>().state
        val buttonsShown = settingsState.showFocusMoveButtons
        val item = JMenuItem(if (buttonsShown) "Hide Up, Down, Nest and Un-nest buttons" else "Show Up, Down, Nest and Un-nest buttons")
        item.addActionListener {
            settingsState.showFocusMoveButtons = !buttonsShown
            refresh()
        }
        return item
    }

    private fun showFocusMoveButtonsMenu(clickedComponent: Component, event: MouseEvent) {
        val menu = JBPopupMenu()
        menu.add(focusMoveButtonsMenuItem())
        menu.show(clickedComponent, event.x, event.y)
    }

    private fun compact(button: JButton): JButton {
        button.font = JBUI.Fonts.smallFont()
        button.margin = JBUI.insets(0, 2)
        button.iconTextGap = JBUI.scale(2)
        return button
    }

    private fun focusRowsOfSelection(): List<FocusRow> =
        focusTree.selectionPaths.orEmpty()
            .sortedBy { focusTree.getRowForPath(it) }
            .mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? FocusRow }

    private fun selectedFocusBlockIds(): List<String> = focusRowsOfSelection().map { it.id }

    private fun removeSelectedFromFocus() {
        reviewMarks.removeFromFocus(selectedFocusBlockIds())
        refresh()
    }

    private fun openSelectedFocusBlock() {
        val focusRow = focusRowsOfSelection().firstOrNull() ?: return
        reviewMarks.goToBlock(focusRow.fileUrl, focusRow.startOffset)
    }

    private fun showFocus() {
        val focusRows = reviewMarks.focusRows()
        val idsSelectedBefore = selectedFocusBlockIds().toSet()
        focusHeadingLabel.text = if (focusRows.isEmpty()) "" else "${focusRows.size} blocks in focus. Drag a block onto another to nest it."
        val root = DefaultMutableTreeNode()
        focusRows.filter { it.parentId == TOP_LEVEL_FOCUS_PARENT_ID }.forEach { root.add(focusNodeOf(it, focusRows)) }
        (focusTree.model as DefaultTreeModel).setRoot(root)
        TreeUtil.expandAll(focusTree)
        val nodesToSelect = nodesOf(focusTree).filter { (it.userObject as? FocusRow)?.id in idsSelectedBefore }
        focusTree.selectionPaths = nodesToSelect.map { TreeUtil.getPathFromRoot(it) }.toTypedArray()
    }

    private fun focusNodeOf(focusRow: FocusRow, allFocusRows: List<FocusRow>): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(focusRow)
        allFocusRows.filter { it.parentId == focusRow.id }.forEach { node.add(focusNodeOf(it, allFocusRows)) }
        return node
    }

    private fun createBlocksTab(tree: CheckboxTree, headingLabel: JBLabel, blockTab: BlockTab): JBPanel<*> {
        val changeStateButton = JButton(blockTab.changeStateLabel, DotIcon(blockTab.changeStateColor))
        changeStateButton.toolTipText = "${blockTab.changeStateLabel}: the ticked blocks, or the selected rows if none are ticked."
        changeStateButton.addActionListener { changeStateOfBlocks(tree, blockTab) }
        val removeButton = JButton(REMOVE_LABEL, AllIcons.Actions.Close)
        removeButton.toolTipText = "Remove the marks of the ticked blocks, or the selected rows if none are ticked. Undo brings them back."
        removeButton.addActionListener { removeBlocks(tree) }
        val buttons = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.RIGHT, 4, 0))
        val addToFocusButton = JButton(ADD_TO_FOCUS_LABEL, DotIcon(FOCUS_ACCENT))
        addToFocusButton.toolTipText = "Add the ticked blocks, or the selected rows if none are ticked, to Current focus. They are highlighted red in the editor."
        addToFocusButton.addActionListener { addBlocksToFocus(tree) }
        buttons.add(changeStateButton)
        buttons.add(removeButton)
        buttons.add(addToFocusButton)
        val clearAllButton = JButton("Clear all")
        if (blockTab == BlockTab.REVIEWED) {
            clearAllButton.addActionListener { confirmAndClearAllMarks() }
        } else {
            clearAllButton.toolTipText = "Remove every block listed here. Reviewed marks are kept. Undo brings them back."
            clearAllButton.addActionListener { confirmAndClearAllBlocksOf(tree) }
        }
        buttons.add(clearAllButton)
        fun refreshButtonCounts() {
            val blockCount = targetBlocks(tree).size
            val countText = if (blockCount == 0) "" else " ($blockCount)"
            changeStateButton.text = blockTab.changeStateLabel + countText
            removeButton.text = REMOVE_LABEL + countText
            addToFocusButton.text = ADD_TO_FOCUS_LABEL + countText
            changeStateButton.isEnabled = blockCount > 0
            removeButton.isEnabled = blockCount > 0
            addToFocusButton.isEnabled = blockCount > 0
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
        val addToFocusItem = JMenuItem("$ADD_TO_FOCUS_LABEL: $countText", DotIcon(FOCUS_ACCENT))
        addToFocusItem.addActionListener { addBlocksToFocus(tree) }
        val tickAllItem = JMenuItem("Tick all")
        tickAllItem.addActionListener { setAllTicked(tree, true) }
        val untickAllItem = JMenuItem("Untick all")
        untickAllItem.addActionListener { setAllTicked(tree, false) }
        listOf(changeStateItem, removeItem, addToFocusItem).forEach { it.isEnabled = blockCount > 0 }
        return listOf(openItem, changeStateItem, removeItem, addToFocusItem, tickAllItem, untickAllItem)
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

    private fun onRightClick(component: Component, showMenu: (MouseEvent) -> Unit) {
        component.addMouseListener(object : MouseAdapter() {
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

    private fun allBlocksOf(tree: CheckboxTree): List<BlockOfFile> {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return emptyList()
        return root.depthFirstEnumeration().toList().mapNotNull { (it as DefaultMutableTreeNode).userObject as? BlockOfFile }
    }

    private fun confirmAndClearAllBlocksOf(tree: CheckboxTree) {
        val blocks = allBlocksOf(tree)
        if (blocks.isEmpty()) return
        val blockCountText = if (blocks.size == 1) "the 1 block" else "all ${blocks.size} blocks"
        val confirmed = Messages.showYesNoDialog(
            project,
            "Remove $blockCountText listed under To review? Reviewed marks and notes are kept.",
            "Clear All To Review",
            null,
        ) == Messages.YES
        if (!confirmed) return
        reviewMarks.clearMarkedBlocks(blockRangesOf(blocks))
        refresh()
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
            pomodoroTimer.finish(System.currentTimeMillis())?.let { summary -> showReviewMarksMessage(project, summary, soundName = pomodoroSoundName()) }
        }
        startBreakButton.addActionListener { pomodoroTimer.startBreak(System.currentTimeMillis()) }
        finishBreakButton.addActionListener { pomodoroTimer.finishBreak(System.currentTimeMillis()) }
        footer.add(startReviewingButton)
        footer.add(startBreakButton)
        footer.add(finishBreakButton)
        footer.add(pauseOrResumeButton)
        footer.add(finishButton)
        footer.add(pomodoroStatusLabel)
        footer.add(ActionLink("Settings…") { openSettings() })
        return footer
    }

    private fun refreshPomodoro() {
        val pomodoroState = pomodoroTimer.pomodoroState
        startReviewingButton.isVisible = pomodoroState == null
        pauseOrResumeButton.isVisible = pomodoroState != null && pomodoroState !is PomodoroState.AwaitingBreak
        startBreakButton.isVisible = pomodoroState is PomodoroState.AwaitingBreak
        finishBreakButton.isVisible = pomodoroState?.phase == PomodoroPhase.ON_BREAK
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
        EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(
            object : EditorMouseListener {
                override fun mouseClicked(event: EditorMouseEvent) {
                    if (isShowing && event.editor.project == project) syncSidebarWithEditor()
                }
            },
            parentDisposable,
        )
        addHierarchyListener { refreshIfShowing() }
        val pomodoroClock = Timer(POMODORO_CLOCK_MILLISECONDS) {
            if (!isShowing) return@Timer
            refreshPomodoro()
            if (selectedTab() == SidebarTab.NOTES) refreshSelectedTab()
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

    private fun refreshSelectedTab() {
        when (selectedTab()) {
            SidebarTab.TO_REVIEW -> showBlocks(toReviewTree, toReviewHeadingLabel, reviewMarks.blocksToReview())
            SidebarTab.REVIEWED -> showBlocks(reviewedTree, reviewedHeadingLabel, reviewMarks.reviewedBlocks())
            SidebarTab.NOTES -> showNotes(reviewMarks.allNotes())
            SidebarTab.CURRENT_FOCUS -> showFocus()
            SidebarTab.TODAY -> todayText.text = reviewMarks.reviewSummaryToday().text()
            null -> Unit
        }
        syncSidebarWithEditor()
    }

    fun syncSidebarWithEditor() {
        if (!service<ReviewMarksSettings>().state.syncSidebarWithEditor) return
        val tree = shownBlockTree() ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val fileUrl = editor.virtualFile?.url ?: return
        val blockUnderCaret = blockContainingOffset(blocksIn(tree), fileUrl, editor.caretModel.offset)
        val nodeToSelect = nodeShowing(tree, blockUnderCaret) ?: fileNodeOf(tree, fileUrl) ?: return
        val pathToSelect = TreeUtil.getPathFromRoot(nodeToSelect)
        tree.selectionPath = pathToSelect
        tree.scrollPathToVisible(pathToSelect)
    }

    private fun shownBlockTree(): CheckboxTree? = when (selectedTab()) {
        SidebarTab.TO_REVIEW -> toReviewTree
        SidebarTab.REVIEWED -> reviewedTree
        else -> null
    }

    private fun nodesOf(tree: Tree): List<DefaultMutableTreeNode> =
        (tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().toList().map { it as DefaultMutableTreeNode }

    private fun blocksIn(tree: Tree): List<BlockOfFile> = nodesOf(tree).mapNotNull { it.userObject as? BlockOfFile }

    private fun nodeShowing(tree: Tree, block: BlockOfFile?): DefaultMutableTreeNode? =
        block?.let { nodesOf(tree).firstOrNull { node -> node.userObject === block } }

    private fun fileNodeOf(tree: Tree, fileUrl: String): DefaultMutableTreeNode? =
        nodesOf(tree).firstOrNull { node -> (node.userObject as? FileRow)?.fileBlocks?.fileUrl == fileUrl }

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

    private fun addBlocksToFocus(tree: CheckboxTree) {
        reviewMarks.addToFocus(blockRangesOf(targetBlocks(tree)))
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
