package com.patrickreynolds.reviewmarks

data class StoredScopeFile(
    var path: String = "",
    var changedLines: MutableList<LineRange> = mutableListOf(),
)

data class StoredScope(
    var baseRef: String = "",
    var files: MutableList<StoredScopeFile> = mutableListOf(),
)
