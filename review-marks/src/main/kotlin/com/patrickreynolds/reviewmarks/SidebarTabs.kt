package com.patrickreynolds.reviewmarks

enum class SidebarTab(val title: String) {
    TO_REVIEW("To review"),
    REVIEWED("Reviewed"),
    NOTES("Notes"),
    CURRENT_FOCUS("Current focus"),
    TODAY("Today"),
}

val DEFAULT_SIDEBAR_TAB_ORDER: List<String> = SidebarTab.entries.map { it.name }

fun sidebarTabNamed(tabName: String): SidebarTab? = SidebarTab.entries.firstOrNull { it.name == tabName }

fun sidebarTabsInOrder(savedOrder: List<String>): List<SidebarTab> {
    val savedTabs = savedOrder.mapNotNull { sidebarTabNamed(it) }.distinct()
    val tabsMissingFromSavedOrder = SidebarTab.entries.filterNot { it in savedTabs }
    return savedTabs + tabsMissingFromSavedOrder
}

fun visibleSidebarTabs(savedOrder: List<String>, hiddenTabNames: Collection<String>): List<SidebarTab> {
    val visibleTabs = sidebarTabsInOrder(savedOrder).filterNot { it.name in hiddenTabNames }
    return visibleTabs.ifEmpty { sidebarTabsInOrder(savedOrder) }
}

fun sidebarTabOrderAfterMoving(savedOrder: List<String>, movedTab: SidebarTab, visibleTabsToShift: Int, hiddenTabNames: Collection<String>): List<String> {
    val allTabs = sidebarTabsInOrder(savedOrder)
    val visibleTabs = allTabs.filterNot { it.name in hiddenTabNames }
    val visibleIndex = visibleTabs.indexOf(movedTab)
    val neighbour = visibleTabs.getOrNull(visibleIndex + visibleTabsToShift) ?: return allTabs.map { it.name }
    val movedIndex = allTabs.indexOf(movedTab)
    val neighbourIndex = allTabs.indexOf(neighbour)
    val reordered = allTabs.toMutableList()
    reordered[movedIndex] = neighbour
    reordered[neighbourIndex] = movedTab
    return reordered.map { it.name }
}

fun sidebarTabsHiddenAfterHiding(tabToHide: SidebarTab, savedOrder: List<String>, hiddenTabNames: Collection<String>): List<String> {
    val tabsLeftVisible = visibleSidebarTabs(savedOrder, hiddenTabNames).filter { it != tabToHide }
    return if (tabsLeftVisible.isEmpty()) hiddenTabNames.toList() else (hiddenTabNames + tabToHide.name).distinct()
}
