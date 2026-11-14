package com.patrickreynolds.reviewmarks

import com.intellij.openapi.util.TextRange

data class FileRanges(
    val documentText: String,
    val reviewed: List<TextRange>,
    val changed: List<TextRange>,
    val outOfScope: List<TextRange>,
)
