// =====================================================================
// 设置弹窗（View 层）
// 对应 C# 版 AdbTool.View.SettingsForm。
// 左侧导航 + 右侧内容，分为「页面修改」「缓存配置」「签名配置」「关于」等功能页。
// 点击「确定」返回全部设置项，由主面板统一持久化并应用。
// =====================================================================

package com.asadb.tools.view

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JColorChooser
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SwingConstants

/**
 * 设置弹窗：无边框窗体，自绘标题栏，左侧导航切换右侧内容。
 */
class SettingsForm(
    currentSize: Int,
    currentFont: String,
    currentFontColor: Color,
    currentBackColor: Color,
    currentAdbPath: String,
    currentCacheDir: String,
    currentMediaDir: String,
    currentApkDir: String,
    currentSignApkDirectory: String,
    currentSignKeystorePath: String,
    currentScrcpyPath: String,
    currentApksignerPath: String,
    currentJavaPath: String,
    owner: Component? = null
) : BaseDialog("设置", DIALOG_WIDTH, 560, owner) {

    companion object {
        private const val DIALOG_WIDTH = 760
        private const val NAV_WIDTH = 150
        private const val NAV_INSET = 8          // 导航按钮距左侧导航栏边缘的内缩（圆角胶囊高亮留白）
        private const val NAV_ROW_H = 46         // 导航行高
        private const val NAV_ROW_GAP = 10       // 导航行间距
        private const val PAGE_PAGE = "page"
        private const val PAGE_CACHE = "cache"
        private const val PAGE_SIGN = "sign"
        private const val PAGE_TOOLS = "tools"
        private const val PAGE_ABOUT = "about"
    }

    // ---- 页面修改 ----
    private val cmbSize = JComboBox<String>()
    private val cmbFont = JComboBox<String>()
    private val btnFontColor = RoundedButton()
    private val btnBackColor = RoundedButton()
    private var fontColor = currentFontColor
    private var backColor = currentBackColor

    // ---- 缓存配置 ----
    private val txtAdbPath = JTextField(currentAdbPath)
    private val txtCacheDir = JTextField(currentCacheDir)
    private val txtMediaDir = JTextField(currentMediaDir)
    private val txtApkDir = JTextField(currentApkDir)
    private val txtSignApkDir = JTextField(currentSignApkDirectory)
    private val txtSignKeystore = JTextField(currentSignKeystorePath)

    // ---- 工具配置 ----
    private val txtScrcpyPath = JTextField(currentScrcpyPath)
    private val txtApksignerPath = JTextField(currentApksignerPath)
    private val txtJavaPath = JTextField(currentJavaPath)

    // ---- 布局 ----
    // 右侧内容用 CardLayout 承载各功能页，保证页面可正常显示并互不重叠
    private val contentHost = JPanel(CardLayout())
    private lateinit var pagePanel: JPanel
    private lateinit var cachePanel: JPanel
    private lateinit var cacheScroll: JScrollPane
    private lateinit var signPanel: JPanel
    private lateinit var signScroll: JScrollPane
    private lateinit var toolsPanel: JPanel
    private lateinit var toolsScroll: JScrollPane
    private lateinit var aboutPanel: JPanel
    private lateinit var navPage: RoundedButton
    private lateinit var navCache: RoundedButton
    private lateinit var navSign: RoundedButton
    private lateinit var navTools: RoundedButton
    private lateinit var navAbout: RoundedButton

    // ---- 页面修改结果 ----
    val selectedFontSize: Int get() = cmbSize.selectedItem?.toString()?.toIntOrNull() ?: 20
    val selectedFontName: String get() = cmbFont.selectedItem?.toString() ?: "Consolas"
    val selectedFontColor: Color get() = fontColor
    val selectedBackColor: Color get() = backColor

    // ---- 缓存配置结果 ----
    val selectedAdbPath: String get() = txtAdbPath.text.trim()
    val selectedCacheDirectory: String get() = txtCacheDir.text.trim()
    val selectedMediaDirectory: String get() = txtMediaDir.text.trim()
    val selectedApkDirectory: String get() = txtApkDir.text.trim()
    val selectedSignApkDirectory: String get() = txtSignApkDir.text.trim()
    val selectedSignKeystorePath: String get() = txtSignKeystore.text.trim()

    // ---- 工具配置结果 ----
    val selectedScrcpyPath: String get() = txtScrcpyPath.text.trim()
    val selectedApksignerPath: String get() = txtApksignerPath.text.trim()
    val selectedJavaPath: String get() = txtJavaPath.text.trim()

    init {
        initContent(currentSize, currentFont)
        switchTo(pagePanel, navPage)
    }

    private fun initContent(currentSize: Int, currentFont: String) {
        // ==== 右侧内容容器 ====
        contentHost.background = UiColors.DialogBack

        initPagePanel(currentSize, currentFont)
        initCachePanel()
        initSignPanel()
        initToolsPanel()
        initAboutPanel()
        contentHost.add(pagePanel, PAGE_PAGE)
        contentHost.add(cacheScroll, PAGE_CACHE)
        contentHost.add(signScroll, PAGE_SIGN)
        contentHost.add(toolsScroll, PAGE_TOOLS)
        contentHost.add(aboutPanel, PAGE_ABOUT)

        // ==== 左侧导航 ====
        val leftNav = JPanel()
        leftNav.preferredSize = Dimension(NAV_WIDTH, 1)
        leftNav.background = UiColors.NavBack
        leftNav.layout = null

        navPage = createNavButton("页面修改")
        navCache = createNavButton("缓存配置")
        navSign = createNavButton("签名配置")
        navTools = createNavButton("工具配置")
        navAbout = createNavButton("关于")
        applyNavLayout()
        navPage.addActionListener { switchTo(pagePanel, navPage) }
        navCache.addActionListener { switchTo(cachePanel, navCache) }
        navSign.addActionListener { switchTo(signScroll, navSign) }
        navTools.addActionListener { switchTo(toolsScroll, navTools) }
        navAbout.addActionListener { switchTo(aboutPanel, navAbout) }
        leftNav.add(navPage)
        leftNav.add(navCache)
        leftNav.add(navSign)
        leftNav.add(navTools)
        leftNav.add(navAbout)

        // ==== 底部：取消 / 确定按钮 ====
        val bottom = JPanel()
        bottom.preferredSize = Dimension(1, 72)
        bottom.background = UiColors.DialogBack
        bottom.layout = null

        val btnW = 110
        val btnH = 38
        val btnGap = 20
        val btnY = (72 - btnH) / 2
        // 两个按钮作为整体在右侧内容区水平居中
        val contentW = DIALOG_WIDTH - NAV_WIDTH
        val btnStartX = NAV_WIDTH + (contentW - (btnW * 2 + btnGap)) / 2

        val btnCancel = RoundedButton("取消")
        btnCancel.setBounds(btnStartX, btnY, btnW, btnH)
        btnCancel.background = UiColors.SecondaryBtnBack
        btnCancel.foreground = UiColors.SecondaryBtnFore
        btnCancel.borderSize = 0
        btnCancel.addActionListener {
            isVisible = false
            dispose()
        }
        bottom.add(btnCancel)

        val btnOk = RoundedButton("确定")
        btnOk.setBounds(btnStartX + btnW + btnGap, btnY, btnW, btnH)
        btnOk.background = UiColors.Accent
        btnOk.foreground = UiColors.AccentFore
        btnOk.borderSize = 0
        btnOk.addActionListener {
            confirmed = true
            isVisible = false
            dispose()
        }
        bottom.add(btnOk)

        rootPane.defaultButton = btnOk

        // ==== 顶部：标题栏 ====
        val top = buildTitleBar("设置")

        // 按「Fill → Left → Bottom → Top」顺序添加，保证各区域互不遮挡
        contentPane.layout = java.awt.BorderLayout()
        contentPane.add(contentHost, java.awt.BorderLayout.CENTER)
        contentPane.add(leftNav, java.awt.BorderLayout.WEST)
        contentPane.add(bottom, java.awt.BorderLayout.SOUTH)
        contentPane.add(top, java.awt.BorderLayout.NORTH)
    }

    /** 创建左侧导航按钮：圆角胶囊样式，文字左对齐并留出内边距。 */
    private fun createNavButton(text: String): RoundedButton {
        val btn = RoundedButton(text)
        btn.font = btn.font.deriveFont(UiMetrics.FONT_TAB).deriveFont(Font.BOLD)
        btn.horizontalAlignment = SwingConstants.LEFT
        btn.borderSize = 0
        return btn
    }

    /** 切换右侧内容面板，并同步导航高亮。 */
    private fun switchTo(target: JComponent, navBtn: RoundedButton) {
        val cardName = when (target) {
            pagePanel -> PAGE_PAGE
            cachePanel -> PAGE_CACHE
            signPanel, signScroll -> PAGE_SIGN
            toolsPanel, toolsScroll -> PAGE_TOOLS
            else -> PAGE_ABOUT
        }
        (contentHost.layout as CardLayout).show(contentHost, cardName)
        styleNavButton(navPage, navPage === navBtn)
        styleNavButton(navCache, navCache === navBtn)
        styleNavButton(navSign, navSign === navBtn)
        styleNavButton(navTools, navTools === navBtn)
        styleNavButton(navAbout, navAbout === navBtn)
    }

    /** 重排左侧导航位置：依次「页面修改」「缓存配置」「签名配置」「工具配置」「关于」。 */
    private fun applyNavLayout() {
        val x = NAV_INSET
        val w = NAV_WIDTH - NAV_INSET * 2
        val step = NAV_ROW_H + NAV_ROW_GAP
        var y = 30
        navPage.setBounds(x, y, w, NAV_ROW_H)
        y += step
        navCache.setBounds(x, y, w, NAV_ROW_H)
        y += step
        navSign.setBounds(x, y, w, NAV_ROW_H)
        y += step
        navTools.setBounds(x, y, w, NAV_ROW_H)
        y += step
        navAbout.setBounds(x, y, w, NAV_ROW_H)
    }

    /** 应用导航按钮样式：选中为强调色胶囊，未选中带悬停高亮。 */
    private fun styleNavButton(btn: RoundedButton, active: Boolean) {
        btn.setBackColor(if (active) UiColors.Accent else UiColors.NavBack)
        btn.foreground = if (active) UiColors.AccentFore else UiColors.NavFore
        // 选中态悬停时按强调色自动加亮；未选中态使用专门的悬停底色
        btn.hoverBackColor = if (active) null else UiColors.NavHover
    }

    /** 设置弹窗内所有字体统一加粗。 */
    private fun boldFont(c: Component) {
        c.font = c.font.deriveFont(Font.BOLD)
    }

    /** 对面板内全部子控件应用加粗字体。 */
    private fun boldAll(parent: JPanel) {
        for (c in parent.components) boldFont(c)
    }

    /** 构建「页面修改」面板：字体/颜色设置。 */
    private fun initPagePanel(currentSize: Int, currentFont: String) {
        pagePanel = JPanel()
        pagePanel.background = UiColors.DialogBack
        pagePanel.layout = null

        // 行式布局辅助：行坐标自动推进，避免手写 y 导致控件重叠
        val rows = RowPlacer(pagePanel)

        rows.field("字体大小（15 ~ 100，步进 5）", cmbSize)
        for (i in 15..100 step 5) cmbSize.addItem(i.toString())
        cmbSize.selectedItem = if (cmbSize.itemCount > 0) {
            val sizeItems = (0 until cmbSize.itemCount).map { cmbSize.getItemAt(it) }
            if (sizeItems.contains(currentSize.toString())) currentSize.toString() else cmbSize.getItemAt(0)
        } else null

        rows.field("字体", cmbFont)
        val isMac = System.getProperty("os.name").contains("mac", ignoreCase = true)
        val fonts = if (isMac) {
            // macOS 本地字体：等宽（Menlo/Monaco/SF Mono）与中文（苹方/冬青黑体）优先
            arrayOf("Menlo", "SF Mono", "Monaco", "PingFang SC", "Hiragino Sans GB",
                "Consolas", "Arial", "Times New Roman", "Courier New")
        } else {
            arrayOf("Consolas", "Arial", "Microsoft YaHei UI", "Microsoft YaHei",
                "SimSun", "SimHei", "KaiTi", "Times New Roman", "Courier New", "Segoe UI")
        }
        fonts.forEach { cmbFont.addItem(it) }
        selectFontItem(currentFont)

        rows.field("字体颜色", btnFontColor)
        btnFontColor.addActionListener {
            val c = JColorChooser.showDialog(this, "选择字体颜色", fontColor)
            if (c != null) {
                fontColor = c
                refreshColorButton(btnFontColor, fontColor)
            }
        }

        rows.field("命令窗背景颜色", btnBackColor)
        btnBackColor.addActionListener {
            val c = JColorChooser.showDialog(this, "选择背景颜色", backColor)
            if (c != null) {
                backColor = c
                refreshColorButton(btnBackColor, backColor)
            }
        }

        refreshColorButton(btnFontColor, fontColor)
        refreshColorButton(btnBackColor, backColor)

        boldAll(pagePanel)
    }

    /** 构建「缓存配置」面板：adb配置、缓存目录、日志/视频/截图保存目录、待安装应用目录。 */
    private fun initCachePanel() {
        cachePanel = JPanel()
        cachePanel.background = UiColors.DialogBack
        cachePanel.layout = null

        val rows = RowPlacer(cachePanel)
        txtAdbPath.isEditable = false
        rows.field("adb配置", txtAdbPath) { pickAdbFile() }
        txtCacheDir.isEditable = false
        rows.field("缓存目录（包名/Action 启动项缓存文件）", txtCacheDir) { pickDirectory("请选择缓存目录") }
        txtMediaDir.isEditable = false
        rows.field("日志 / 视频 / 截图保存目录", txtMediaDir) { pickDirectory("请选择日志/视频/截图保存目录") }
        txtApkDir.isEditable = false
        rows.field("待安装应用目录", txtApkDir) { pickDirectory("请选择待安装应用目录") }
        txtSignApkDir.isEditable = false
        rows.field("待签名apk文件夹", txtSignApkDir) { pickDirectory("请选择待签名apk文件夹") }

        boldAll(cachePanel)

        // 5 行配置项超出内容区高度时用滚动容器承载
        cachePanel.preferredSize = Dimension(DIALOG_WIDTH - NAV_WIDTH, rows.contentHeight())
        cacheScroll = JScrollPane(cachePanel)
        cacheScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        cacheScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        cacheScroll.border = BorderFactory.createEmptyBorder()
        cacheScroll.background = UiColors.DialogBack
        cacheScroll.viewport.background = UiColors.DialogBack
    }

    /** 构建「签名配置」面板：签名所用的密钥库文件路径选择（常驻展示）。 */
    private fun initSignPanel() {
        signPanel = JPanel()
        signPanel.background = UiColors.DialogBack
        signPanel.layout = null

        val rows = RowPlacer(signPanel)
        txtSignKeystore.isEditable = false
        rows.field("签名文件（密钥库文件 .keystore / .jks，未配置时无法签名）", txtSignKeystore) {
            pickKeystoreFile("选择签名文件")
        }

        boldAll(signPanel)

        // 配置项较少，滚动容器仅作布局兜底
        signPanel.preferredSize = Dimension(DIALOG_WIDTH - NAV_WIDTH, rows.contentHeight())
        signScroll = JScrollPane(signPanel)
        signScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        signScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        signScroll.border = BorderFactory.createEmptyBorder()
        signScroll.background = UiColors.DialogBack
        signScroll.viewport.background = UiColors.DialogBack
    }

    /**
     * 构建「工具配置」面板：三个外部可执行文件路径（scrcpy / apksigner / java）。
     * 均允许留空，留空时由插件按 PATH、环境变量与 SDK 位置自动探测。
     */
    private fun initToolsPanel() {
        toolsPanel = JPanel()
        toolsPanel.background = UiColors.DialogBack
        toolsPanel.layout = null

        val rows = RowPlacer(toolsPanel)
        txtScrcpyPath.isEditable = false
        rows.field("scrcpy 路径（留空自动探测，屏幕投影使用）", txtScrcpyPath) {
            pickExecutableFile("选择 scrcpy 可执行文件", "exe", "")
        }
        txtApksignerPath.isEditable = false
        rows.field("apksigner 路径（留空自动探测，签名 / 校验使用）", txtApksignerPath) {
            pickExecutableFile("选择 apksigner 可执行文件 / jar", "exe", "bat", "jar", "")
        }
        txtJavaPath.isEditable = false
        rows.field("java 路径（留空使用 IDE 自带 JVM，签名 .jar 时使用）", txtJavaPath) {
            pickExecutableFile("选择 java 可执行文件", "exe", "")
        }

        boldAll(toolsPanel)

        // 配置项超出内容区高度时用滚动容器承载
        toolsPanel.preferredSize = Dimension(DIALOG_WIDTH - NAV_WIDTH, rows.contentHeight())
        toolsScroll = JScrollPane(toolsPanel)
        toolsScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        toolsScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        toolsScroll.border = BorderFactory.createEmptyBorder()
        toolsScroll.background = UiColors.DialogBack
        toolsScroll.viewport.background = UiColors.DialogBack
    }

    /**
     * 设置页行式布局辅助：维护私有 y 游标，每放置一行自动向下推进，
     * 彻底避免调用方手写 y 坐标导致控件重叠。
     */
    private class RowPlacer(private val parent: JPanel) {
        private var y = 28                       // 当前行起始 y（首行固定留白）
        private val pad = UiMetrics.DIALOG_PADDING
        private val fieldW = DIALOG_WIDTH - NAV_WIDTH - pad * 2
        private val fieldH = 32
        private val btnW = 78
        private val rowStep = 24 + 32 + 30       // 每行：标题(24) + 字段(32) + 行距(30)
        private val labelColor = UiColors.LabelFore

        /** 放置一行「标题 + 输入控件」；[browse] 非空时在右侧追加浏览按钮。 */
        fun field(title: String, field: JComponent, browse: (() -> String?)? = null) {
            val lbl = JLabel(title)
            lbl.foreground = labelColor
            lbl.setBounds(pad, y, fieldW, 24)
            parent.add(lbl)

            val textW = if (browse != null) fieldW - btnW - 8 else fieldW
            field.setBounds(pad, y + 24, textW, fieldH)
            field.background = UiColors.FieldBack
            parent.add(field)

            if (browse != null) {
                val btn = RoundedButton("浏览")
                btn.setBounds(pad + fieldW - btnW, y + 24, btnW, fieldH)
                btn.background = UiColors.Accent
                btn.foreground = UiColors.AccentFore
                btn.borderSize = 0
                btn.addActionListener {
                    val path = browse()
                    if (!path.isNullOrEmpty()) (field as? JTextField)?.text = path
                }
                parent.add(btn)
            }
            y += rowStep
        }

        /** 面板内容总高度（末行字段底部 + 底部留白），用于设置首选尺寸。 */
        fun contentHeight(): Int = y + 24 + 32 + 20
    }

    /** 构建「关于」面板：显示应用版本号与开发者信息。 */
    private fun initAboutPanel() {
        aboutPanel = JPanel()
        aboutPanel.background = UiColors.DialogBack
        aboutPanel.layout = null

        val leftX = UiMetrics.DIALOG_PADDING
        val labelColor = UiColors.LabelFore
        val valueColor = UiColors.PanelFore

        val lblVersion = JLabel("版本号")
        lblVersion.foreground = labelColor
        lblVersion.setBounds(leftX, 28, 200, 24)
        aboutPanel.add(lblVersion)

        val lblVersionValue = JLabel(pluginVersion())
        lblVersionValue.foreground = valueColor
        lblVersionValue.font = lblVersionValue.font.deriveFont(UiMetrics.FONT_LARGE).deriveFont(Font.BOLD)
        lblVersionValue.setBounds(leftX, 56, 200, 24)
        aboutPanel.add(lblVersionValue)

        val lblDeveloper = JLabel("开发者")
        lblDeveloper.foreground = labelColor
        lblDeveloper.setBounds(leftX, 104, 200, 24)
        aboutPanel.add(lblDeveloper)

        val lblDeveloperValue = JLabel("H")
        lblDeveloperValue.foreground = valueColor
        lblDeveloperValue.font = lblDeveloperValue.font.deriveFont(UiMetrics.FONT_LARGE).deriveFont(Font.BOLD)
        lblDeveloperValue.setBounds(leftX, 132, 200, 24)
        aboutPanel.add(lblDeveloperValue)

        boldAll(aboutPanel)
    }

    /**
     * 读取本插件版本号：运行时从插件描述符获取，与构建产物（build.gradle.kts 的 version）保持一致，
     * 避免在代码里写死版本号导致展示值与实际安装版本不符。
     */
    private fun pluginVersion(): String =
        PluginManagerCore.getPlugin(PluginId.getId("com.asadb.tools"))?.version ?: "未知"

    /** 选中指定字体；若不在列表中则插入到首位并选中。 */
    private fun selectFontItem(fontName: String) {
        if (fontName.isBlank()) return
        val items = (0 until cmbFont.itemCount).map { cmbFont.getItemAt(it) }
        if (fontName in items) {
            cmbFont.selectedItem = fontName
        } else {
            cmbFont.insertItemAt(fontName, 0)
            cmbFont.selectedIndex = 0
        }
    }

    /** 更新颜色按钮的显示：圆角底色 + 反色文字，便于一眼看清当前所选颜色。 */
    private fun refreshColorButton(btn: RoundedButton, color: Color) {
        btn.setBackColor(color)
        btn.foreground = Color(
            Math.abs(255 - color.red), Math.abs(255 - color.green), Math.abs(255 - color.blue)
        )
        btn.text = "点击选择颜色  (R:${color.red}, G:${color.green}, B:${color.blue})"
        btn.borderSize = 1
        btn.borderColor = UiColors.Border
        btn.cornerRadius = UiMetrics.RADIUS
    }

    /** 弹出选择 adb 可执行文件的对话框。 */
    private fun pickAdbFile(): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "选择 adb 可执行文件"
        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "adb 可执行文件 (adb.exe;adb)", "exe", "")
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }

    /** 弹出选择目录的对话框。 */
    private fun pickDirectory(description: String): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = description
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }

    /** 弹出选择签名文件（keystore / jks）的对话框。 */
    private fun pickKeystoreFile(description: String): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = description
        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "签名文件 (*.keystore;*.jks)", "keystore", "jks")
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }

    /**
     * 弹出选择可执行文件（或 jar）的对话框。
     * [extensions] 传空字符串表示同时允许无扩展名的可执行文件（macOS / Linux）。
     */
    private fun pickExecutableFile(description: String, vararg extensions: String): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = description
        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "可执行文件 (${extensions.joinToString(";") { if (it.isEmpty()) "*" else "*.$it" }})", *extensions)
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }
}