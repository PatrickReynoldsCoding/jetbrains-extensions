package com.patrickreynolds.reviewmarks

import com.intellij.util.xmlb.annotations.Tag

data class StoredMark(
    var fileUrl: String = "",
    var startOffset: Int = 0,
    @Tag("markedText") var markedText: String = "",
    var state: String = MarkState.REVIEWED.name,
)

class ReviewMarksState(
    var marks: MutableList<StoredMark> = mutableListOf(),
    var foldMode: Boolean = false,
    var lastStop: StoredStop? = null,
    var notes: MutableList<StoredNote> = mutableListOf(),
    var reviewLog: MutableList<ReviewLogEntry> = mutableListOf(),
    var pullRequest: PullRequestSession? = null,
)
