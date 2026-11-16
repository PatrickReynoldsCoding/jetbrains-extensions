package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class MacNotificationTest {

    @Test
    fun `builds an osascript command that shows the message with the title`() {
        assertEquals(
            listOf("osascript", "-e", "display notification \"Break time\" with title \"Review Marks\""),
            macNotificationCommand(title = "Review Marks", message = "Break time", soundName = null),
        )
    }

    @Test
    fun `adds the sound name to the notification when one is given`() {
        assertEquals(
            listOf("osascript", "-e", "display notification \"Keep focus\" with title \"Review Marks\" sound name \"Glass\""),
            macNotificationCommand(title = "Review Marks", message = "Keep focus", soundName = "Glass"),
        )
    }

    @Test
    fun `escapes quotes in the sound name`() {
        assertEquals(
            "display notification \"x\" with title \"t\" sound name \"a\\\"b\"",
            macNotificationCommand(title = "t", message = "x", soundName = "a\"b")[2],
        )
    }

    @Test
    fun `escapes double quotes so the message cannot end the AppleScript string`() {
        assertEquals("say \\\"hi\\\" now", escapedForAppleScriptString("say \"hi\" now"))
    }

    @Test
    fun `escapes backslashes before quotes`() {
        assertEquals("C:\\\\temp \\\\\\\"x\\\\\\\"", escapedForAppleScriptString("C:\\temp \\\"x\\\""))
    }

    @Test
    fun `keeps apostrophes as they are`() {
        assertEquals("you're supposed to be reviewing", escapedForAppleScriptString("you're supposed to be reviewing"))
    }
}
