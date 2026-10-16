// =====================================================================
// 包名启动窗口（View 层）
// 对应 C# 版 AdbTool.View.LaunchPackageForm。
// 下拉框展示已保存的包名启动项供筛选，支持「自定义包名」实时新增，
// 确定后返回所选启动项。新增/删除项会写入传入的列表中供上层持久化。
// =====================================================================

package com.asadb.tools.view

import com.asadb.tools.model.LaunchPackage
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * 包名启动窗口：下拉筛选 + 自定义新增，确定后返回所选包名启动项。
 */
class LaunchPackageForm(
    private val packages: MutableList<LaunchPackage>,
    owner: Component? = null
) : BaseDialog("包名启动", 560, 260, owner) {

    private val cmb = JComboBox<LaunchPackage>()

    /** 用户确认启动的包名启动项；取消为 null。 */
    var selectedPackage: LaunchPackage? = null
        private set

    init {
        val top = buildTitleBar("包名启动")
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

        val lbl = JLabel("选择要启动的包名：")
        lbl.foreground = labelColor
        lbl.setBounds(30, 24, fieldW, 24)
        mid.add(lbl)

        cmb.setBounds(30, 52, fieldW, 28)
        cmb.renderer = object : ListCellRenderer<LaunchPackage> {
            private val label = JLabel().apply { isOpaque = true }
            override fun getListCellRendererComponent(
                list: JList<out LaunchPackage>?,
                value: LaunchPackage?,
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

        val btnCustom = RoundedButton("自定义包名")
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

    /** 删除当前选中的包名启动项。 */
    private fun onDeleteClick() {
        val pkg = cmb.selectedItem as? LaunchPackage ?: run {
            JOptionPane.showMessageDialog(this, "请先选择要删除的包名启动项！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        packages.remove(pkg)
        cmb.removeItem(pkg)
        if (cmb.itemCount > 0) cmb.selectedIndex = 0
    }

    /** 将已保存的启动项加载进下拉框。 */
    private fun loadItems() {
        cmb.removeAllItems()
        for (p in packages) cmb.addItem(p)
        if (cmb.itemCount > 0) cmb.selectedIndex = 0
    }

    /** 自定义新增：打开弹窗并把结果写入下拉框与集合。 */
    private fun onCustomClick() {
        val dlg = AddPackageForm(this)
        dlg.isVisible = true
        if (!dlg.confirmed) return
        val pkg = LaunchPackage(name = dlg.displayName, packageName = dlg.packageName)
        if (pkg.packageName.isBlank()) return

        packages.add(pkg)
        cmb.addItem(pkg)
        cmb.selectedItem = pkg
    }

    /** 确定（启动）按钮：需先选择一个启动项。 */
    override fun onOkClick() {
        val item = cmb.selectedItem as? LaunchPackage
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请选择或新增一个包名启动项！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        selectedPackage = item
        super.onOkClick()
    }
}
