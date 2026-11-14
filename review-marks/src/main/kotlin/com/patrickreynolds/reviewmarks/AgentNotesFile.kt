package com.patrickreynolds.reviewmarks

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File

const val AGENT_NOTES_FILE_PATH = ".idea/review-marks-agent-notes.md"

class AgentNotesFile(private val project: Project) {

    private var lastSavedText: String? = null

    @Synchronized
    fun save(entries: List<AgentNoteEntry>) {
        val projectFolder = project.basePath ?: return
        val notesText = agentNotesMarkdown(entries)
        if (notesText == lastSavedText) return
        val notesFile = File(projectFolder, AGENT_NOTES_FILE_PATH)
        if (entries.isEmpty() && !notesFile.exists()) return
        lastSavedText = notesText
        AppExecutorUtil.getAppExecutorService().execute {
            notesFile.parentFile.mkdirs()
            notesFile.writeText(notesText)
        }
    }
}
