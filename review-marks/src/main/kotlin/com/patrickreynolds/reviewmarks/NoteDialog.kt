package com.patrickreynolds.reviewmarks

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

private const val NOTE_ROWS = 5
private const val NOTE_COLUMNS = 50

class NoteDialog(
    project: Project,
    initialDetails: NoteDetails?,
    private val pullRequestReviewActive: Boolean,
) : DialogWrapper(project) {

    private val textArea = JBTextArea(initialDetails?.text.orEmpty(), NOTE_ROWS, NOTE_COLUMNS)
    private val floatingCheckBox = JBCheckBox("Show this note above the code", initialDetails?.floating ?: false)
    private val pullRequestCheckBox = JBCheckBox("Post as a comment in the PR review", initialDetails?.includeInPullRequest ?: true)

    init {
        title = "Review Note"
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("Note for this line. Leave it empty to delete the note."), BorderLayout.NORTH)
        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)

        val options = JPanel()
        options.layout = BoxLayout(options, BoxLayout.Y_AXIS)
        options.add(floatingCheckBox)
        if (pullRequestReviewActive) options.add(pullRequestCheckBox)
        panel.add(options, BorderLayout.SOUTH)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = textArea

    fun noteDetails() = NoteDetails(
        text = textArea.text.trim(),
        floating = floatingCheckBox.isSelected,
        includeInPullRequest = pullRequestCheckBox.isSelected,
    )
}
