package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacNotificationTest {

    @Test
    fun `builds an osascript command that shows the message with the title`() {
        assertEquals(
            listOf("osascript", "-e", "display notification \"Break time\" with title \"Review Marks\""),
            macNotificationCommand(title = "Review Marks", message = "Break time"),
        )
    }

    @Test
    fun `plays a named system sound with afplay`() {
        assertEquals(
            listOf("afplay", "/System/Library/Sounds/Basso.aiff"),
            macSoundCommand(soundName = "Basso"),
        )
    }

    @Test
    fun `plays no sound for a name that is not a system alert sound`() {
        assertNull(macSoundCommand(soundName = "../../tmp/evil"))
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
