package com.patrickreynolds.reviewmarks

import com.intellij.util.messages.Topic

fun interface ReviewStateListener {
    fun reviewStateChanged()

    companion object {
        val TOPIC: Topic<ReviewStateListener> = Topic.create("Review Marks state changed", ReviewStateListener::class.java)
    }
}
