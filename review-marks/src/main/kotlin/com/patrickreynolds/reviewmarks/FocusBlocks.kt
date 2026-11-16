package com.patrickreynolds.reviewmarks

import com.intellij.util.xmlb.annotations.Tag

const val TOP_LEVEL_FOCUS_PARENT_ID = ""

data class StoredFocusBlock(
    var id: String = "",
    var parentId: String = TOP_LEVEL_FOCUS_PARENT_ID,
    var fileUrl: String = "",
    var startOffset: Int = 0,
    @Tag("markedText") var markedText: String = "",
)

fun focusBlocksNestedUnder(focusBlocks: List<StoredFocusBlock>, parentId: String): List<StoredFocusBlock> =
    focusBlocks.filter { it.parentId == parentId }

fun idsOfFocusBlockAndItsDescendants(focusBlocks: List<StoredFocusBlock>, focusBlockId: String): Set<String> {
    val childIds = focusBlocksNestedUnder(focusBlocks, focusBlockId).flatMap { idsOfFocusBlockAndItsDescendants(focusBlocks, it.id) }
    return setOf(focusBlockId) + childIds
}

fun focusBlocksWithout(focusBlocks: List<StoredFocusBlock>, removedIds: Collection<String>): List<StoredFocusBlock> {
    val idsToDrop = removedIds.flatMap { idsOfFocusBlockAndItsDescendants(focusBlocks, it) }.toSet()
    return focusBlocks.filterNot { it.id in idsToDrop }
}

fun focusBlocksAfterMoving(
    focusBlocks: List<StoredFocusBlock>,
    movedBlockId: String,
    newParentId: String,
    indexAmongNewSiblings: Int,
): List<StoredFocusBlock>? {
    val movedBlock = focusBlocks.firstOrNull { it.id == movedBlockId } ?: return null
    val newParentExists = newParentId == TOP_LEVEL_FOCUS_PARENT_ID || focusBlocks.any { it.id == newParentId }
    val newParentIsInsideMovedBlock = newParentId in idsOfFocusBlockAndItsDescendants(focusBlocks, movedBlockId)
    if (!newParentExists || newParentIsInsideMovedBlock) return null

    val newSiblingsWithoutMovedBlock = focusBlocksNestedUnder(focusBlocks, newParentId).filter { it.id != movedBlockId }
    val insertIndex = indexAmongNewSiblings.coerceIn(0, newSiblingsWithoutMovedBlock.size)
    val newSiblings = newSiblingsWithoutMovedBlock.take(insertIndex) +
        movedBlock.copy(parentId = newParentId) +
        newSiblingsWithoutMovedBlock.drop(insertIndex)
    val blocksOfOtherParents = focusBlocks.filter { it.parentId != newParentId && it.id != movedBlockId }
    val childrenByParentId = blocksOfOtherParents.groupBy { it.parentId } + (newParentId to newSiblings)
    return inDisplayOrder(childrenByParentId, TOP_LEVEL_FOCUS_PARENT_ID)
}

fun focusBlocksAfterIndenting(focusBlocks: List<StoredFocusBlock>, blockId: String): List<StoredFocusBlock>? {
    val block = focusBlocks.firstOrNull { it.id == blockId } ?: return null
    val siblings = focusBlocksNestedUnder(focusBlocks, block.parentId)
    val blockAbove = siblings.getOrNull(siblings.indexOfFirst { it.id == blockId } - 1) ?: return null
    return focusBlocksAfterMoving(focusBlocks, blockId, blockAbove.id, focusBlocksNestedUnder(focusBlocks, blockAbove.id).size)
}

fun focusBlocksAfterOutdenting(focusBlocks: List<StoredFocusBlock>, blockId: String): List<StoredFocusBlock>? {
    val block = focusBlocks.firstOrNull { it.id == blockId } ?: return null
    val parentBlock = focusBlocks.firstOrNull { it.id == block.parentId } ?: return null
    val parentsSiblings = focusBlocksNestedUnder(focusBlocks, parentBlock.parentId)
    val indexAfterParent = parentsSiblings.indexOfFirst { it.id == parentBlock.id } + 1
    return focusBlocksAfterMoving(focusBlocks, blockId, parentBlock.parentId, indexAfterParent)
}

fun focusBlocksAfterShiftingAmongSiblings(
    focusBlocks: List<StoredFocusBlock>,
    blockId: String,
    placesToShift: Int,
): List<StoredFocusBlock>? {
    val block = focusBlocks.firstOrNull { it.id == blockId } ?: return null
    val siblings = focusBlocksNestedUnder(focusBlocks, block.parentId)
    val shiftedIndex = siblings.indexOfFirst { it.id == blockId } + placesToShift
    if (shiftedIndex < 0 || shiftedIndex >= siblings.size) return null
    return focusBlocksAfterMoving(focusBlocks, blockId, block.parentId, shiftedIndex)
}

private fun inDisplayOrder(childrenByParentId: Map<String, List<StoredFocusBlock>>, parentId: String): List<StoredFocusBlock> =
    childrenByParentId[parentId].orEmpty().flatMap { child -> listOf(child) + inDisplayOrder(childrenByParentId, child.id) }

fun newFocusBlockId(): String = java.util.UUID.randomUUID().toString().take(8)

data class FocusRow(
    val id: String,
    val parentId: String,
    val fileUrl: String,
    val fileName: String,
    val firstLine: Int?,
    val lastLine: Int?,
    val preview: String,
    val startOffset: Int,
)
