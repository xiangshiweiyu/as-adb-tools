// =====================================================================
// 通用输入弹窗（View 层）
// 对应 C# 版 AdbTool.View.InputDialog。
// 用于需要用户输入单行文本的场景（如输入 IP、目标路径）。
// 复用 BaseDialog 的标题栏 + 底部居中按钮布局，输入框加宽加大。
// =====================================================================

package com.asadb.tools.view

import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 通用单行输入弹窗：自绘标题栏，输入框居中，底部居中「确定 / 取消」按钮。
 */
class InputDialog(
    owner: Component?,
    title: String,
    labelText: String,
    defaultText: String
) : BaseDialog(title, 480, 220, owner) {

    private val txtValue = JTextField(defaultText)

    /** 用户输入的内容（未修改则为默认值）。 */
    val inputValue: String
        get() = txtValue.text

    init {
        val mid = JPanel()
        mid.background = UiColors.DialogBack
        mid.layout = null

        val lbl = JLabel(labelText)
        lbl.foreground = UiColors.LabelFore
        lbl.font = lbl.font.deriveFont(Font.BOLD)
        lbl.setBounds(UiMetrics.DIALOG_PADDING, 26, 420, 24)
        mid.add(lbl)

        // 输入框加大字号与高度，便于看清并输入
        txtValue.font = txtValue.font.deriveFont(UiMetrics.FONT_INPUT).deriveFont(Font.BOLD)
        txtValue.preferredSize = Dimension(420, 38)
        txtValue.setBounds(UiMetrics.DIALOG_PADDING, 56, 420, 38)
        mid.add(txtValue)

        mount(buildTitleBar(title), mid, buildFooter("确定"))
    }

    companion object {
        /** 弹出输入框；确定返回输入内容（可能为空字符串），取消返回 null。 */
        fun prompt(title: String, labelText: String, defaultText: String, owner: Component? = null): String? {
            val dlg = InputDialog(owner, title, labelText, defaultText)
            dlg.isVisible = true
            return if (dlg.confirmed) dlg.inputValue else null
        }
    }
}
