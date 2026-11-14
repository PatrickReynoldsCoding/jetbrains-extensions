package com.patrickreynolds.reviewmarks

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager

fun AnActionEvent.reviewEditor(): Editor? =
    getData(CommonDataKeys.EDITOR) ?: project?.let { FileEditorManager.getInstance(it).selectedTextEditor }
