// =====================================================================
// 功能按钮高亮管理器（View 层工具）
// 点击功能按钮后将其高亮为蓝底白字白框，切换时自动恢复上一个按钮的原始样式。
// 从 MainPanel 抽取，职责单一，与业务逻辑解耦。
// =====================================================================

package com.asadb.tools.view

import java.awt.Color
import javax.swing.JButton

/**
 * 功能按钮高亮管理器：记录按钮被高亮前的原始样式快照，切换时自动恢复。
 */
class ButtonHighlighter {

    /** 按钮高亮前样式快照（保存在按钮自身，避免多按钮切换时被覆盖）。 */
    private val buttonOriginals = java.util.IdentityHashMap<JButton, ButtonStyle>()

    /** 当前高亮的按钮。 */
    private var activeButton: JButton? = null

    /** 点击功能按钮后将其高亮为蓝底白字白框，并恢复上一个高亮按钮。 */
    fun highlight(btn: JButton) {
        if (activeButton === btn) return

        activeButton?.let { restoreHighlightedButton(it) }

        // 先快照按钮自身原始样式，再施加高亮样式
        buttonOriginals[btn] = ButtonStyle(
            btn.background,
            btn.foreground,
            (btn as? RoundedButton)?.borderColor,
            (btn as? RoundedButton)?.borderSize ?: 0
        )
        if (btn is RoundedButton) btn.setBackColor(UiColors.Accent) else btn.background = UiColors.Accent
        btn.foreground = Color.WHITE
        if (btn is RoundedButton) {
            btn.borderColor = Color.WHITE
            btn.borderSize = 1
        }
        activeButton = btn
    }

    /** 恢复按钮到高亮前的原始样式（使用该按钮自身保存的快照）。 */
    private fun restoreHighlightedButton(btn: JButton) {
        val style = buttonOriginals.remove(btn) ?: return
        if (btn is RoundedButton) btn.setBackColor(style.back) else btn.background = style.back
        btn.foreground = style.fore
        if (btn is RoundedButton) {
            btn.borderColor = style.border ?: btn.borderColor
            btn.borderSize = style.borderSize
            btn.repaint()
        }
    }

    /** 按钮高亮前的原始样式快照。 */
    private class ButtonStyle(
        val back: Color,
        val fore: Color,
        val border: Color?,
        val borderSize: Int
    )
}
