package com.patrickreynolds.reviewmarks

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class FocusTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val focusRow = (value as? DefaultMutableTreeNode)?.userObject as? FocusRow ?: return
        icon = DotIcon(FOCUS_ACCENT)
        append(focusRow.fileName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        if (focusRow.firstLine == null) {
            append("   not found in the file", SimpleTextAttributes.ERROR_ATTRIBUTES)
        } else {
            append("   lines ${focusRow.firstLine}–${focusRow.lastLine}")
        }
        append("   ${focusRow.preview}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}
