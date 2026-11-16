package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAlertTest {

    private val second = 1000L

    @Test
    fun `cooldown has passed when no alert was ever sent`() {
        assertTrue(cooldownHasPassed(lastAlertAtMillis = null, nowMillis = 0, cooldownSeconds = 30))
    }

    @Test
    fun `cooldown has not passed 29 seconds after the last alert`() {
        assertFalse(cooldownHasPassed(lastAlertAtMillis = 100 * second, nowMillis = 129 * second, cooldownSeconds = 30))
    }

    @Test
    fun `cooldown has passed exactly 30 seconds after the last alert`() {
        assertTrue(cooldownHasPassed(lastAlertAtMillis = 100 * second, nowMillis = 130 * second, cooldownSeconds = 30))
    }

    @Test
    fun `sends the alert when enabled, reviewing and the cooldown has passed`() {
        assertTrue(shouldSendFocusAlert(true, true, null, 0, 30))
    }

    @Test
    fun `does not send the alert when it is switched off`() {
        assertFalse(shouldSendFocusAlert(false, true, null, 0, 30))
    }

    @Test
    fun `does not send the alert when not reviewing`() {
        assertFalse(shouldSendFocusAlert(true, false, null, 0, 30))
    }

    @Test
    fun `does not send a second alert inside the cooldown`() {
        assertFalse(shouldSendFocusAlert(true, true, 100 * second, 110 * second, 30))
    }

    @Test
    fun `reviewing means a pomodoro review period that is running`() {
        assertTrue(isReviewing(PomodoroState.Running(PomodoroPhase.WORKING, 0)))
        assertFalse(isReviewing(PomodoroState.Running(PomodoroPhase.ON_BREAK, 0)))
        assertFalse(isReviewing(PomodoroState.Paused(PomodoroPhase.WORKING, 0)))
        assertEquals(false, isReviewing(null))
    }

    @Test
    fun `plays the chosen sound only when the sound is switched on`() {
        assertEquals("Glass", focusAlertSoundName(soundEnabled = true, soundName = "Glass"))
        assertEquals(null, focusAlertSoundName(soundEnabled = false, soundName = "Glass"))
    }
}
