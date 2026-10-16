// =====================================================================
// 主题色与界面度量常量定义（View 层）
// 通过 JBColor.namedColor() 绑定 IntelliJ 平台标准主题键，
// 使插件界面颜色精确跟随 Android Studio 当前主题（含第三方主题）。
// 未命中的键会回退到 JBColor(深色, 浅色) 默认值。
// 注意：JBColor(深色, 浅色) —— 第一个参数为深色主题颜色。
// 圆角、字号、间距等度量统一收敛在 UiMetrics，避免各处散落魔法数字。
// =====================================================================

package com.asadb.tools.view

import com.intellij.ui.JBColor
import java.awt.Color

object UiColors {

    // ===================== 跟随 IDE 主题的颜色 =====================

    /** 主面板背景（Panel.background）。 */
    val PanelBack = JBColor.namedColor("Panel.background", JBColor(0x3C3F41, 0xE8EDF5))

    /** 普通前景文字（Label.foreground）。 */
    val PanelFore = JBColor.namedColor("Label.foreground", JBColor(0xAFB1B3, 0x3C3C3C))

    /** 通用边框（Component.borderColor）。 */
    val Border = JBColor.namedColor("Component.borderColor", JBColor(0x515151, 0xC8C8C8))

    /** 弹窗内容区背景。 */
    val DialogBack = JBColor.namedColor("Panel.background", JBColor(Color(0x3C3F41), Color.WHITE))

    /** 设置弹窗左侧导航背景。 */
    val NavBack = JBColor.namedColor("SidePanel.background", JBColor(0x313335, 0xF5F6F8))

    /** 导航按钮文字。 */
    val NavFore = JBColor.namedColor("Label.foreground", JBColor(0xAFB1B3, 0x3C3C3C))

    /** 表单标签文字。 */
    val LabelFore = JBColor.namedColor("Label.foreground", JBColor(0xAFB1B3, 0x505050))

    /** 输入框/只读文本域背景。 */
    val FieldBack = JBColor.namedColor("TextField.background", JBColor(Color(0x3C3F41), Color.WHITE))

    /** 参数区等浅灰区域背景。 */
    val AreaBack = JBColor.namedColor("TextField.background", JBColor(0x3C3F41, 0xFAFAFA))

    /** 单选按钮文字。 */
    val RadioFore = JBColor.namedColor("Label.foreground", JBColor(0xAFB1B3, 0x282828))

    /** 主按钮 / 选中态强调色（Button.default.background，跟随 IDE 主按钮颜色）。 */
    val Accent = JBColor.namedColor("Button.default.background", JBColor(0x3B6EA5, 0x3478F6))

    /** 主按钮上的文字。 */
    val AccentFore = JBColor.namedColor("Button.default.foreground", JBColor(Color.WHITE, Color.WHITE))

    /** 次要按钮背景（取消、编辑快捷项）。 */
    val SecondaryBtnBack = JBColor.namedColor("Button.background", JBColor(0x4E5154, 0xEBEEF2))

    /** 次要按钮文字。 */
    val SecondaryBtnFore = JBColor.namedColor("Button.foreground", JBColor(0xAFB1B3, 0x3C3C3C))

    /** 顶部操作按钮描边。 */
    val TopBtnBorder = JBColor.namedColor("Component.borderColor", JBColor(0x5C5C5C, 0x8C8C8C))

    /** RoundedButton 禁用背景。 */
    val DisabledBack = JBColor.namedColor("Button.disabledBackground", JBColor(0x3A3C3E, 0xC8C8C8))

    /** RoundedButton 禁用文字。 */
    val DisabledFore = JBColor.namedColor("Button.disabledForeground", JBColor(0x6E7072, 0x828282))

    /** RoundedButton 默认描边。 */
    val DefaultBorder = JBColor.namedColor("Component.borderColor", JBColor(0x5C5F63, 0xAAAAAA))

    /** 未选中标签背景。 */
    val TabNormalBack = JBColor.namedColor("Panel.background", JBColor(0x3C3F41, 0xE8EDF5))

    /** 未选中标签文字。 */
    val TabNormalFore = JBColor.namedColor("Label.foreground", JBColor(0xAFB1B3, 0x3C3C3C))

    /** 未选中标签描边。 */
    val TabNormalBorder = JBColor.namedColor("Component.borderColor", JBColor(0x4E5053, 0xB4BAC6))

    /** 分组分隔线 / 标题栏分隔细线颜色（浅于通用边框，避免生硬）。 */
    val Separator = JBColor.namedColor("Separator.separatorColor", JBColor(0x4E5054, 0xD0D5DD))

    /** 设置弹窗左侧导航项的悬停背景。 */
    val NavHover = JBColor.namedColor("List.hoverBackground", JBColor(0x3D4043, 0xE7EBF0))

    // ===================== 插件自有语义色（深浅双套） =====================

    /** 弹窗关闭按钮悬停背景（警示红，深浅主题保持一致）。 */
    val CloseHover = JBColor(0xE81123, 0xE81123)

    /** 删除类实心按钮背景（鲜明警示红，白字，一眼可辨）。 */
    val DangerSolidBack = JBColor(0xC62828, 0xD32F2F)

    /** 删除类浅色背景（删除模式下按钮底色，深色主题下仍保留可辨识的红色）。 */
    val DangerBack = JBColor(0x8A3333, 0xFFE4E1)

    /** 删除类文字（深红/浅红）。 */
    val DangerFore = JBColor(0xFF8A80, 0xBE2D2D)

    /** 删除类描边。 */
    val DangerBorder = JBColor(0xC62828, 0xD32F2F)

    /** 动态命令按钮背景（浅蓝系）。 */
    val DynamicBack = JBColor(0x26384A, 0xEDF6FF)

    val DynamicHover = JBColor(0x2F4359, 0xDCE9FF)

    val DynamicPressed = JBColor(0x1F3144, 0xC6E1FF)

    val DynamicFore = JBColor(0x6FB1F5, 0x0866D6)

    val DynamicBorder = JBColor(0x3A5E80, 0xBCD9F9)
}

/**
 * 界面度量常量：圆角、字号、间距的统一入口。
 * 集中定义以便全局保持一致，避免同类控件在各文件里各写一套数值。
 */
object UiMetrics {

    /** 按钮 / 输入控件标准圆角半径（像素）。 */
    const val RADIUS = 12

    /** 左侧竖向功能标签与设置导航的字号。 */
    const val FONT_TAB = 13f

    /** 弹窗标题、说明重点文字的字号。 */
    const val FONT_LARGE = 14f

    /** 弹窗右上角关闭按钮的字号。 */
    const val FONT_CLOSE = 18f

    /** 弹窗输入框字号。 */
    const val FONT_INPUT = 16f

    /** 弹窗内容区左右留白（像素）。 */
    const val DIALOG_PADDING = 30

    /** 主面板四周留白（像素）。 */
    const val PANEL_PADDING = 10

    /** 功能按钮行列间距（像素）。 */
    const val BUTTON_GAP = 10
}
