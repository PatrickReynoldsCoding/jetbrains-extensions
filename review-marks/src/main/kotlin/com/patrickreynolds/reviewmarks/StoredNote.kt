package com.patrickreynolds.reviewmarks

data class StoredNote(
    var fileUrl: String = "",
    var startOffset: Int = 0,
    var anchorText: String = "",
    var text: String = "",
)
