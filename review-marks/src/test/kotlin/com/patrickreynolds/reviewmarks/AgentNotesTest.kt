package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentNotesTest {

    @Test
    fun `says there are no notes when the list is empty so an old file never lingers`() {
        assertEquals("There are no agent notes right now.\n", agentNotesMarkdown(emptyList()))
    }

    @Test
    fun `lists a one line note with its file, line, code and text`() {
        val text = agentNotesMarkdown(
            listOf(AgentNoteEntry("n1", "src/Client.kt", 42, 42, "retry(3) { call() }", "Why 3 retries? Make it a setting.")),
        )

        assertTrue(text.contains("## src/Client.kt:42\n"))
        assertTrue(text.contains("Note id: `n1`"))
        assertTrue(text.contains("Code on that line:\n\n```\nretry(3) { call() }\n```"))
        assertTrue(text.contains("Why 3 retries? Make it a setting."))
    }

    @Test
    fun `lists a note on several lines with the line range and all of the code`() {
        val text = agentNotesMarkdown(
            listOf(AgentNoteEntry("n1", "src/Client.kt", 10, 12, "fun close() {\n    flush()\n}", "Does close() flush twice?")),
        )

        assertTrue(text.contains("## src/Client.kt:10-12\n"))
        assertTrue(text.contains("Code on those lines:\n\n```\nfun close() {\n    flush()\n}\n```"))
    }

    @Test
    fun `orders the notes by file and then by first line`() {
        val text = agentNotesMarkdown(
            listOf(
                AgentNoteEntry("n1", "b.kt", 1, 1, "x", "third"),
                AgentNoteEntry("n1", "a.kt", 20, 21, "x", "second"),
                AgentNoteEntry("n1", "a.kt", 3, 3, "x", "first"),
            ),
        )

        val headings = Regex("## (.*)").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(listOf("a.kt:3", "a.kt:20-21", "b.kt:1"), headings)
    }
}
