package com.patrickreynolds.reviewmarks

data class FileBlocks(val fileUrl: String, val rows: List<BlockRow>) {
    val fileName: String get() = fileUrl.substringAfterLast('/')
    val codeLineCount: Int get() = rows.sumOf { it.codeLineCount }
}
