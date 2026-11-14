package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnplacedTextTest {

    @Test
    fun `says nothing when everything was placed`() {
        assertNull(unplacedText("Client.kt", removedMarkCount = 0, movedNoteCount = 0))
    }

    @Test
    fun `says how many marks were removed and how many comments were moved`() {
        assertEquals(
            "Client.kt: 2 marks were removed because their code changed while the file was closed. " +
                "Mark that code again when you have read it. " +
                "1 comment could not find its code, so it was put on the nearest line. Check it.",
            unplacedText("Client.kt", removedMarkCount = 2, movedNoteCount = 1),
        )
    }
}
