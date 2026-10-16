// =====================================================================
// 自定义包名弹窗（View 层）
// 对应 C# 版 AdbTool.View.AddPackageForm。
// 供用户输入「包名名称」与「包名」，点击「确定」后返回新增的包名启动项。
// =====================================================================

package com.asadb.tools.view

import java.awt.Component
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 自定义包名弹窗：包名名称 + 包名两个输入框，底部确定/取消水平居中。
 */
class AddPackageForm(owner: Component? = null) : BaseDialog("自定义包名", 460, 300, owner) {

    private val txtName = JTextField()
    private val txtPackage = JTextField()

    /** 用户输入的包名名称（去除首尾空白）。 */
    val displayName: String get() = txtName.text.trim()

    /** 用户输入的目标包名（去除首尾空白）。 */
    val packageName: String get() = txtPackage.text.trim()

    init {
        val top = buildTitleBar("自定义包名")
        val bottom = buildFooter("确定")
        val mid = buildContent()
        mount(top, mid, bottom)
    }

    private fun buildContent(): JPanel {
        val mid = JPanel()
        mid.background = UiColors.DialogBack
        mid.layout = null

        val fieldW = 460 - 60
        val fieldH = 34
        val labelColor = UiColors.LabelFore

        val lblName = JLabel("名称")
        lblName.foreground = labelColor
        lblName.setBounds(30, 26, fieldW, 24)
        mid.add(lblName)

        txtName.setBounds(30, 52, fieldW, fieldH)
        mid.add(txtName)

        val lblPackage = JLabel("包名")
        lblPackage.foreground = labelColor
        lblPackage.setBounds(30, 112, fieldW, 24)
        mid.add(lblPackage)

        txtPackage.setBounds(30, 138, fieldW, fieldH)
        mid.add(txtPackage)

        return mid
    }

    /** 确定按钮：包名必填（名称可选）。 */
    override fun onOkClick() {
        if (packageName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写包名！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        super.onOkClick()
    }
}
