package com.patrickreynolds.reviewmarks

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton

class SidebarTabsEditor : JBPanel<SidebarTabsEditor>() {

    private var tabsInOrder: List<SidebarTab> = SidebarTab.entries
    private var hiddenTabs: Set<SidebarTab> = emptySet()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        showRows()
    }

    fun load(savedOrder: List<String>, hiddenTabNames: List<String>) {
        tabsInOrder = sidebarTabsInOrder(savedOrder)
        hiddenTabs = hiddenTabNames.mapNotNull { sidebarTabNamed(it) }.toSet()
        showRows()
    }

    fun orderNames(): MutableList<String> = tabsInOrder.map { it.name }.toMutableList()

    fun hiddenTabNames(): MutableList<String> = tabsInOrder.filter { it in hiddenTabs }.map { it.name }.toMutableList()

    private fun showRows() {
        removeAll()
        val visibleTabCount = tabsInOrder.count { it !in hiddenTabs }
        tabsInOrder.forEachIndexed { tabIndex, tab ->
            val showCheckBox = JBCheckBox(tab.title, tab !in hiddenTabs)
            showCheckBox.isEnabled = tab in hiddenTabs || visibleTabCount > 1
            showCheckBox.addActionListener {
                hiddenTabs = if (showCheckBox.isSelected) hiddenTabs - tab else hiddenTabs + tab
                showRows()
            }
            val moveUpButton = JButton("↑")
            moveUpButton.isEnabled = tabIndex > 0
            moveUpButton.addActionListener { moveTab(tabIndex, -1) }
            val moveDownButton = JButton("↓")
            moveDownButton.isEnabled = tabIndex < tabsInOrder.size - 1
            moveDownButton.addActionListener { moveTab(tabIndex, 1) }
            val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0))
            row.add(showCheckBox)
            row.add(moveUpButton)
            row.add(moveDownButton)
            add(row)
        }
        revalidate()
        repaint()
    }

    private fun moveTab(tabIndex: Int, placesToShift: Int) {
        val reordered = tabsInOrder.toMutableList()
        val movedTab = reordered.removeAt(tabIndex)
        reordered.add(tabIndex + placesToShift, movedTab)
        tabsInOrder = reordered
        showRows()
    }
}
