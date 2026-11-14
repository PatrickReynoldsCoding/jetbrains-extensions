package com.patrickreynolds.reviewmarks

fun unplacedText(fileName: String, removedMarkCount: Int, movedNoteCount: Int): String? {
    val sentences = listOfNotNull(
        when (removedMarkCount) {
            0 -> null
            1 -> "1 mark was removed because its code changed while the file was closed. Mark that code again when you have read it."
            else -> "$removedMarkCount marks were removed because their code changed while the file was closed. Mark that code again when you have read it."
        },
        when (movedNoteCount) {
            0 -> null
            1 -> "1 comment could not find its code, so it was put on the nearest line. Check it."
            else -> "$movedNoteCount comments could not find their code, so they were put on the nearest line. Check them."
        },
    )
    if (sentences.isEmpty()) return null
    return "$fileName: ${sentences.joinToString(" ")}"
}
