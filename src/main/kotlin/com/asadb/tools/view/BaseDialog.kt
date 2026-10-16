// =====================================================================
// 弹窗基类（View 层）
// 对应 C# 版 AdbTool.View.BaseDialog。
// 封装「无边框窗体 + 自绘标题栏 + 右上角关闭按钮 + 底部确定/取消按钮」
// 的通用外观与拖动逻辑，供各类自定义弹窗复用。
// =====================================================================

package com.asadb.tools.view

import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import java.awt.FlowLayout

/**
 * 自定义弹窗基类：统一标题栏、关闭按钮、底部居中确定/取消按钮与拖动支持。
 * 子类只需负责中间内容区与输入校验。
 */
abstract class BaseDialog(
    title: String,
    width: Int,
    height: Int,
    owner: Component? = null
) : JDialog(windowOf(owner), ModalityType.APPLICATION_MODAL) {

    init {
        this.title = title
        isUndecorated = true
        setSize(width, height)
        background = UiColors.DialogBack
        contentPane.background = UiColors.DialogBack
        isResizable = false
        setLocationRelativeTo(windowOf(owner))
    }

    /** 构建顶部标题栏（左侧标题 + 右上角关闭按钮），并绑定拖动支持。 */
    protected fun buildTitleBar(title: String): JPanel {
        val top = TitleBar()
        top.preferredSize = Dimension(1, 48)
        top.background = UiColors.Accent
        top.layout = null

        val lblTitle = JLabel(title)
        lblTitle.foreground = Color.WHITE
        lblTitle.font = lblTitle.font.deriveFont(UiMetrics.FONT_LARGE).deriveFont(java.awt.Font.BOLD)
        lblTitle.setBounds(20, 13, 300, 24)

        val btnClose = RoundedButton("×")
        btnClose.font = btnClose.font.deriveFont(UiMetrics.FONT_CLOSE).deriveFont(java.awt.Font.BOLD)
        // 高度减 1，让标题栏底部的分隔细线完整可见
        btnClose.setBounds(width - 48, 0, 48, 47)
        btnClose.cornerRadius = 0
        btnClose.background = UiColors.Accent
        btnClose.foreground = Color.WHITE
        btnClose.hoverBackColor = UiColors.CloseHover
        btnClose.borderSize = 0
        btnClose.addActionListener {
            isVisible = false
            dispose()
        }

        top.add(lblTitle)
        top.add(btnClose)

        registerDragSupport(top)
        return top
    }

    /** 弹窗标题栏：纯色背景 + 底部一条同色系加深的分隔细线，增强与内容区的层次。 */
    private class TitleBar : JPanel() {
        init {
            isOpaque = true
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val c = background ?: return
            // 以底色按比例加深绘制 1px 分隔线，浅/深主题下都能与内容区自然过渡
            g.color = Color(
                (c.red * 0.72f).toInt().coerceIn(0, 255),
                (c.green * 0.72f).toInt().coerceIn(0, 255),
                (c.blue * 0.72f).toInt().coerceIn(0, 255)
            )
            g.drawLine(0, height - 1, width, height - 1)
        }
    }

    /**
     * 构建底部按钮栏：确定 / 取消两个按钮水平居中。
     * 确定按钮触发 [onOkClick]，取消按钮关闭窗体。
     */
    protected fun buildFooter(okText: String): JPanel {
        val bottom = JPanel()
        bottom.preferredSize = Dimension(1, 72)
        bottom.background = UiColors.DialogBack
        bottom.layout = FlowLayout(FlowLayout.CENTER, 20, 17)

        val btnW = 110
        val btnH = 38

        val btnOk = RoundedButton(okText)
        btnOk.preferredSize = Dimension(btnW, btnH)
        btnOk.background = UiColors.Accent
        btnOk.foreground = UiColors.AccentFore
        btnOk.borderSize = 0
        btnOk.addActionListener { onOkClick() }

        val btnCancel = RoundedButton("取消")
        btnCancel.preferredSize = Dimension(btnW, btnH)
        btnCancel.background = UiColors.SecondaryBtnBack
        btnCancel.foreground = UiColors.SecondaryBtnFore
        btnCancel.borderSize = 1
        btnCancel.borderColor = UiColors.Border
        btnCancel.addActionListener {
            isVisible = false
            dispose()
        }

        rootPane.defaultButton = btnOk

        bottom.add(btnOk)
        bottom.add(btnCancel)
        return bottom
    }

    /** 是否通过「确定」按钮关闭；取消或直接关闭为 false。 */
    var confirmed: Boolean = false
        protected set

    /** 确定按钮点击处理；子类可覆写以进行输入校验，通过后再调用基类关闭。 */
    protected open fun onOkClick() {
        confirmed = true
        isVisible = false
        dispose()
    }

    /** 为指定控件区域绑定拖动支持（用于无边框窗体移动）。 */
    protected fun registerDragSupport(trigger: JPanel) {
        var dragging = false
        var dragOffset = Point()
        trigger.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragging = true
                dragOffset = e.point
            }

            override fun mouseReleased(e: MouseEvent) {
                dragging = false
            }
        })
        trigger.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (dragging) {
                    val w = windowOf(this@BaseDialog) ?: return
                    w.location = Point(w.x + e.x - dragOffset.x, w.y + e.y - dragOffset.y)
                }
            }
        })
    }

    /** 将内容面板置入窗体：先 Fill 区块，再 Top/Bottom，保证中部内容不被遮挡。 */
    protected fun mount(top: JPanel, mid: JPanel, bottom: JPanel) {
        contentPane.layout = java.awt.BorderLayout()
        contentPane.add(mid, java.awt.BorderLayout.CENTER)
        contentPane.add(top, java.awt.BorderLayout.NORTH)
        contentPane.add(bottom, java.awt.BorderLayout.SOUTH)
        // 弹窗内所有文字统一加粗显示
        boldAll(contentPane)
    }

    /** 将容器及全部后代控件的字体统一设为加粗。 */
    private fun boldAll(c: Component) {
        c.font = c.font.deriveFont(java.awt.Font.BOLD)
        if (c is java.awt.Container) {
            for (child in c.components) boldAll(child)
        }
    }

    companion object {
        /** 从任意组件解析所属 Window（无则返回 null）。 */
        fun windowOf(comp: Component?): Window? {
            if (comp == null) return null
            if (comp is Window) return comp
            return SwingUtilities.getWindowAncestor(comp)
        }
    }
}
