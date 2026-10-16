// =====================================================================
// 下拉组合按钮（View 层控件）
// 主按钮执行主操作，右侧下拉按钮弹出选择菜单（如「卸载应用」「重启设备」等）。
// 主按钮点击行为由外部通过 onMainClick 注入，菜单内容由构建函数按需填充。
// =====================================================================

package com.asadb.tools.view

import java.awt.Dimension
import javax.swing.JPopupMenu

/**
 * 下拉组合按钮：主按钮 + 右侧下拉按钮 + 关联菜单。
 *
 * @param buttonText 主按钮显示文本
 * @param emptyText  未选中任何项时下拉按钮的空态提示文本
 * @param buildMenu  每次弹出菜单前的填充函数（先 removeAll 再按需添加菜单项）
 */
class SplitButton(
    val buttonText: String,
    private val emptyText: String,
    private val buildMenu: (JPopupMenu) -> Unit
) {

    /** 主按钮。 */
    val mainButton = RoundedButton(buttonText).apply {
        preferredSize = Dimension(BTN_WIDTH, BTN_HEIGHT)
        borderSize = 1
    }

    /** 右侧下拉按钮。 */
    val arrowButton = RoundedButton("选择  ▼").apply {
        preferredSize = Dimension(BTN_WIDTH, BTN_HEIGHT)
        borderSize = 1
        arrowRightGlyph = true
    }

    /** 下拉菜单（每次弹出前由构建函数重新填充）。 */
    val menu = JPopupMenu()

    /** 主按钮点击回调：由外部注入业务动作。 */
    var onMainClick: () -> Unit = {}

    init {
        mainButton.addActionListener { onMainClick() }
        arrowButton.addActionListener {
            buildMenu(menu)
            if (menu.componentCount > 0) menu.show(arrowButton, 0, arrowButton.height)
        }
    }

    /** 更新下拉按钮显示文本与提示：选中项名字 + ▼，未选中时显示空态提示。 */
    fun updateTip(selected: String?) {
        arrowButton.text = fitArrowText(selected ?: emptyText)
        val hasSelection = !selected.isNullOrEmpty()
        val tip = if (hasSelection) "$buttonText：当前选中 $selected"
        else "$buttonText：点击右侧下拉按钮选择项目"
        mainButton.toolTipText = tip
        arrowButton.toolTipText = tip
    }

    companion object {
        const val BTN_WIDTH = 140
        const val BTN_HEIGHT = 46
    }

    private fun fitArrowText(text: String): String = (if (text.isEmpty()) "选择" else text) + "  ▼"
}
