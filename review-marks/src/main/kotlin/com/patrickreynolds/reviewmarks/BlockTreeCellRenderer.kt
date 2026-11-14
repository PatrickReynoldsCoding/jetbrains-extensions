package com.patrickreynolds.reviewmarks

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.ui.CheckboxTree
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

class BlockTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer() {

    override fun customizeRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val text = textRenderer
        when (val shownItem = (value as? DefaultMutableTreeNode)?.userObject) {
            is FolderRow -> {
                text.icon = AllIcons.Nodes.Folder
                text.append(shownItem.name)
                text.append("   ${shownItem.codeLineCount} lines", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is FileRow -> {
                text.icon = FileTypeManager.getInstance().getFileTypeByFileName(shownItem.name).icon
                text.append(shownItem.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                text.append("   ${shownItem.fileBlocks.codeLineCount} lines", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is BlockOfFile -> {
                val blockRow = shownItem.row
                val kindColor = COLOR_BY_BLOCK_KIND.getValue(blockRow.kind)
                text.icon = DotIcon(kindColor)
                text.append("${LABEL_BY_BLOCK_KIND.getValue(blockRow.kind)} ", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, kindColor))
                text.append("lines ${blockRow.firstLine}–${blockRow.lastLine}")
                text.append("   ${blockRow.preview}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
