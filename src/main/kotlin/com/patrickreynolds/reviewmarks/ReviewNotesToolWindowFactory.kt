package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

private const val ANCHOR_PREVIEW_LENGTH = 60

class ReviewNotesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val reviewMarks = project.service<ReviewMarksService>()
        val notesModel = CollectionListModel<StoredNote>()
        val notesList = JBList(notesModel)
        notesList.emptyText.text = "No notes yet. Right-click a line, Review Marks, Add or Edit Note."
        notesList.cellRenderer = SimpleListCellRenderer.create { label, note, _ ->
            val fileName = note.fileUrl.substringAfterLast('/')
            label.text = "${note.text}   —   $fileName: ${note.anchorText.take(ANCHOR_PREVIEW_LENGTH)}"
        }

        fun showCurrentNotes() = notesModel.replaceAll(reviewMarks.allNotes())

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
                        showCurrentNotes()
                    }
                }
            }
        })
        project.messageBus.connect(toolWindow.disposable).subscribe(
            ReviewNotesListener.TOPIC,
            ReviewNotesListener { showCurrentNotes() },
        )

        showCurrentNotes()
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(JBScrollPane(notesList), "", false),
        )
    }
}
