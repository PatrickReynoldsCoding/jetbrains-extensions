package com.patrickreynolds.reviewmarks

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

fun localFilesByPath(changedLinesByPath: Map<String, List<LineRange>>): Map<VirtualFile, List<LineRange>> =
    changedLinesByPath.mapNotNull { (path, changedLines) ->
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path)?.let { it to changedLines }
    }.toMap()
