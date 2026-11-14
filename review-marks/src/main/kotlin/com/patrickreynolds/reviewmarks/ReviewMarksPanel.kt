package com.patrickreynolds.reviewmarks

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

private const val TO_REVIEW_TAB = 0
private const val REVIEWED_TAB = 1
private const val NOTES_TAB = 2
private const val TODAY_TAB = 3

private const val ACTION_PLACE = "ReviewMarksToolWindow"
private const val TOOLBAR_GROUP_ID = "ReviewMarks.Toolbar"
private const val ANCHOR_PREVIEW_LENGTH = 60

private val KIND_MARKER_BY_KIND = mapOf(
    NoteKind.NOTE to "",
    NoteKind.PR_COMMENT to "💬 ",
    NoteKind.AGENT_NOTE to "🤖 ",
)

class ReviewMarksPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : JBPanel<ReviewMarksPanel>(BorderLayout()) {

    private val reviewMarks = project.service<ReviewMarksService>()
    private val pauseSettings = service<ReviewMarksSettings>().state

    private val foldCheckBox = JBCheckBox("Fold reviewed")
    private val pullRequestLabel = JBLabel()
    private val pullRequestRow = createPullRequestRow()
    private val fileNameLabel = JBLabel()
    private val progressLabel = JBLabel()
    private val progressBar = JProgressBar(0, 100)
    private val tabs = JBTabbedPane()
    private val toReviewTree = createBlockTree()
    private val reviewedTree = createBlockTree()
    private val reviewedHeadingLabel = JBLabel()
    private val notesModel = CollectionListModel<StoredNote>()
    private val notesList = JBList(notesModel)
    private val todayText = JBTextArea()
    private val pauseCheckBox = JBCheckBox("Pomodoro breaks")
    private val pauseLimitsLabel = JBLabel()

    private var refreshingFromState = false

    init {
        add(createTopPanel(), BorderLayout.NORTH)
        add(createTabs(), BorderLayout.CENTER)
        add(createPauseFooter(), BorderLayout.SOUTH)
        listenForChanges(parentDisposable)
        refresh()
    }

    fun refresh() {
        refreshingFromState = true
        try {
            foldCheckBox.isSelected = reviewMarks.foldMode
            refreshPullRequestBanner()
            pauseCheckBox.isSelected = pauseSettings.pauseRemindersOn
            pauseLimitsLabel.text = pomodoroStatusText()
            refreshProgress()
            refreshNotesTabTitle()
            refreshSelectedTab()
        } finally {
            refreshingFromState = false
        }
    }

    private fun createTopPanel(): JBPanel<*> {
        val topPanel = JBPanel<JBPanel<*>>()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
        topPanel.border = JBUI.Borders.empty(4)
        topPanel.add(createToolbar())
        topPanel.add(pullRequestRow)
        topPanel.add(checkBoxRow(foldCheckBox))
        topPanel.add(fileNameLabel)
        topPanel.add(progressLabel)
        topPanel.add(progressBar)
        topPanel.components.forEach { (it as JComponent).alignmentX = Component.LEFT_ALIGNMENT }

        foldCheckBox.addActionListener { if (!refreshingFromState) reviewMarks.changeFoldMode(foldCheckBox.isSelected) }
        return topPanel
    }

    private fun createPullRequestRow(): JBPanel<*> {
        val postReviewButton = JButton("Post review…")
        val endPullRequestButton = JButton("End PR")
        postReviewButton.addActionListener { runAction("ReviewMarks.PostPullRequestReview") }
        endPullRequestButton.addActionListener { runAction("ReviewMarks.EndPullRequestReview") }
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 2))
        row.add(pullRequestLabel)
        row.add(postReviewButton)
        row.add(endPullRequestButton)
        return row
    }

    private fun createToolbar(): JComponent {
        val toolbarGroup = ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID) as ActionGroup
        val toolbar = ActionManager.getInstance().createActionToolbar(ACTION_PLACE, toolbarGroup, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    private fun checkBoxRow(vararg checkBoxes: JBCheckBox): JBPanel<*> {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 2))
        checkBoxes.forEach { row.add(it) }
        return row
    }

    private fun createTabs(): JBTabbedPane {
        tabs.addTab("To review", JBScrollPane(toReviewTree))
        tabs.addTab("Reviewed", createReviewedTab())
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
            onClearBlock(tree)
        }
        return tabs
    }

    private fun createReviewedTab(): JBPanel<*> {
        val clearAllButton = JButton("Clear all")
        clearAllButton.addActionListener {
            val confirmed = Messages.showYesNoDialog(
                project,
                "Remove every mark in this project (to review, reviewed and changed)? Notes are kept.",
                "Clear All Marks",
                null,
            ) == Messages.YES
            if (confirmed) reviewMarks.clearAllMarks()
        }
        val headingRow = JBPanel<JBPanel<*>>(BorderLayout())
        headingRow.border = JBUI.Borders.empty(2, 6)
        headingRow.add(reviewedHeadingLabel, BorderLayout.CENTER)
        headingRow.add(clearAllButton, BorderLayout.EAST)

        val reviewedTab = JBPanel<JBPanel<*>>(BorderLayout())
        reviewedTab.add(headingRow, BorderLayout.NORTH)
        reviewedTab.add(JBScrollPane(reviewedTree), BorderLayout.CENTER)
        return reviewedTab
    }

    private fun createNotesTab(): JBScrollPane {
        notesList.emptyText.text = "No notes yet. Click Note, or right-click a line, Review Marks, Add or Edit Note."
        notesList.cellRenderer = SimpleListCellRenderer.create { label, note, _ ->
            val floatingMarker = if (note.floating) "▲ " else ""
            val kindMarker = KIND_MARKER_BY_KIND.getValue(noteKindNamed(note.kind))
            label.text = "$floatingMarker$kindMarker${note.text.lineSequence().first()}   —   " +
                "${note.fileUrl.substringAfterLast('/')}: ${note.anchorText.take(ANCHOR_PREVIEW_LENGTH)}"
        }
        notesList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) notesList.selectedValue?.let { reviewMarks.goToNote(it) }
            }
        })
        notesList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                val selectedNote = notesList.selectedValue ?: return
                when (event.keyCode) {
                    KeyEvent.VK_ENTER -> reviewMarks.goToNote(selectedNote)
                    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
                        reviewMarks.deleteNote(selectedNote)
                        refresh()
                    }
                }
            }
        })
        return JBScrollPane(notesList)
    }

    private fun createPauseFooter(): JBPanel<*> {
        val footer = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 6, 2))
        footer.border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
        pauseCheckBox.addActionListener {
            if (refreshingFromState) return@addActionListener
            pauseSettings.pauseRemindersOn = pauseCheckBox.isSelected
        }
        footer.add(pauseCheckBox)
        footer.add(pauseLimitsLabel)
        footer.add(ActionLink("Settings…") { openSettings() })
        return footer
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
    }

    private fun refreshIfShowing() {
        if (isShowing) refresh()
    }

    private fun pomodoroStatusText(): String {
        val minutesLeftText = project.service<PomodoroTimer>().minutesLeftText(System.currentTimeMillis())
        return minutesLeftText.ifEmpty { pomodoroLengthsText(pauseSettings.pomodoroLengths()) }
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

    private fun refreshNotesTabTitle() {
        tabs.setTitleAt(NOTES_TAB, "Notes (${reviewMarks.allNotes().size})")
    }

    private fun refreshSelectedTab() {
        when (tabs.selectedIndex) {
            TO_REVIEW_TAB -> showBlocks(toReviewTree, reviewMarks.blocksToReview())
            REVIEWED_TAB -> {
                val reviewedBlocks = reviewMarks.reviewedBlocks()
                reviewedHeadingLabel.text = "${reviewedBlocks.sumOf { it.codeLineCount }} lines in ${reviewedBlocks.size} files"
                showBlocks(reviewedTree, reviewedBlocks)
            }
            NOTES_TAB -> notesModel.replaceAll(reviewMarks.allNotes())
            TODAY_TAB -> todayText.text = reviewMarks.reviewSummaryToday().text()
        }
    }

    private fun showBlocks(tree: Tree, filesWithBlocks: List<FileBlocks>) {
        val root = DefaultMutableTreeNode()
        filesWithBlocks.forEach { fileBlocks ->
            val fileNode = DefaultMutableTreeNode(fileBlocks)
            fileBlocks.rows.forEach { row -> fileNode.add(DefaultMutableTreeNode(BlockOfFile(fileBlocks.fileUrl, row))) }
            root.add(fileNode)
        }
        (tree.model as DefaultTreeModel).setRoot(root)
        TreeUtil.expandAll(tree)
    }

    private fun createBlockTree(): Tree {
        val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode()))
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = BlockTreeCellRenderer()
        return tree
    }

    private fun selectedBlock(tree: Tree): BlockOfFile? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? BlockOfFile

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

    private fun onClearBlock(tree: Tree) {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                val deleteKeyPressed = event.keyCode == KeyEvent.VK_DELETE || event.keyCode == KeyEvent.VK_BACK_SPACE
                val block = selectedBlock(tree)
                if (deleteKeyPressed && block != null) {
                    reviewMarks.clearMarkedBlock(block.fileUrl, TextRange(block.row.startOffset, block.row.endOffset))
                }
            }
        })
    }

    private fun openSelectedBlock(tree: Tree) {
        val block = selectedBlock(tree) ?: return
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
