package com.patrickreynolds.reviewmarks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo

fun escapedForAppleScriptString(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"")

private const val MAC_SYSTEM_SOUNDS_FOLDER = "/System/Library/Sounds"

fun macNotificationCommand(title: String, message: String): List<String> {
    val displayNotificationScript =
        "display notification \"${escapedForAppleScriptString(message)}\" with title \"${escapedForAppleScriptString(title)}\""
    return listOf("osascript", "-e", displayNotificationScript)
}

fun macSoundCommand(soundName: String): List<String>? =
    if (soundName in MAC_ALERT_SOUND_NAMES) listOf("afplay", "$MAC_SYSTEM_SOUNDS_FOLDER/$soundName.aiff") else null

object MacNotification {

    fun canSend(): Boolean = SystemInfo.isMac && !ApplicationManager.getApplication().isUnitTestMode

    fun send(title: String, message: String, soundName: String?, onFailure: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            soundName?.let { macSoundCommand(it) }?.let { playSound(it) }
            val sent = runCatching {
                ProcessBuilder(macNotificationCommand(title, message)).redirectErrorStream(true).start().waitFor() == 0
            }.getOrDefault(false)
            if (!sent) onFailure()
        }
    }

    private fun playSound(soundCommand: List<String>) {
        runCatching { ProcessBuilder(soundCommand).redirectErrorStream(true).start() }
    }
}
