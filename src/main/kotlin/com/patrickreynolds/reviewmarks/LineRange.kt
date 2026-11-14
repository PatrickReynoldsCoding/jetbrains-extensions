package com.patrickreynolds.reviewmarks

data class LineRange(
    var firstLine: Int = 0,
    var lastLine: Int = 0,
) {
    fun contains(lineNumber: Int): Boolean = lineNumber in firstLine..lastLine
}
