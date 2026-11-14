package com.patrickreynolds.reviewmarks

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

private const val NOTIFICATION_GROUP_ID = "Review Marks"

fun showReviewMarksMessage(project: Project, message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification("Review Marks", message, NotificationType.INFORMATION)
        .notify(project)
}
