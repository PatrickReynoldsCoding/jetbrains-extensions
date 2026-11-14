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
    fun `lists each note with its file, line, the code on that line and the note text`() {
        val text = agentNotesMarkdown(
            listOf(AgentNoteEntry("src/Client.kt", 42, "retry(3) { call() }", "Why 3 retries? Make it a setting.")),
        )

        assertTrue(text.contains("## src/Client.kt:42"))
        assertTrue(text.contains("Code on that line: `retry(3) { call() }`"))
        assertTrue(text.contains("Why 3 retries? Make it a setting."))
    }

    @Test
    fun `orders the notes by file and then by line`() {
        val text = agentNotesMarkdown(
            listOf(
                AgentNoteEntry("b.kt", 1, "x", "third"),
                AgentNoteEntry("a.kt", 20, "x", "second"),
                AgentNoteEntry("a.kt", 3, "x", "first"),
            ),
        )

        val headings = Regex("## (.*)").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(listOf("a.kt:3", "a.kt:20", "b.kt:1"), headings)
    }
}
