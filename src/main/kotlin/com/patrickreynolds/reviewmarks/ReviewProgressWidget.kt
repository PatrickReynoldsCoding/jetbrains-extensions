package com.patrickreynolds.reviewmarks

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class ReviewProgressWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ReviewProgressWidget.WIDGET_ID

    override fun getDisplayName(): String = "Review Progress"

    override fun createWidget(project: Project): StatusBarWidget = ReviewProgressWidget(project)
}

class ReviewProgressWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.TextPresentation {

    private var progressText = ""

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    refresh()
                }
            },
        )
        refresh()
    }

    override fun getText(): String = progressText

    override fun getAlignment(): Float = 0.5f

    override fun getTooltipText(): String = "Review progress in this file. Click to go to the next code to review."

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val editor = getEditor() ?: return@Consumer
        project.service<ReviewMarksService>().goToNextRunToReview(editor)
    }

    fun refresh() {
        val editor = getEditor()
        progressText = if (editor == null) "" else project.service<ReviewMarksService>().progressOf(editor.document).statusText()
        myStatusBar?.updateWidget(WIDGET_ID)
    }

    companion object {
        const val WIDGET_ID = "ReviewMarks.Progress"
    }
}
