package com.patrickreynolds.reviewmarks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.treeStructure.Tree
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode

private const val ID_SEPARATOR = "\n"

private const val DROPPED_ON_A_BLOCK = -1

class FocusTreeTransferHandler(
    private val focusTree: Tree,
    private val selectedFocusBlockIds: () -> List<String>,
    private val moveFocusBlocks: (movedBlockIds: List<String>, newParentId: String, indexAmongNewSiblings: Int) -> Unit,
) : TransferHandler() {

    override fun getSourceActions(component: JComponent): Int = MOVE

    override fun createTransferable(component: JComponent): Transferable? {
        val draggedBlockIds = selectedFocusBlockIds()
        return if (draggedBlockIds.isEmpty()) null else StringSelection(draggedBlockIds.joinToString(ID_SEPARATOR))
    }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDrop || !support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false
        support.setShowDropLocation(true)
        return support.dropLocation is JTree.DropLocation
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        val dropLocation = support.dropLocation as JTree.DropLocation
        val movedBlockIds = (support.transferable.getTransferData(DataFlavor.stringFlavor) as String).split(ID_SEPARATOR)
        val dropParentNode = dropLocation.path?.lastPathComponent as? DefaultMutableTreeNode ?: focusTree.model.root as DefaultMutableTreeNode
        val newParentId = (dropParentNode.userObject as? FocusRow)?.id ?: TOP_LEVEL_FOCUS_PARENT_ID
        val indexAmongNewSiblings = indexAmongSiblings(dropLocation, dropParentNode, movedBlockIds)
        ApplicationManager.getApplication().invokeLater { moveFocusBlocks(movedBlockIds, newParentId, indexAmongNewSiblings) }
        return true
    }

    private fun indexAmongSiblings(dropLocation: JTree.DropLocation, dropParentNode: DefaultMutableTreeNode, movedBlockIds: List<String>): Int {
        if (dropLocation.childIndex == DROPPED_ON_A_BLOCK) return AFTER_THE_LAST_SIBLING
        val movedBlocksBeforeDropPlace = (0 until dropLocation.childIndex).count { childIndex ->
            val childRow = (dropParentNode.getChildAt(childIndex) as DefaultMutableTreeNode).userObject as? FocusRow
            childRow?.id in movedBlockIds
        }
        return dropLocation.childIndex - movedBlocksBeforeDropPlace
    }
}
