package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditChangesMarkedBlockTest {

    private val markedBlockStart = 10
    private val markedBlockEnd = 20

    @Test
    fun `typing inside the marked block changes it`() {
        assertTrue(editChangesMarkedBlock(editOffset = 15, removedLength = 0, markedBlockStart, markedBlockEnd))
    }

    @Test
    fun `typing right before the marked block leaves it unchanged`() {
        assertFalse(editChangesMarkedBlock(editOffset = 10, removedLength = 0, markedBlockStart, markedBlockEnd))
    }

    @Test
    fun `typing right after the marked block leaves it unchanged`() {
        assertFalse(editChangesMarkedBlock(editOffset = 20, removedLength = 0, markedBlockStart, markedBlockEnd))
    }

    @Test
    fun `deleting text that overlaps the start of the marked block changes it`() {
        assertTrue(editChangesMarkedBlock(editOffset = 8, removedLength = 4, markedBlockStart, markedBlockEnd))
    }

    @Test
    fun `deleting text that ends exactly where the marked block starts leaves it unchanged`() {
        assertFalse(editChangesMarkedBlock(editOffset = 6, removedLength = 4, markedBlockStart, markedBlockEnd))
    }

    @Test
    fun `deleting text that starts exactly where the marked block ends leaves it unchanged`() {
        assertFalse(editChangesMarkedBlock(editOffset = 20, removedLength = 4, markedBlockStart, markedBlockEnd))
    }

    @Test
    fun `replacing text around the whole marked block changes it`() {
        assertTrue(editChangesMarkedBlock(editOffset = 5, removedLength = 30, markedBlockStart, markedBlockEnd))
    }
}
