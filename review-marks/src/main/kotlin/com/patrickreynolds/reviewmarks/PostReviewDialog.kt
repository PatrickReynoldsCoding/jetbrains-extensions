package com.patrickreynolds.reviewmarks

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

private const val SUMMARY_ROWS = 6
private const val SUMMARY_COLUMNS = 60
private const val COMMENT_PREVIEW_LENGTH = 80

class PostReviewDialog(
    project: Project,
    private val session: PullRequestSession,
    private val comments: List<ReviewComment>,
) : DialogWrapper(project) {

    private val commentsByPlace = commentsByPlace(comments, session.commentableLines)

    private val summaryArea = JBTextArea(SUMMARY_ROWS, SUMMARY_COLUMNS)
    private val commentRadioButton = JRadioButton("Comment", true)
    private val approveRadioButton = JRadioButton("Approve")
    private val requestChangesRadioButton = JRadioButton("Request changes")

    init {
        title = "Post Review"
        setOKButtonText("Post review")
        summaryArea.lineWrap = true
        summaryArea.wrapStyleWord = true
        val reviewTypes = ButtonGroup()
        listOf(commentRadioButton, approveRadioButton, requestChangesRadioButton).forEach { reviewTypes.add(it) }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.add(
            JBLabel("<html>This sends your review to GitHub as <b>${session.accountName}</b> on " +
                "<b>${session.owner}/${session.repository}</b>, PR #${session.number}: ${session.title}</html>"),
            BorderLayout.NORTH,
        )

        val middle = JPanel(BorderLayout(0, JBUI.scale(4)))
        middle.add(JBLabel("Summary (optional when you add comments or approve):"), BorderLayout.NORTH)
        middle.add(JBScrollPane(summaryArea), BorderLayout.CENTER)
        val reviewTypeRow = JPanel()
        reviewTypeRow.layout = BoxLayout(reviewTypeRow, BoxLayout.X_AXIS)
        listOf(commentRadioButton, approveRadioButton, requestChangesRadioButton).forEach { reviewTypeRow.add(it) }
        middle.add(reviewTypeRow, BorderLayout.SOUTH)
        panel.add(middle, BorderLayout.CENTER)

        val commentList = JBList(comments.map { "${it.path}:${linesOf(it)} — ${it.body.lineSequence().first().take(COMMENT_PREVIEW_LENGTH)}" })
        commentList.emptyText.text = "No comments. Add a note to a line to include it."
        val commentsPanel = JPanel(BorderLayout())
        commentsPanel.add(JBLabel("${comments.size} PR comments:"), BorderLayout.NORTH)
        if (commentsByPlace.outsideDiff.isNotEmpty()) {
            val warning = JBLabel(
                "<html><b>${commentsByPlace.outsideDiff.size} of them are on lines outside this PR's changes.</b> " +
                    "GitHub refuses those as line comments, so they are added to the end of the summary instead.</html>",
            )
            warning.foreground = JBColor.namedColor("Label.warningForeground", JBColor(0x9A6700, 0xE0B341))
            commentsPanel.add(warning, BorderLayout.SOUTH)
        }
        commentsPanel.add(JBScrollPane(commentList), BorderLayout.CENTER)
        commentsPanel.preferredSize = JBUI.size(500, 140)
        panel.add(commentsPanel, BorderLayout.SOUTH)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = summaryArea

    override fun doValidate(): ValidationInfo? {
        val needsSomethingToSay = selectedEvent() != ReviewEvent.APPROVE
        if (needsSomethingToSay && summaryArea.text.isBlank() && comments.isEmpty()) {
            return ValidationInfo("Write a summary or add at least one comment.", summaryArea)
        }
        return null
    }

    fun submission() = ReviewSubmission(
        commitSha = session.headSha,
        summary = summaryWithCommentsOutsideDiff(summaryArea.text.trim(), commentsByPlace.outsideDiff),
        event = selectedEvent(),
        comments = commentsByPlace.insideDiff,
    )

    private fun selectedEvent(): ReviewEvent = when {
        approveRadioButton.isSelected -> ReviewEvent.APPROVE
        requestChangesRadioButton.isSelected -> ReviewEvent.REQUEST_CHANGES
        else -> ReviewEvent.COMMENT
    }
}

private fun linesOf(comment: ReviewComment): String =
    if (comment.firstLine == comment.lastLine) "${comment.firstLine}" else "${comment.firstLine}-${comment.lastLine}"
