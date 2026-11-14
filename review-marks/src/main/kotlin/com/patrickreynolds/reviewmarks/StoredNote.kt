package com.patrickreynolds.reviewmarks

data class StoredNote(
    var fileUrl: String = "",
    var startOffset: Int = 0,
    var anchorText: String = "",
    var text: String = "",
    var floating: Boolean = false,
    var kind: String = NoteKind.NOTE.name,
    var id: String = "",
)
