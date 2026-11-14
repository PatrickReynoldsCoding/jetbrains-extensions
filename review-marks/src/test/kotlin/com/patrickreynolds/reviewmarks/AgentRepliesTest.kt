package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentRepliesTest {

    @Test
    fun `reads each reply by the id of its note`() {
        val replies = parseAgentReplies(
            """[{"noteId": "ab12cd34", "reply": "Changed it to a for loop.", "resolved": true},
                {"noteId": "ef56gh78", "reply": "3 comes from the API docs."}]""",
        )

        assertEquals(
            mapOf(
                "ab12cd34" to AgentReply("ab12cd34", "Changed it to a for loop.", resolved = true),
                "ef56gh78" to AgentReply("ef56gh78", "3 comes from the API docs.", resolved = false),
            ),
            replies,
        )
    }

    @Test
    fun `gives no replies when the file is not valid json, so a half written file shows nothing`() {
        assertEquals(emptyMap<String, AgentReply>(), parseAgentReplies("[{\"noteId\": \"ab1"))
    }

    @Test
    fun `skips a reply that has no note id`() {
        assertEquals(emptyMap<String, AgentReply>(), parseAgentReplies("""[{"reply": "orphan"}]"""))
    }
}
