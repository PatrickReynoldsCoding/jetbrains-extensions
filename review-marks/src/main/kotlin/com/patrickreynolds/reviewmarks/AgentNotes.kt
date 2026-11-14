package com.patrickreynolds.reviewmarks

data class AgentNoteEntry(
    val noteId: String,
    val relativePath: String,
    val firstLine: Int,
    val lastLine: Int,
    val code: String,
    val noteText: String,
) {
    val linesText: String get() = if (firstLine == lastLine) "$firstLine" else "$firstLine-$lastLine"
}

private const val NO_AGENT_NOTES_TEXT = "There are no agent notes right now.\n"
private const val CODE_FENCE = "```"

fun agentNotesMarkdown(entries: List<AgentNoteEntry>): String {
    if (entries.isEmpty()) return NO_AGENT_NOTES_TEXT
    val noteSections = entries.sortedWith(compareBy({ it.relativePath }, { it.firstLine })).joinToString("\n") { entry ->
        "## ${entry.relativePath}:${entry.linesText}\n\n" +
            "Note id: `${entry.noteId}`\n\n" +
            "Code on ${if (entry.firstLine == entry.lastLine) "that line" else "those lines"}:\n\n" +
            "$CODE_FENCE\n${entry.code}\n$CODE_FENCE\n\n" +
            "${entry.noteText}\n"
    }
    return "# Review Marks agent notes\n\n" +
        "Feedback from the reviewer, written in the Review Marks IntelliJ plugin. " +
        "Each note is about the code at a file and a line or a range of lines (counted from 1). " +
        "Paths are relative to the project folder. " +
        "Reply to a note by its id in $AGENT_REPLIES_FILE_PATH.\n\n" +
        noteSections
}
