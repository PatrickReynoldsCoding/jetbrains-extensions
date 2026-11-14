package com.patrickreynolds.reviewmarks

sealed interface FileTreeEntry<T> {
    data class Folder<T>(val name: String, val children: List<FileTreeEntry<T>>) : FileTreeEntry<T>
    data class File<T>(val name: String, val item: T) : FileTreeEntry<T>
}

fun <T> fileTreeOf(itemsByRelativePath: List<Pair<String, T>>): List<FileTreeEntry<T>> =
    entriesOf(itemsByRelativePath.map { (relativePath, item) -> relativePath.split('/').filter { it.isNotEmpty() } to item })

private fun <T> entriesOf(itemsByPathParts: List<Pair<List<String>, T>>): List<FileTreeEntry<T>> {
    val (filesHere, itemsInFolders) = itemsByPathParts.partition { (pathParts, _) -> pathParts.size == 1 }
    val folders = itemsInFolders
        .groupBy { (pathParts, _) -> pathParts.first() }
        .toSortedMap()
        .map { (folderName, itemsInFolder) ->
            val children = entriesOf(itemsInFolder.map { (pathParts, item) -> pathParts.drop(1) to item })
            joinedWithOnlyChildFolder(FileTreeEntry.Folder(folderName, children))
        }
    val files = filesHere
        .map { (pathParts, item) -> FileTreeEntry.File(pathParts.single(), item) }
        .sortedBy { it.name }
    return folders + files
}

private fun <T> joinedWithOnlyChildFolder(folder: FileTreeEntry.Folder<T>): FileTreeEntry<T> {
    val onlyChild = folder.children.singleOrNull()
    return if (onlyChild is FileTreeEntry.Folder) {
        FileTreeEntry.Folder("${folder.name}/${onlyChild.name}", onlyChild.children)
    } else {
        folder
    }
}
