// =====================================================================
// Action 启动窗口（View 层）
// 对应 C# 版 AdbTool.View.LaunchActionForm。
// 下拉框展示已保存的 action 启动项供筛选，支持「自定义 Action」实时新增，
// 确定后返回所选启动项。新增/删除项会写入传入的列表中供上层持久化。
// =====================================================================

package com.asadb.tools.view

import com.asadb.tools.model.LaunchAction
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Action 启动窗口：下拉筛选 + 自定义新增，确定后返回所选 action 启动项。
 */
class LaunchActionForm(
    private val actions: MutableList<LaunchAction>,
    owner: Component? = null
) : BaseDialog("Action 启动", 560, 260, owner) {

    private val cmb = JComboBox<LaunchAction>()

    /** 用户确认启动的 action 启动项；取消为 null。 */
    var selectedAction: LaunchAction? = null
        private set

    init {
        val top = buildTitleBar("Action 启动")
        val bottom = buildFooter("启动")
        val mid = buildContent()
        mount(top, mid, bottom)
        loadItems()
    }

    private fun buildContent(): JPanel {
        val mid = JPanel()
        mid.background = UiColors.DialogBack
        mid.layout = null

        val fieldW = 560 - 60
        val labelColor = UiColors.LabelFore

        val lbl = JLabel("选择要启动的 Action：")
        lbl.foreground = labelColor
        lbl.setBounds(30, 24, fieldW, 24)
        mid.add(lbl)

        cmb.setBounds(30, 52, fieldW, 28)
        cmb.renderer = object : ListCellRenderer<LaunchAction> {
            private val label = JLabel().apply { isOpaque = true }
            override fun getListCellRendererComponent(
                list: JList<out LaunchAction>?,
                value: LaunchAction?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                label.text = value?.displayText ?: ""
                label.background = if (isSelected) list?.selectionBackground ?: UiColors.Accent else list?.background ?: UiColors.DialogBack
                label.foreground = if (isSelected) list?.selectionForeground ?: UiColors.AccentFore else UiColors.PanelFore
                return label
            }
        }
        mid.add(cmb)

        // 新增 / 删除两个按钮作为一个整体水平居中
        val btnGap = 16
        val btnCustomW = 130
        val btnDeleteW = 110
        val btnStartX = (560 - btnCustomW - btnGap - btnDeleteW) / 2

        val btnCustom = RoundedButton("自定义Action")
        btnCustom.setBounds(btnStartX, 96, btnCustomW, 34)
        btnCustom.background = UiColors.Accent
        btnCustom.foreground = UiColors.AccentFore
        btnCustom.borderSize = 0
        btnCustom.addActionListener { onCustomClick() }
        mid.add(btnCustom)

        val btnDelete = RoundedButton("删除")
        btnDelete.setBounds(btnStartX + btnCustomW + btnGap, 96, btnDeleteW, 34)
        btnDelete.borderSize = 1
        btnDelete.borderColor = UiColors.DangerBorder
        btnDelete.foreground = UiColors.DangerFore
        btnDelete.background = UiColors.DangerBack
        btnDelete.addActionListener { onDeleteClick() }
        mid.add(btnDelete)

        return mid
    }

    /** 删除当前选中的 action 启动项。 */
    private fun onDeleteClick() {
        val act = cmb.selectedItem as? LaunchAction ?: run {
            JOptionPane.showMessageDialog(this, "请先选择要删除的 Action 启动项！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        actions.remove(act)
        cmb.removeItem(act)
        if (cmb.itemCount > 0) cmb.selectedIndex = 0
    }

    /** 将已保存的启动项加载进下拉框。 */
    private fun loadItems() {
        cmb.removeAllItems()
        for (a in actions) cmb.addItem(a)
        if (cmb.itemCount > 0) cmb.selectedIndex = 0
    }

    /** 自定义新增：打开弹窗并把结果（含参数）写入下拉框与集合。 */
    private fun onCustomClick() {
        val dlg = AddActionForm(this)
        dlg.isVisible = true
        if (!dlg.confirmed) return
        val act = LaunchAction(
            name = dlg.displayName,
            appName = dlg.appName,
            action = dlg.action,
            parameters = dlg.parameters.toMutableList()
        )
        if (act.action.isBlank()) return

        actions.add(act)
        cmb.addItem(act)
        cmb.selectedItem = act
    }

    /** 确定（启动）按钮：需先选择一个启动项。 */
    override fun onOkClick() {
        val item = cmb.selectedItem as? LaunchAction
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请选择或新增一个 Action 启动项！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        selectedAction = item
        super.onOkClick()
    }
}
