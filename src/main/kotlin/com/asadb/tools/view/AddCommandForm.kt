// =====================================================================
// 新增命令弹窗（View 层）
// 对应 C# 版 AdbTool.View.AddCommandForm。
// 供用户输入「命令名称」与「adb 命令」，点击「确定」后保存新增命令。
// =====================================================================

package com.asadb.tools.view

import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 新增命令弹窗：无边框窗体，自绘标题栏、右上角关闭按钮与底部确定按钮。
 */
class AddCommandForm(owner: Component? = null) : BaseDialog("新增 ADB 命令", 540, 340, owner) {

    private val txtName = JTextField()
    private val txtCommand = JTextField()

    /** 用户输入的命令名称（去除首尾空白）。 */
    val commandName: String get() = txtName.text.trim()

    /** 用户输入的 adb 命令（去除首尾空白）。 */
    val adbCommand: String get() = txtCommand.text.trim()

    init {
        val top = buildTitleBar("新增 ADB 命令")
        val bottom = buildSingleOkFooter()
        val mid = buildContent()
        mount(top, mid, bottom)
    }

    private fun buildContent(): JPanel {
        val mid = JPanel()
        mid.background = UiColors.DialogBack
        mid.layout = null

        val fieldW = 540 - 60
        val fieldH = 34
        val labelColor = UiColors.LabelFore

        val lblName = JLabel("命令名称")
        lblName.foreground = labelColor
        lblName.setBounds(30, 26, fieldW, 24)
        mid.add(lblName)

        txtName.setBounds(30, 52, fieldW, fieldH)
        mid.add(txtName)

        val lblCmd = JLabel("adb命令")
        lblCmd.foreground = labelColor
        lblCmd.setBounds(30, 112, fieldW, 24)
        mid.add(lblCmd)

        txtCommand.setBounds(30, 138, fieldW, fieldH)
        mid.add(txtCommand)

        return mid
    }

    /** 底部：单个确定按钮（水平居中）。 */
    private fun buildSingleOkFooter(): JPanel {
        val bottom = JPanel()
        bottom.preferredSize = Dimension(1, 72)
        bottom.background = UiColors.DialogBack
        bottom.layout = FlowLayout(FlowLayout.CENTER, 0, 17)

        val btnOk = RoundedButton("确定")
        btnOk.preferredSize = Dimension(110, 38)
        btnOk.background = UiColors.Accent
        btnOk.foreground = UiColors.AccentFore
        btnOk.borderSize = 0
        btnOk.addActionListener { onOkClick() }
        bottom.add(btnOk)

        rootPane.defaultButton = btnOk
        return bottom
    }

    /** 确定按钮点击：校验输入后关闭。 */
    override fun onOkClick() {
        if (commandName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写命令名称！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        if (adbCommand.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写确切的 adb 命令！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        super.onOkClick()
    }
}
