package com.patrickreynolds.reviewmarks

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

private const val NOTIFICATION_GROUP_ID = "Review Marks"
private const val NOTIFICATION_TITLE = "Review Marks"

fun showReviewMarksMessage(project: Project, message: String, soundName: String?) {
    if (MacNotification.canSend()) {
        MacNotification.send(NOTIFICATION_TITLE, message, soundName, onFailure = { showBalloon(project, message) })
    } else {
        showBalloon(project, message)
    }
}

private fun showBalloon(project: Project, message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(NOTIFICATION_TITLE, message, NotificationType.INFORMATION)
        .notify(project)
}
