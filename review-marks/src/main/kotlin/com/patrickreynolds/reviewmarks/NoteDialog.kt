package com.patrickreynolds.reviewmarks

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

private const val NOTE_ROWS = 5
private const val NOTE_COLUMNS = 50

class NoteDialog(
    project: Project,
    initialDetails: NoteDetails?,
) : DialogWrapper(project) {

    private val textArea = JBTextArea(initialDetails?.text.orEmpty(), NOTE_ROWS, NOTE_COLUMNS)
    private val floatingCheckBox = JBCheckBox("Show this note above the code", initialDetails?.floating ?: false)
    private val kindButtons = NoteKind.entries.associateWith { kind ->
        JBRadioButton("${kind.label}: ${kind.explanation}", kind == (initialDetails?.kind ?: NoteKind.NOTE))
    }

    init {
        title = "Review Note"
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        val kindGroup = ButtonGroup()
        kindButtons.values.forEach { kindGroup.add(it) }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("Note for this line. Leave it empty to delete the note."), BorderLayout.NORTH)
        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)

        val options = JPanel()
        options.layout = BoxLayout(options, BoxLayout.Y_AXIS)
        kindButtons.values.forEach { options.add(it) }
        options.add(floatingCheckBox)
        panel.add(options, BorderLayout.SOUTH)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = textArea

    fun noteDetails() = NoteDetails(
        text = textArea.text.trim(),
        floating = floatingCheckBox.isSelected,
        kind = kindButtons.entries.first { it.value.isSelected }.key,
    )
}
