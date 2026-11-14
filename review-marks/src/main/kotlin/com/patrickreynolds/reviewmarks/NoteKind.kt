package com.patrickreynolds.reviewmarks

enum class NoteKind(val label: String, val explanation: String) {
    NOTE("Note", "Just for me. Stays in the editor and the Notes tab."),
    PR_COMMENT("PR comment", "Posted as a line comment when you post the PR review."),
    AGENT_NOTE("Agent note", "Feedback for Claude. Saved where Claude can read it, with the file and line."),
}

fun noteKindNamed(name: String): NoteKind = NoteKind.entries.firstOrNull { it.name == name } ?: NoteKind.NOTE
