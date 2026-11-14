package com.patrickreynolds.reviewmarks

data class StoredStop(
    var fileUrl: String = "",
    var startOffset: Int = 0,
    var anchorText: String = "",
)
