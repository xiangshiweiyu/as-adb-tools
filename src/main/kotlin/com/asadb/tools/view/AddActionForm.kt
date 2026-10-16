// =====================================================================
// 自定义 Action 弹窗（View 层）
// 对应 C# 版 AdbTool.View.AddActionForm。
// 供用户输入「应用名称」（选填）「页面名称」「页面 action」（后两者必填）
// 以及可动态增删的 key-value 参数对，点击「确定」后返回新增的 action 启动项。
// =====================================================================

package com.asadb.tools.view

import com.asadb.tools.model.LaunchParameter
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.border.LineBorder

/**
 * 自定义 Action 弹窗：应用名称（选填） + 页面名称 + 页面 action + 动态 key-value 参数。
 */
class AddActionForm(owner: Component? = null) : BaseDialog("自定义 Action", 600, 600, owner) {

    private val txtAppName = JTextField()
    private val txtName = JTextField()
    private val txtAction = JTextField()
    private val paramRows = JPanel()
    private val rows = mutableListOf<ParameterRow>()

    /** 用户输入的应用名称（去除首尾空白，选填）。 */
    val appName: String get() = txtAppName.text.trim()

    /** 用户输入的页面名称（去除首尾空白）。 */
    val displayName: String get() = txtName.text.trim()

    /** 用户输入的页面 action（去除首尾空白）。 */
    val action: String get() = txtAction.text.trim()

    /** 用户输入的全部参数（仅保留 key 非空的有效项）。 */
    val parameters: List<LaunchParameter>
        get() = rows.mapNotNull { r ->
            if (r.key.isEmpty()) null else LaunchParameter(key = r.key, value = r.value)
        }

    init {
        val top = buildTitleBar("自定义 Action")
        val bottom = buildFooter("确定")
        val mid = buildContent()
        mount(top, mid, bottom)
    }

    private fun buildContent(): JPanel {
        val mid = JPanel()
        mid.background = UiColors.DialogBack
        mid.layout = null

        val fieldW = 600 - 60
        val fieldH = 34
        val labelColor = UiColors.LabelFore

        val lblAppName = JLabel("应用名称（选填）")
        lblAppName.foreground = labelColor
        lblAppName.setBounds(30, 20, fieldW, 24)
        mid.add(lblAppName)

        txtAppName.setBounds(30, 46, fieldW, fieldH)
        mid.add(txtAppName)

        val lblName = JLabel("页面名称")
        lblName.foreground = labelColor
        lblName.setBounds(30, 96, fieldW, 24)
        mid.add(lblName)

        txtName.setBounds(30, 122, fieldW, fieldH)
        mid.add(txtName)

        val lblAction = JLabel("页面action")
        lblAction.foreground = labelColor
        lblAction.setBounds(30, 172, fieldW, 24)
        mid.add(lblAction)

        txtAction.setBounds(30, 198, fieldW, fieldH)
        mid.add(txtAction)

        val lblParams = JLabel("参数（key - value）")
        lblParams.foreground = labelColor
        lblParams.setBounds(30, 254, fieldW, 24)
        mid.add(lblParams)

        val btnAdd = RoundedButton("＋ 增加参数")
        btnAdd.setBounds(30, 282, 130, 30)
        btnAdd.background = UiColors.Accent
        btnAdd.foreground = UiColors.AccentFore
        btnAdd.borderSize = 0
        btnAdd.addActionListener { addParameterRow("", "") }
        mid.add(btnAdd)

        paramRows.layout = BoxLayout(paramRows, BoxLayout.Y_AXIS)
        paramRows.background = UiColors.AreaBack

        val scroll = JScrollPane(paramRows)
        scroll.setBounds(30, 320, fieldW, 190)
        scroll.border = LineBorder(UiColors.Border)
        scroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        mid.add(scroll)

        return mid
    }

    /** 新增一对 key / value 输入框（成对同时增加）。 */
    private fun addParameterRow(key: String, value: String) {
        val row = JPanel()
        row.background = UiColors.AreaBack
        row.layout = FlowLayout(FlowLayout.LEFT, 4, 3)
        row.maximumSize = Dimension(Int.MAX_VALUE, 40)

        val txtKey = JTextField(key)
        txtKey.preferredSize = Dimension(200, 26)
        val txtValue = JTextField(value)
        txtValue.preferredSize = Dimension(200, 26)

        val btnRemove = RoundedButton("删除")
        btnRemove.preferredSize = Dimension(90, 28)
        btnRemove.borderSize = 1
        btnRemove.borderColor = UiColors.DangerBorder
        btnRemove.foreground = UiColors.DangerFore
        btnRemove.background = UiColors.DangerBack

        val r = ParameterRow(txtKey, txtValue)
        btnRemove.addActionListener { removeParameterRow(r) }

        row.add(txtKey)
        row.add(txtValue)
        row.add(btnRemove)

        rows.add(r)
        paramRows.add(row)
        paramRows.add(Box.createVerticalStrut(2))
        paramRows.revalidate()
        paramRows.repaint()
    }

    /** 删除一对 key / value 输入框（成对同时删除）。 */
    private fun removeParameterRow(r: ParameterRow) {
        val index = rows.indexOf(r)
        if (index < 0) return
        rows.removeAt(index)
        paramRows.remove(index * 2)      // 行面板
        paramRows.remove(index * 2)      // 间隔（删除后序号前移）
        paramRows.revalidate()
        paramRows.repaint()
    }

    /** 确定按钮：页面名称与页面 action 均必填。 */
    override fun onOkClick() {
        if (displayName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写页面名称！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        if (action.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写页面action！", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        super.onOkClick()
    }

    /** 参数行：承载一对 key / value 输入框。 */
    private class ParameterRow(val keyBox: JTextField, val valueBox: JTextField) {
        val key: String get() = keyBox.text.trim()
        val value: String get() = valueBox.text.trim()
    }
}
