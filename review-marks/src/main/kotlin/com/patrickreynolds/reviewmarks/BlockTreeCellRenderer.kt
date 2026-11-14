package com.patrickreynolds.reviewmarks

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class BlockOfFile(val fileUrl: String, val row: BlockRow)

private val LABEL_BY_BLOCK_KIND = mapOf(
    BlockKind.REVIEWED to "✓",
    BlockKind.CHANGED to "✎ changed",
    BlockKind.TO_REVIEW to "○ to review",
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
            is FileBlocks -> {
                append(shownItem.fileName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("   ${shownItem.codeLineCount} lines", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is BlockOfFile -> {
                val blockRow = shownItem.row
                append("${LABEL_BY_BLOCK_KIND.getValue(blockRow.kind)} ${blockRow.firstLine}–${blockRow.lastLine}")
                append("   ${blockRow.preview}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
