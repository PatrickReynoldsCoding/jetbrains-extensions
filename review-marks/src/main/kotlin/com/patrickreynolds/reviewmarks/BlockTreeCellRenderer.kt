package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class BlockOfFile(val fileUrl: String, val row: BlockRow)

data class FolderRow(val name: String, val codeLineCount: Int)

data class FileRow(val name: String, val fileBlocks: FileBlocks)

private val LABEL_BY_BLOCK_KIND = mapOf(
    BlockKind.REVIEWED to "Reviewed",
    BlockKind.CHANGED to "Changed",
    BlockKind.TO_REVIEW to "To review",
)

private val COLOR_BY_BLOCK_KIND = mapOf(
    BlockKind.REVIEWED to REVIEWED_ACCENT,
    BlockKind.CHANGED to CHANGED_ACCENT,
    BlockKind.TO_REVIEW to TO_REVIEW_ACCENT,
)

class BlockTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        when (val shownItem = (value as? DefaultMutableTreeNode)?.userObject) {
            is FolderRow -> {
                icon = AllIcons.Nodes.Folder
                append(shownItem.name)
                append("   ${shownItem.codeLineCount} lines", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is FileRow -> {
                icon = FileTypeManager.getInstance().getFileTypeByFileName(shownItem.name).icon
                append(shownItem.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("   ${shownItem.fileBlocks.codeLineCount} lines", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is BlockOfFile -> {
                val blockRow = shownItem.row
                val kindColor = COLOR_BY_BLOCK_KIND.getValue(blockRow.kind)
                icon = DotIcon(kindColor)
                append("${LABEL_BY_BLOCK_KIND.getValue(blockRow.kind)} ", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, kindColor))
                append("lines ${blockRow.firstLine}–${blockRow.lastLine}")
                append("   ${blockRow.preview}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
