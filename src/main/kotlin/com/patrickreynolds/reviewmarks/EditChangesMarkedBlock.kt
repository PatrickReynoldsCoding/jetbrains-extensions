package com.patrickreynolds.reviewmarks

fun editChangesMarkedBlock(
    editOffset: Int,
    removedLength: Int,
    markedBlockStart: Int,
    markedBlockEnd: Int,
): Boolean {
    val editOnlyInsertsText = removedLength == 0
    if (editOnlyInsertsText) {
        return editOffset > markedBlockStart && editOffset < markedBlockEnd
    }
    val removedTextEnd = editOffset + removedLength
    return editOffset < markedBlockEnd && removedTextEnd > markedBlockStart
}
