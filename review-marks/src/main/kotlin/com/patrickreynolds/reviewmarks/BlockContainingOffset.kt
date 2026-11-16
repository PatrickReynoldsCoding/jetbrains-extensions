package com.patrickreynolds.reviewmarks

fun blockContainingOffset(blocksInSidebar: List<BlockOfFile>, fileUrl: String, offset: Int): BlockOfFile? =
    blocksInSidebar.firstOrNull { block ->
        block.fileUrl == fileUrl && offset >= block.row.startOffset && offset <= block.row.endOffset
    }
