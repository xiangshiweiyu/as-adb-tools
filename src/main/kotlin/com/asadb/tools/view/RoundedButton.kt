// =====================================================================
// 圆角按钮控件（View 层）
// 对应 C# 版 AdbTool.View.RoundedButton。
// 自绘圆角背景，支持悬停/按压颜色动画，文字对齐遵循 Swing 对齐设置。
// =====================================================================

package com.asadb.tools.view

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * 圆角按钮：圆角背景 + 描边，内置悬停/按压过渡动画，可用于导航与操作按钮。
 */
class RoundedButton(text: String = "") : JButton(text) {

    /** 圆角半径（像素），默认取全局统一值 [UiMetrics.RADIUS]。 */
    var cornerRadius: Int = UiMetrics.RADIUS

    /** 鼠标悬停时的背景色，若未设置则按背景色自动加亮。 */
    var hoverBackColor: Color? = null

    /** 鼠标按下时的背景色，若未设置则按背景色自动加深。 */
    var pressedBackColor: Color? = null

    /** 是否启用过渡动画，关闭后颜色变化即时生效。 */
    var animationEnabled: Boolean = true

    /**
     * 直接设置背景色并立即生效（跳过过渡动画）。
     * 自绘时使用的是动画色 [animatedBackColor]，仅修改 background 不会立即反映到界面上，
     * 因此程序化换色（高亮 / 恢复 / 模式切换）必须走本方法，同步重置动画状态。
     */
    fun setBackColor(color: Color) {
        background = color
        animatedBackColor = color
        targetBackColor = color
        repaint()
    }

    /** 下拉按钮样式：文字左对齐（距左 10px），▼ 箭头右对齐（距右 10px）。 */
    var arrowRightGlyph: Boolean = false

    /** 竖排文字模式：每个字符单独一行，整体水平/垂直居中（用于左侧纵向标签）。 */
    var verticalText: Boolean = false

    /** 边框宽度（像素），0 表示不绘制边框。 */
    var borderSize: Int = 0

    /** 边框颜色。 */
    var borderColor: Color = UiColors.DefaultBorder

    private var animatedBackColor: Color = background
    private var targetBackColor: Color = background
    private var pointerInside = false
    private var pointerDown = false
    private var pressProgress = 0f

    private val animationTimer = Timer(15) { animateFrame() }

    init {
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        isOpaque = false
        preferredSize = Dimension(100, 36)
        // 插件界面统一使用加粗字体；后续 deriveFont(尺寸) 会保留加粗样式
        font = font.deriveFont(Font.BOLD)
        animatedBackColor = background
        targetBackColor = background
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                pointerInside = true
                setAnimationTarget(hoverColor())
            }

            override fun mouseExited(e: MouseEvent) {
                pointerInside = false
                pointerDown = false
                setAnimationTarget(background)
            }

            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pointerDown = true
                    setAnimationTarget(pressedColor())
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                pointerDown = false
                setAnimationTarget(if (pointerInside) hoverColor() else background)
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val inset = (pressProgress * 2f).toInt()
            val rect = Rectangle(inset, inset, width - 1 - inset * 2, height - 1 - inset * 2)
            if (rect.width <= 0 || rect.height <= 0) return

            val bg = if (isEnabled) animatedBackColor else UiColors.DisabledBack
            val path = RoundRectangle2D.Float(
                rect.x.toFloat(), rect.y.toFloat(),
                rect.width.toFloat(), rect.height.toFloat(),
                cornerRadius.toFloat(), cornerRadius.toFloat()
            )
            g2.color = bg
            g2.fill(path)

            if (borderSize > 0) {
                g2.color = borderColor
                g2.stroke = BasicStroke(borderSize.toFloat())
                g2.draw(path)
            }

            val fg = if (isEnabled) foreground else UiColors.DisabledFore
            if (arrowRightGlyph) {
                drawArrowRightText(g2, rect, fg)
            } else {
                drawCenteredOrAlignedText(g2, rect, fg)
            }
        } finally {
            g2.dispose()
        }
    }

    /** 普通文本绘制：按 horizontalAlignment / verticalAlignment 对齐；竖排模式逐字分行绘制。 */
    private fun drawCenteredOrAlignedText(g2: Graphics2D, rect: Rectangle, fg: Color) {
        val text = this.text
        if (text.isEmpty()) return

        // 竖排模式：每字符一行，整体水平/垂直居中。
        // 以「按钮实际可用区域」为基准计算字号，强制上下各留 ≥10px、左右各留 ≥8px，
        // 无论按钮实际高度如何，文字都不会被截断。
        if (verticalText) {
            val chars = text.toCharArray()
            var f = font
            var mf = getFontMetrics(f)
            while (f.size > 8 &&
                (chars.size * mf.height + 20 > rect.height || mf.stringWidth(text) / chars.size + 8 > rect.width)
            ) {
                f = f.deriveFont((f.size - 1).toFloat())
                mf = getFontMetrics(f)
            }
            val sh = mf.height
            val blockH = chars.size * sh
            val charW = mf.stringWidth(text) / chars.size
            val x = rect.x + (rect.width - charW) / 2
            var y = rect.y + (rect.height - blockH) / 2 + mf.ascent
            g2.color = fg
            g2.font = f
            for (c in chars) {
                g2.drawString(c.toString(), x, y)
                y += sh
            }
            return
        }

        // 横排模式：可用宽度放不下时自动缩小字号，保证文字完整显示（不裁剪）
        var f = font
        var mf = getFontMetrics(f)
        val availW = rect.width - 16
        while (f.size > 8 && mf.stringWidth(text) > availW) {
            f = f.deriveFont((f.size - 1).toFloat())
            mf = getFontMetrics(f)
        }
        val sw = mf.stringWidth(text)
        val sh = mf.height
        val x = when (horizontalAlignment) {
            SwingConstants.LEFT -> rect.x + 12
            SwingConstants.RIGHT -> rect.x + rect.width - sw - 12
            else -> rect.x + (rect.width - sw) / 2
        }
        val y = rect.y + (rect.height - sh) / 2 + mf.ascent

        g2.color = fg
        g2.font = f
        g2.drawString(text, x, y)
    }

    /** 下拉按钮文本：主体文字左端对齐，▼ 右端对齐。 */
    private fun drawArrowRightText(g2: Graphics2D, rect: Rectangle, fg: Color) {
        val text = this.text
        if (text.isEmpty()) return
        var body = text
        var arrow = "▼"
        val idx = text.lastIndexOf(arrow)
        if (idx >= 0) {
            arrow = text.substring(idx)
            body = text.substring(0, idx).trimEnd()
        }

        // 左右各留 10px 内边距；主体文字放不下时自动缩小字号防截断
        val textRect = Rectangle(rect.x + 10, rect.y, rect.width - 20, rect.height)
        var f = font
        var mf = getFontMetrics(f)
        val availW = textRect.width - mf.stringWidth(arrow) - 8
        while (f.size > 8 && mf.stringWidth(body) > availW) {
            f = f.deriveFont((f.size - 1).toFloat())
            mf = getFontMetrics(f)
        }

        g2.color = fg
        g2.font = f

        val bodyY = textRect.y + (textRect.height - mf.height) / 2 + mf.ascent
        g2.drawString(body, textRect.x, bodyY)

        val arrowW = mf.stringWidth(arrow)
        g2.drawString(arrow, textRect.x + textRect.width - arrowW, bodyY)
    }

    private fun setAnimationTarget(color: Color) {
        targetBackColor = color
        if (!animationEnabled) {
            animatedBackColor = color
            pressProgress = if (pointerDown) 1f else 0f
            repaint()
            return
        }
        animationTimer.start()
    }

    private fun animateFrame() {
        animatedBackColor = blend(animatedBackColor, targetBackColor, 0.24f)
        val targetPress = if (pointerDown) 1f else 0f
        pressProgress += (targetPress - pressProgress) * 0.3f

        val colorDone = colorDistance(animatedBackColor, targetBackColor) < 4
        val pressDone = Math.abs(targetPress - pressProgress) < 0.03f
        if (colorDone) animatedBackColor = targetBackColor
        if (pressDone) pressProgress = targetPress

        repaint()
        if (colorDone && pressDone) animationTimer.stop()
    }

    private fun hoverColor(): Color {
        hoverBackColor?.let { return it }
        val amount = if (background.brightness() < 0.5f) 0.12f else 0.18f
        return blend(background, Color.WHITE, amount)
    }

    private fun pressedColor(): Color {
        pressedBackColor?.let { return it }
        return blend(background, Color.BLACK, 0.1f)
    }

    private fun Color.brightness(): Float =
        (0.299f * red + 0.587f * green + 0.114f * blue) / 255f

    private fun blend(from: Color, to: Color, amount: Float): Color {
        val a = amount.coerceIn(0f, 1f)
        return Color(
            (from.red + (to.red - from.red) * a).toInt(),
            (from.green + (to.green - from.green) * a).toInt(),
            (from.blue + (to.blue - from.blue) * a).toInt()
        )
    }

    private fun colorDistance(a: Color, b: Color): Int =
        Math.abs(a.red - b.red) + Math.abs(a.green - b.green) + Math.abs(a.blue - b.blue)
}
