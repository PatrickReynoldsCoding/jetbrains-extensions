package com.patrickreynolds.reviewmarks

import com.intellij.util.messages.Topic

fun interface ReviewNotesListener {
    fun notesChanged()

    companion object {
        val TOPIC: Topic<ReviewNotesListener> = Topic.create("Review Marks notes changed", ReviewNotesListener::class.java)
    }
}
