package com.patrickreynolds.reviewmarks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo

fun escapedForAppleScriptString(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"")

fun macNotificationCommand(title: String, message: String, soundName: String?): List<String> {
    val soundClause = soundName?.let { " sound name \"${escapedForAppleScriptString(it)}\"" }.orEmpty()
    val displayNotificationScript =
        "display notification \"${escapedForAppleScriptString(message)}\" with title \"${escapedForAppleScriptString(title)}\"$soundClause"
    return listOf("osascript", "-e", displayNotificationScript)
}

object MacNotification {

    fun canSend(): Boolean = SystemInfo.isMac && !ApplicationManager.getApplication().isUnitTestMode

    fun send(title: String, message: String, soundName: String?, onFailure: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val sent = runCatching {
                ProcessBuilder(macNotificationCommand(title, message, soundName)).redirectErrorStream(true).start().waitFor() == 0
            }.getOrDefault(false)
            if (!sent) onFailure()
        }
    }
}
