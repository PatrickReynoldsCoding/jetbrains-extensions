package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager

class ReviewMarksEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return
        project.service<ReviewMarksService>().showStoredMarks(event.editor.document, file)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val project = event.editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return
        project.service<ReviewMarksService>().storeMarksOf(event.editor.document, file)
    }
}
