package com.patrickreynolds.reviewmarks

data class AgentNoteEntry(
    val relativePath: String,
    val lineNumber: Int,
    val codeLine: String,
    val noteText: String,
)

private const val NO_AGENT_NOTES_TEXT = "There are no agent notes right now.\n"

fun agentNotesMarkdown(entries: List<AgentNoteEntry>): String {
    if (entries.isEmpty()) return NO_AGENT_NOTES_TEXT
    val noteSections = entries.sortedWith(compareBy({ it.relativePath }, { it.lineNumber })).joinToString("\n") { entry ->
        "## ${entry.relativePath}:${entry.lineNumber}\n\nCode on that line: `${entry.codeLine}`\n\n${entry.noteText}\n"
    }
    return "# Review Marks agent notes\n\n" +
        "Feedback from the reviewer, written in the Review Marks IntelliJ plugin. " +
        "Each note is about the code at a file and line. Paths are relative to the project folder.\n\n" +
        noteSections
}
