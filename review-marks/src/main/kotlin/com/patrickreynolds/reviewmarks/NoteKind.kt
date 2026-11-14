package com.patrickreynolds.reviewmarks

enum class NoteKind(val label: String, val pluralLabel: String, val explanation: String) {
    NOTE("Note", "Notes", "Just for me. Stays in the editor and the Notes tab."),
    PR_COMMENT("PR comment", "PR comments", "Posted as a comment on these lines when you post the PR review."),
    AGENT_NOTE("Agent note", "Agent notes", "Feedback for Claude. Saved where Claude can read it, with the file and lines."),
}

fun noteKindNamed(name: String): NoteKind = NoteKind.entries.firstOrNull { it.name == name } ?: NoteKind.NOTE
