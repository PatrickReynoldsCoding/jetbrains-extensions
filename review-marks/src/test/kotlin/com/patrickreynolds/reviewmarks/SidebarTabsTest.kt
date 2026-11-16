package com.patrickreynolds.reviewmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarTabsTest {

    @Test
    fun `the default order has the five tabs with current focus fourth`() {
        assertEquals(
            listOf(SidebarTab.TO_REVIEW, SidebarTab.REVIEWED, SidebarTab.NOTES, SidebarTab.CURRENT_FOCUS, SidebarTab.TODAY),
            sidebarTabsInOrder(DEFAULT_SIDEBAR_TAB_ORDER),
        )
    }

    @Test
    fun `a tab missing from the saved order is added at the end and unknown names are ignored`() {
        assertEquals(
            listOf(SidebarTab.TODAY, SidebarTab.TO_REVIEW, SidebarTab.REVIEWED, SidebarTab.NOTES, SidebarTab.CURRENT_FOCUS),
            sidebarTabsInOrder(listOf("TODAY", "GONE", "TO_REVIEW")),
        )
    }

    @Test
    fun `hidden tabs are left out of the visible tabs`() {
        assertEquals(
            listOf(SidebarTab.TO_REVIEW, SidebarTab.CURRENT_FOCUS),
            visibleSidebarTabs(DEFAULT_SIDEBAR_TAB_ORDER, hiddenTabNames = listOf("REVIEWED", "NOTES", "TODAY")),
        )
    }

    @Test
    fun `when every tab is hidden all tabs are shown so the sidebar is never empty`() {
        assertEquals(5, visibleSidebarTabs(DEFAULT_SIDEBAR_TAB_ORDER, hiddenTabNames = DEFAULT_SIDEBAR_TAB_ORDER).size)
    }

    @Test
    fun `the last visible tab cannot be hidden`() {
        val hiddenNames = listOf("REVIEWED", "NOTES", "CURRENT_FOCUS", "TODAY")

        assertEquals(hiddenNames, sidebarTabsHiddenAfterHiding(SidebarTab.TO_REVIEW, DEFAULT_SIDEBAR_TAB_ORDER, hiddenNames))
    }

    @Test
    fun `moving a tab right swaps it with the next visible tab and skips hidden ones`() {
        val actualOrder = sidebarTabOrderAfterMoving(DEFAULT_SIDEBAR_TAB_ORDER, SidebarTab.REVIEWED, 1, hiddenTabNames = listOf("NOTES"))

        assertEquals(listOf("TO_REVIEW", "CURRENT_FOCUS", "NOTES", "REVIEWED", "TODAY"), actualOrder)
    }

    @Test
    fun `the first tab cannot move left`() {
        assertEquals(DEFAULT_SIDEBAR_TAB_ORDER, sidebarTabOrderAfterMoving(DEFAULT_SIDEBAR_TAB_ORDER, SidebarTab.TO_REVIEW, -1, hiddenTabNames = emptyList()))
    }
}
