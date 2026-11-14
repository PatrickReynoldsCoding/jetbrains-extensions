package com.patrickreynolds.reviewmarks

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

private const val DOT_SIZE = 10

class DotIcon(private val color: Color) : Icon {

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = color
            graphics2D.fillOval(x, y, iconWidth, iconHeight)
        } finally {
            graphics2D.dispose()
        }
    }

    override fun getIconWidth(): Int = JBUI.scale(DOT_SIZE)

    override fun getIconHeight(): Int = JBUI.scale(DOT_SIZE)
}
