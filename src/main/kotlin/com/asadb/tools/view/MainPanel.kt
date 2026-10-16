// =====================================================================
// 主面板（View 层）
// 对应 C# 版 AdbTool.View.MainForm。
// 负责所有控件的构建与纯 UI 交互（复制信息、新增/删除命令按钮等），
// 与 ADB 相关的业务逻辑委托给 MainPresenter 完成。
// 本类实现 IMainView 接口，供 Presenter 调用。
// =====================================================================

package com.asadb.tools.view

import com.asadb.tools.model.AdbSettingsState
import com.asadb.tools.model.LaunchAction
import com.asadb.tools.model.LaunchPackage
import com.asadb.tools.presenter.IMainPresenter
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * 主面板：实现 [IMainView]，并承载所有界面控件。
 */
class MainPanel(private val project: Project) : JPanel(), IMainView {

    private var presenter: IMainPresenter? = null

    // 终端输出适配器：插件的运行输出统一写入 AS 内置 Terminal
    private val terminalOutput = TerminalOutput(project)

    // 控件
    private val tabBar = JPanel()                         // 顶部功能标签行
    private val flpButtonsInner = JPanel()                // 滚动容器内的内容面板（承载全部按钮）
    private val scrollPane = JScrollPane()                // 功能按钮滚动容器
    private val flpBottom = JPanel()                      // 底部固定行：新增/删除命令
    private val btnAdd = RoundedButton("＋ 新增命令")      // 新增命令按钮
    private val btnDelete = RoundedButton("删除命令")      // 删除命令按钮

    // 状态
    private val infoBuffer = StringBuilder()              // 信息文本缓存（供「复制信息」使用）
    private val buttonHighlighter = ButtonHighlighter()  // 功能按钮高亮管理器
    private var isDeleteMode = false                       // 是否处于删除模式
    private val tabButtons = mutableListOf<RoundedButton>()   // 全部功能标签按钮
    private val tabButtonMap = LinkedHashMap<String, MutableList<JComponent>>()
    private val dynamicGroup = mutableListOf<JComponent>() // 「我的命令」标签下的动态按钮
    private var currentTab = ""                            // 当前选中的标签名

    // 应用管理 SplitButton
    private lateinit var rbLogTime: JRadioButton           // 日志：按时间抓取
    private lateinit var rbLogThread: JRadioButton         // 日志：时间线程抓取
    private var sbtUninstall: SplitButton? = null
    private var sbtPackage: SplitButton? = null
    private var sbtAction: SplitButton? = null
    private var sbtKillProcess: SplitButton? = null
    private var sbtClearCache: SplitButton? = null
    private var sbtReboot: SplitButton? = null             // 设备管理：重启设备
    private var sbtForward: SplitButton? = null            // 设备管理：端口转发
    private var sbtExtract: SplitButton? = null            // 应用管理：提取APK
    private var sbtDisable: SplitButton? = null            // 应用管理：停用应用
    private var sbtEnable: SplitButton? = null             // 应用管理：启用应用
    private var currentUninstallPackage: String? = null
    private var currentKillPackage: String? = null
    private var currentClearCachePackage: String? = null
    private var currentRebootMode = "system"                 // 重启设备：当前选中的重启模式（默认系统）
    private var currentExtractPackage: String? = null
    private var currentDisablePackage: String? = null
    private var currentEnablePackage: String? = null
    private var currentPackage: LaunchPackage? = null
    private var currentAction: LaunchAction? = null
    private var installedPackages = mutableListOf<String>()
    private var runningProcesses = mutableListOf<String>()
    private var logFilter = ""                               // 日志 TAG 过滤（空串表示不过滤）

    companion object {
        private const val BTN_WIDTH = 140
        private const val BTN_HEIGHT = 46
        private const val TAB_W = 44                       // 左侧竖排标签宽度
        private const val TAB_GAP = 10                     // 标签之间间距
        private const val INFO_BUFFER_LIMIT = 512 * 1024   // 信息缓存上限（字节），超限时丢弃旧内容
        private const val ADD_LAUNCH_ITEM = "＋ 添加新项…"
        private const val REFRESH_PACKAGES_ITEM = "↻ 刷新已安装应用列表"
        private const val REFRESH_RUNNING_PROCESSES_ITEM = "↻ 刷新运行中的进程列表"
        private val PanelBackColor = UiColors.PanelBack
        private val TabSelectedBack = UiColors.Accent
        private val TabSelectedFore = UiColors.AccentFore
        private val TabNormalBack = UiColors.TabNormalBack
        private val TabNormalFore = UiColors.TabNormalFore
        private val TabNormalBorder = UiColors.TabNormalBorder
    }

    init {
        initControls()
    }

    /** 由组合根（ToolWindow 工厂）注入 Presenter（依赖倒置：视图仅依赖 IMainPresenter 抽象）。 */
    fun attachPresenter(presenter: IMainPresenter) {
        this.presenter = presenter
    }

    /** 启动初始化：解析 adb 路径并刷新连接状态。 */
    fun initialize() {
        presenter?.initialize()
        presenter?.refreshConnectionInfo()
        presenter?.loadInstalledPackages()
        presenter?.loadRunningProcesses()
        refreshLaunchMenus()
    }

    // ==================== 界面构建 ====================

    private fun initControls() {
        layout = BorderLayout()
        background = PanelBackColor

        // 顶部操作按钮行：open tools / 复制信息 / 刷新状态 / 设置
        val topPanel = JPanel(BorderLayout())
        topPanel.background = PanelBackColor
        topPanel.border = BorderFactory.createEmptyBorder(
            UiMetrics.PANEL_PADDING, UiMetrics.PANEL_PADDING, UiMetrics.PANEL_PADDING, UiMetrics.PANEL_PADDING
        )
        topPanel.preferredSize = Dimension(1, 30 + UiMetrics.PANEL_PADDING * 2)

        val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        btnBar.background = PanelBackColor
        btnBar.add(createTopButton("open tools") { terminalOutput.openTerminal() })
        btnBar.add(createTopButton("复制信息") { copyInfo() })
        btnBar.add(createTopButton("刷新状态", openTerminalAfter = true) { presenter?.refreshConnectionInfo() })
        btnBar.add(createTopButton("设置") { openSettingsDialog() })
        topPanel.add(btnBar, BorderLayout.EAST)
        add(topPanel, BorderLayout.NORTH)

        // 下半：功能标签行 + 功能按钮区 + 底部命令按钮区
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.background = PanelBackColor

        tabBar.background = PanelBackColor
        // 标签改为左侧纵向排列、文字竖排显示；
        // 采用「手动绝对定位」，按钮尺寸由自身计算决定，不受布局管理器压缩
        tabBar.layout = null
        tabBar.border = BorderFactory.createEmptyBorder(10, 10, 6, 10)
        // 标签总高可能超过窗口高度，套滚动容器避免底部标签被截断
        val tabScroll = JScrollPane(tabBar)
        tabScroll.border = null
        tabScroll.background = PanelBackColor
        tabScroll.viewport.background = PanelBackColor
        tabScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        tabScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        bottomPanel.add(tabScroll, BorderLayout.WEST)

        // 功能按钮区：外层滚动容器 + 内部内容面板
        flpButtonsInner.background = PanelBackColor
        flpButtonsInner.layout = null
        scrollPane.viewport.view = flpButtonsInner
        scrollPane.border = null
        scrollPane.background = PanelBackColor
        scrollPane.viewport.background = PanelBackColor
        bottomPanel.add(scrollPane, BorderLayout.CENTER)

        // 底部固定行：新增命令 / 删除命令（贴底）
        flpBottom.background = PanelBackColor
        flpBottom.layout = FlowLayout(FlowLayout.CENTER, 12, 12)
        btnAdd.preferredSize = Dimension(BTN_WIDTH, BTN_HEIGHT)
        btnAdd.background = UiColors.Accent
        btnAdd.foreground = UiColors.AccentFore
        btnAdd.borderSize = 0
        btnAdd.addActionListener { openAddCommandDialog() }
        flpBottom.add(btnAdd)

        btnDelete.preferredSize = Dimension(BTN_WIDTH, BTN_HEIGHT)
        btnDelete.background = UiColors.DangerSolidBack
        btnDelete.foreground = UiColors.AccentFore
        btnDelete.borderSize = 0
        btnDelete.addActionListener { toggleDeleteMode() }
        flpBottom.add(btnDelete)

        bottomPanel.add(flpBottom, BorderLayout.SOUTH)

        add(bottomPanel, BorderLayout.CENTER)

        // 基本功能按钮（按功能标签归类，委托给 Presenter 执行相应业务）
        buildConnectionGroup()
        buildDeviceGroup()
        buildAppGroup()
        buildSignGroup()
        buildFileGroup()
        buildScreenGroup()
        buildLogGroup()

        // 默认选中第一个标签
        if (tabButtons.isNotEmpty()) switchTab(tabButtons[0].text)

        // 功能按钮随窗口宽度自动重排
        scrollPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                layoutFunctionButtons()
            }
        })
    }

    /** 构建「连接管理」标签页功能按钮。 */
    private fun buildConnectionGroup() {
        val group = addTab("连接管理")
        addFunctionButton(group, "连接adb") { presenter?.connectAdb() }
        addFunctionButton(group, "连接ip") { presenter?.connectIp() }
        addFunctionButton(group, "断开ip") { presenter?.disconnectIp() }
        addFunctionButton(group, "再次连接") { presenter?.reConnect() }
        addFunctionButton(group, "查看连接状态") { presenter?.showDevices() }
        addFunctionButton(group, "扫描局域网设备") { presenter?.scanLanDevices() }
        addFunctionButton(group, "重启adb") { presenter?.restartAdb() }
        addFunctionButton(group, "结束当前adb操作") { presenter?.stopCurrentOperation() }
    }

    /** 构建「设备管理」标签页功能按钮：权限 / 信息 / 操作三类，之间用分隔线区分。 */
    private fun buildDeviceGroup() {
        val group = addTab("设备管理")
        addFunctionButton(group, "Root") { presenter?.rootAdb() }
        addFunctionButton(group, "Remount") { presenter?.remountAdb() }
        addGroupDivider(group)
        addFunctionButton(group, "栈信息") { presenter?.dumpActivityStack() }
        addFunctionButton(group, "查看进程") { presenter?.listProcesses() }
        addFunctionButton(group, "前台Activity") { presenter?.showForegroundActivity() }
        addFunctionButton(group, "设备信息") { presenter?.showDeviceInfo() }
        addFunctionButton(group, "电池信息") { presenter?.showBatteryInfo() }
        addFunctionButton(group, "CPU占用") { presenter?.showCpuUsage() }
        addFunctionButton(group, "内存占用") { presenter?.showMemoryInfo() }
        addFunctionButton(group, "存储空间") { presenter?.showStorageInfo() }
        addGroupDivider(group)
        sbtReboot = addSplitButton(group, "重启设备",
            mainClick = { presenter?.rebootDevice(currentRebootMode) },
            buildMenu = { menu -> buildRebootMenu(menu) },
            emptyText = "选择重启模式")
        updateSplitButtonTip(sbtReboot, currentRebootMode)
        addFunctionButton(group, "WiFi调试") { presenter?.enableWifiDebug() }
        sbtForward = addSplitButton(group, "端口转发",
            mainClick = { presenter?.portForward() },
            buildMenu = { menu -> buildForwardMenu(menu) },
            emptyText = "选择转发方式")
        updateSplitButtonTip(sbtForward, null)
    }

    /** 构建「应用管理」标签页功能按钮：安装卸载 / 启动 / 进程缓存 / 提取停用 四组，之间用分隔线区分。 */
    private fun buildAppGroup() {
        val group = addTab("应用管理")
        addFunctionButton(group, "安装应用") { presenter?.installApk() }
        sbtUninstall = addSplitButton(group, "卸载应用",
            mainClick = { onUninstallMainClick() },
            buildMenu = { menu -> buildUninstallMenu(menu) },
            emptyText = "请选择待卸载应用")
        updateSplitButtonTip(sbtUninstall, null)
        // 安装应用 / 卸载应用 与 启动类功能 分组之间用横线分隔
        addGroupDivider(group)
        sbtPackage = addSplitButton(group, "包名启动",
            mainClick = { onPackageMainClick() },
            buildMenu = { menu -> buildPackageMenu(menu) },
            emptyText = "添加包名")
        sbtAction = addSplitButton(group, "参数启动",
            mainClick = { onActionMainClick() },
            buildMenu = { menu -> buildActionMenu(menu) },
            emptyText = "添加参数")
        // 启动类功能 与 结束进程 / 清除缓存 分组之间用横线分隔
        addGroupDivider(group)
        sbtKillProcess = addSplitButton(group, "结束进程",
            mainClick = { onKillProcessMainClick() },
            buildMenu = { menu -> buildProcessMenu(menu) },
            emptyText = "请选择待结束应用")
        updateSplitButtonTip(sbtKillProcess, null)
        sbtClearCache = addSplitButton(group, "清除缓存",
            mainClick = { onClearCacheMainClick() },
            buildMenu = { menu -> buildClearCacheMenu(menu) },
            emptyText = "请选择待清理应用")
        updateSplitButtonTip(sbtClearCache, null)
        // 卸载/清理类 与 提取/停用/启用 分组之间用横线分隔
        addGroupDivider(group)
        sbtExtract = addSplitButton(group, "提取APK",
            mainClick = { onExtractMainClick() },
            buildMenu = { menu -> buildPickAppMenu(menu, { currentExtractPackage }) { currentExtractPackage = it; updateSplitButtonTip(sbtExtract, it) } },
            emptyText = "请选择待提取应用")
        updateSplitButtonTip(sbtExtract, null)
        sbtDisable = addSplitButton(group, "停用应用",
            mainClick = { onDisableMainClick() },
            buildMenu = { menu -> buildPickAppMenu(menu, { currentDisablePackage }) { currentDisablePackage = it; updateSplitButtonTip(sbtDisable, it) } },
            emptyText = "请选择待停用应用")
        updateSplitButtonTip(sbtDisable, null)
        sbtEnable = addSplitButton(group, "启用应用",
            mainClick = { onEnableMainClick() },
            buildMenu = { menu -> buildPickAppMenu(menu, { currentEnablePackage }) { currentEnablePackage = it; updateSplitButtonTip(sbtEnable, it) } },
            emptyText = "请选择待启用应用")
        updateSplitButtonTip(sbtEnable, null)
    }

    /** 构建「应用签名」标签页功能按钮：签名 / 校验 / MD5 三组，之间用分隔线区分。 */
    private fun buildSignGroup() {
        val group = addTab("应用签名")
        addFunctionButton(group, "APK签名") { presenter?.signApk() }
        addFunctionButton(group, "签名校验") { presenter?.verifyApkSignature() }
        addFunctionButton(group, "签名校验md5一条龙") { presenter?.signOneStop() }
        addGroupDivider(group)
        addFunctionButton(group, "生成MD5") { presenter?.generateMd5() }
    }

    /** 构建「文件操作」标签页功能按钮：hosts 相关 与 代理相关 两组，之间用分隔线区分。 */
    private fun buildFileGroup() {
        val group = addTab("文件操作")
        addFunctionButton(group, "推送文件") { presenter?.pushFile() }
        addFunctionButton(group, "获取hosts") { presenter?.getHosts() }
        addFunctionButton(group, "指定hosts") { presenter?.setHosts() }
        addFunctionButton(group, "清空hosts") { presenter?.clearHosts() }
        // hosts 相关 与 代理相关 分组之间用横线分隔
        addGroupDivider(group)
        addFunctionButton(group, "设置代理") { presenter?.setProxy() }
        addFunctionButton(group, "清除代理") { presenter?.clearProxy() }
    }

    /** 构建「屏幕操作」标签页功能按钮：截图录屏 / 模拟输入 / 屏幕投影 三组，之间用分隔线区分。 */
    private fun buildScreenGroup() {
        val group = addTab("屏幕操作")
        addFunctionButton(group, "截图") { presenter?.screenshot() }
        addFunctionButton(group, "开始录屏") { presenter?.startRecord() }
        addFunctionButton(group, "结束录屏") { presenter?.stopRecord() }
        addFunctionButton(group, "获取屏幕分辨率") { presenter?.getScreenResolution() }
        // 截图录屏类 与 模拟输入类 分组之间用横线分隔
        addGroupDivider(group)
        addFunctionButton(group, "模拟点击") { presenter?.inputTap() }
        addFunctionButton(group, "模拟滑动") { presenter?.inputSwipe() }
        addFunctionButton(group, "模拟输入") { presenter?.inputText() }
        addFunctionButton(group, "按键事件") { presenter?.inputKeyEvent() }
        // 模拟输入类 与 屏幕投影 分组之间用横线分隔
        addGroupDivider(group)
        addFunctionButton(group, "屏幕投影") { presenter?.startScrcpy() }
    }

    /** 构建「日志操作」标签页功能按钮，并追加日志抓取方式单选按钮。 */
    private fun buildLogGroup() {
        val group = addTab("日志操作")
        addFunctionButton(group, "开始") { presenter?.startLogCapture() }
        addFunctionButton(group, "停止") { presenter?.stopLogCapture() }
        addFunctionButton(group, "同步日志/录制开始") { presenter?.startLogAndRecord() }
        addFunctionButton(group, "同步日志/录制结束") { presenter?.stopLogAndRecord() }
        addFunctionButton(group, "TAG过滤") { setLogFilterDialog() }

        // 日志抓取方式单选按钮
        val bg = ButtonGroup()
        rbLogTime = createRadioButton("按时间抓取", true)
        rbLogThread = createRadioButton("时间线程抓取", false)
        bg.add(rbLogTime)
        bg.add(rbLogThread)
        group.add(rbLogTime)
        group.add(rbLogThread)
        flpButtonsInner.add(rbLogTime)
        flpButtonsInner.add(rbLogThread)
    }

    /** 顶部操作按钮（带边框）。[openTerminalAfter] 为 true 时点击后自动打开 AS Terminal。 */
    private fun createTopButton(text: String, openTerminalAfter: Boolean = false, action: () -> Unit): RoundedButton {
        val btn = RoundedButton(text)
        btn.preferredSize = Dimension(if (text.length > 6) 130 else 100, 30)
        btn.borderSize = 1
        btn.borderColor = UiColors.TopBtnBorder
        btn.setBackColor(PanelBackColor)
        btn.foreground = UiColors.PanelFore
        btn.addActionListener {
            action()
            if (openTerminalAfter) terminalOutput.openIfNeeded()
            buttonHighlighter.highlight(btn)
        }
        return btn
    }

    /** 功能按钮分组分隔线：一条横贯整行可用宽度的主题色细线。 */
    private class GroupDivider : JPanel() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = UiColors.Separator
            g.drawLine(0, height / 2, width, height / 2)
        }
    }

    /** 布局功能按钮：仅排布当前标签下可见的按钮，左对齐，内容超出后上下滚动。 */
    private fun layoutFunctionButtons() {
        val gapH = UiMetrics.BUTTON_GAP
        val gapV = UiMetrics.BUTTON_GAP
        val left = UiMetrics.PANEL_PADDING
        val top = UiMetrics.PANEL_PADDING
        val dividerH = 12   // 分组分隔线行高（含上下留白）

        val availWidth = scrollPane.viewport.width
        if (availWidth <= 0) return

        // 每行可容纳的按钮数量（至少 1 个），按钮一律左对齐；
        // 每次布局都按当前可用宽度重新计算，拖动窗口时列数实时联动
        val perRow = Math.max(1, (availWidth - left * 2 + gapH) / (BTN_WIDTH + gapH))
        val startX = left

        // 分隔线长度 = 本页内单行最多按钮数量 × 按钮宽度，左对齐，而非横贯整行可用宽度
        val buttonCount = flpButtonsInner.components.count { it.isVisible && it !is GroupDivider }
        val maxRowButtons = Math.min(perRow, buttonCount)
        val dividerWidth = maxRowButtons * BTN_WIDTH

        var y = top
        var inRow = 0
        var contentBottom = top

        for (c in flpButtonsInner.components) {
            if (!c.isVisible) continue
            // 分隔线独占一行：先换行，再按本页最多按钮行的按钮总宽度绘制
            if (c is GroupDivider) {
                if (inRow > 0) {
                    y += BTN_HEIGHT + gapV
                    inRow = 0
                }
                c.setBounds(left, y, dividerWidth, dividerH)
                y += dividerH + gapV
                contentBottom = y
                continue
            }
            if (inRow == perRow) {
                y += BTN_HEIGHT + gapV
                inRow = 0
            }
            c.setBounds(startX + inRow * (BTN_WIDTH + gapH), y, BTN_WIDTH, BTN_HEIGHT)
            inRow++
            contentBottom = y + BTN_HEIGHT + gapV
        }

        contentBottom = Math.max(contentBottom, 1)
        flpButtonsInner.preferredSize = Dimension(availWidth, contentBottom)
        flpButtonsInner.size = Dimension(availWidth, contentBottom)
        scrollPane.revalidate()
    }

    /** 添加一个功能标签（页），返回该标签下的按钮集合。 */
    private fun addTab(name: String): MutableList<JComponent> {
        val tab = RoundedButton(name)
        // 高度 = 竖排文字总行高 + 上下各 10px 留白，文字完整显示且不顶边
        tab.font = tab.font.deriveFont(UiMetrics.FONT_TAB)
        val fm = tab.getFontMetrics(tab.font)
        val tabH = name.length * fm.height + 20
        tab.borderSize = 1
        tab.verticalText = true
        tab.addActionListener { switchTab(name) }

        // 手动绝对定位：宽 44、高按文字自适应，标签之间固定 10px 间距
        val x = 10
        val y = tabButtons.size * (tabH + TAB_GAP)
        tab.setBounds(x, y, TAB_W, tabH)
        tabBar.add(tab)
        tabButtons.add(tab)

        // 同步容器首选尺寸，供滚动容器正确显示
        val totalW = x + TAB_W + 10
        val totalH = y + tabH + 6
        tabBar.preferredSize = Dimension(totalW, totalH)
        tabBar.size = Dimension(totalW, totalH)

        val group = mutableListOf<JComponent>()
        tabButtonMap[name] = group
        return group
    }

    /** 切换功能标签（页）：刷新标签样式，并仅显示该标签下的功能按钮。 */
    private fun switchTab(name: String) {
        if (name.isEmpty() || currentTab == name) return
        currentTab = name

        for (tab in tabButtons) {
            val selected = tab.text == name
            if (selected) {
                tab.setBackColor(TabSelectedBack)
                tab.foreground = TabSelectedFore
                tab.borderColor = TabSelectedBack
            } else {
                tab.setBackColor(TabNormalBack)
                tab.foreground = TabNormalFore
                tab.borderColor = TabNormalBorder
            }
        }

        refreshFunctionButtonsVisibility()
    }

    /** 按当前标签刷新功能按钮可见性并重排。 */
    private fun refreshFunctionButtonsVisibility() {
        val visibleList = tabButtonMap[currentTab]
        for (c in flpButtonsInner.components) {
            c.isVisible = visibleList?.contains(c) == true
        }
        layoutFunctionButtons()
    }

    /** 添加一个常用功能按钮，并归入指定标签（页）的按钮集合。 */
    private fun addFunctionButton(group: MutableList<JComponent>, text: String, action: () -> Unit): JButton {
        val btn = RoundedButton(text)
        btn.preferredSize = Dimension(BTN_WIDTH, BTN_HEIGHT)
        btn.borderSize = 1
        btn.addActionListener {
            action()
            terminalOutput.openIfNeeded()
            buttonHighlighter.highlight(btn)
        }
        flpButtonsInner.add(btn)
        group.add(btn)
        return btn
    }

    /** 在功能按钮组之间插入一条分组分隔线，返回该分隔线组件。 */
    private fun addGroupDivider(group: MutableList<JComponent>): JComponent {
        val divider = GroupDivider()
        flpButtonsInner.add(divider)
        group.add(divider)
        return divider
    }

    /** 添加一个日志抓取方式单选按钮。 */
    private fun createRadioButton(text: String, isChecked: Boolean): JRadioButton {
        val rb = JRadioButton(text)
        rb.isSelected = isChecked
        rb.foreground = UiColors.RadioFore
        rb.font = rb.font.deriveFont(12f).deriveFont(Font.BOLD)
        rb.isOpaque = false
        return rb
    }

    /** 添加一个 SplitButton：主按钮与下拉按钮均为独立按钮，返回该组合控件。 */
    private fun addSplitButton(
        group: MutableList<JComponent>,
        text: String,
        mainClick: () -> Unit,
        buildMenu: (JPopupMenu) -> Unit,
        emptyText: String
    ): SplitButton {
        val sbt = SplitButton(text, emptyText, buildMenu)
        // 主按钮点击：执行业务动作 + 打开终端 + 高亮当前按钮
        sbt.onMainClick = {
            mainClick()
            terminalOutput.openIfNeeded()
            buttonHighlighter.highlight(sbt.mainButton)
        }
        flpButtonsInner.add(sbt.mainButton)
        flpButtonsInner.add(sbt.arrowButton)
        group.add(sbt.mainButton)
        group.add(sbt.arrowButton)
        return sbt
    }

    /** 更新 SplitButton 下拉按钮显示文本：选中项名字 + ▼（未选中时显示空态提示）。 */
    private fun updateSplitButtonTip(state: SplitButton?, selected: String?) {
        state?.updateTip(selected)
    }

    // ==================== 纯 UI 交互 ====================

    /** 复制信息到剪贴板。 */
    private fun copyInfo() {
        if (infoBuffer.isNotEmpty()) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(infoBuffer.toString()), null)
            JOptionPane.showMessageDialog(this, "连接信息已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    /** 打开「设置」弹窗。 */
    private fun openSettingsDialog() {
        val p = presenter ?: return
        val settings = AdbSettingsState.getInstance()
        val oldAdbPath = p.getAdbPath()
        val oldCacheDir = settings.cacheDirectory
        val oldMediaDir = settings.mediaDirectory
        val oldApkDir = settings.apkDirectory
        val oldSignApkDir = settings.signApkDirectory
        val oldSignKeystore = settings.signKeystorePath
        val oldScrcpyPath = settings.scrcpyPath
        val oldApksignerPath = settings.apksignerPath
        val oldJavaPath = settings.javaPath

        val dlg = SettingsForm(
            settings.fontSize,
            settings.fontName,
            AdbSettingsState.parseColor(settings.fontColor) ?: Color(99, 217, 139),
            AdbSettingsState.parseColor(settings.backColor) ?: Color(16, 23, 34),
            oldAdbPath,
            oldCacheDir,
            oldMediaDir,
            oldApkDir,
            oldSignApkDir,
            oldSignKeystore,
            oldScrcpyPath,
            oldApksignerPath,
            oldJavaPath,
            this
        )
        dlg.isVisible = true
        if (!dlg.confirmed) return

        settings.fontSize = dlg.selectedFontSize
        settings.fontName = dlg.selectedFontName
        settings.fontColor = AdbSettingsState.colorString(dlg.selectedFontColor)
        settings.backColor = AdbSettingsState.colorString(dlg.selectedBackColor)

        // 仅当某缓存项实际发生修改时才持久化并提示对应内容
        val newCacheDir = dlg.selectedCacheDirectory
        if (newCacheDir != oldCacheDir) {
            settings.cacheDirectory = newCacheDir
            appendInfo("已设置缓存目录：$newCacheDir\r\n")
        }

        val newMediaDir = dlg.selectedMediaDirectory
        if (newMediaDir != oldMediaDir) {
            settings.mediaDirectory = newMediaDir
            appendInfo("已设置日志/视频/截图保存目录：$newMediaDir\r\n")
        }

        val newApkDir = dlg.selectedApkDirectory
        if (newApkDir != oldApkDir) {
            settings.apkDirectory = newApkDir
            appendInfo("已设置待安装应用目录：$newApkDir\r\n")
        }

        val newSignApkDir = dlg.selectedSignApkDirectory
        if (newSignApkDir != oldSignApkDir) {
            settings.signApkDirectory = newSignApkDir
            appendInfo("已设置待签名apk文件夹：$newSignApkDir\r\n")
        }

        val newSignKeystore = dlg.selectedSignKeystorePath
        if (newSignKeystore != oldSignKeystore) {
            settings.signKeystorePath = newSignKeystore
            appendInfo("已设置签名文件：$newSignKeystore\r\n")
        }

        val newScrcpyPath = dlg.selectedScrcpyPath
        if (newScrcpyPath != oldScrcpyPath) {
            settings.scrcpyPath = newScrcpyPath
            appendInfo("已设置 scrcpy 路径：${newScrcpyPath.ifEmpty { "（留空，自动探测）" }}\r\n")
        }

        val newApksignerPath = dlg.selectedApksignerPath
        if (newApksignerPath != oldApksignerPath) {
            settings.apksignerPath = newApksignerPath
            appendInfo("已设置 apksigner 路径：${newApksignerPath.ifEmpty { "（留空，自动探测）" }}\r\n")
        }

        val newJavaPath = dlg.selectedJavaPath
        if (newJavaPath != oldJavaPath) {
            settings.javaPath = newJavaPath
            appendInfo("已设置 java 路径：${newJavaPath.ifEmpty { "（留空，使用 IDE 自带 JVM）" }}\r\n")
        }

        // adb 路径交由 Presenter 校验并持久化
        val adbPath = dlg.selectedAdbPath
        if (adbPath.isNotEmpty() && adbPath != oldAdbPath) {
            p.applyAdbPath(adbPath)
        }
    }

    /** 打开「新增命令」弹窗。 */
    private fun openAddCommandDialog() {
        val dlg = AddCommandForm(this)
        dlg.isVisible = true
        if (!dlg.confirmed) return
        addDynamicButton(dlg.commandName, dlg.adbCommand)
    }

    /** 新增加一个动态命令按钮。 */
    private fun addDynamicButton(name: String, command: String) {
        val btn = RoundedButton(name)
        btn.name = name
        btn.preferredSize = Dimension(BTN_WIDTH, BTN_HEIGHT)
        btn.font = btn.font.deriveFont(Font.BOLD)
        btn.setBackColor(UiColors.DynamicBack)
        btn.hoverBackColor = UiColors.DynamicHover
        btn.pressedBackColor = UiColors.DynamicPressed
        btn.foreground = UiColors.DynamicFore
        btn.borderSize = 1
        btn.borderColor = UiColors.DynamicBorder
        btn.addActionListener { onDynamicButtonClick(btn, name, command) }
        btn.putClientProperty("adbCommand", command)

        flpButtonsInner.add(btn)
        dynamicGroup.add(btn)
        appendInfo("[已添加命令按钮] $name  ->  adb $command\r\n")
        // 新增后自动切换到「我的命令」标签，让用户立刻看到新按钮
        if (currentTab != "我的命令") switchTab("我的命令") else layoutFunctionButtons()
    }

    /** 动态按钮点击：删除模式下删除，否则交给 Presenter 执行命令。 */
    private fun onDynamicButtonClick(btn: JButton, name: String, cmd: String) {
        if (isDeleteMode) {
            val dr = JOptionPane.showConfirmDialog(
                this, "确定删除命令按钮「$name」吗？", "确认删除",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
            )
            if (dr == JOptionPane.YES_OPTION) {
                flpButtonsInner.remove(btn)
                dynamicGroup.remove(btn)
                appendInfo("[已删除命令按钮] $name\r\n")
                updateDeleteModeStyle()
                layoutFunctionButtons()
            }
            return
        }
        presenter?.executeCommand(name, cmd)
        terminalOutput.openIfNeeded()
        buttonHighlighter.highlight(btn)
    }

    /** 切换删除模式。 */
    private fun toggleDeleteMode() {
        isDeleteMode = !isDeleteMode
        updateDeleteModeStyle()
    }

    /** 更新删除模式样式。 */
    private fun updateDeleteModeStyle() {
        if (isDeleteMode) {
            btnDelete.text = "完成编辑"
            btnDelete.setBackColor(UiColors.DangerBack)
            btnDelete.foreground = UiColors.DangerFore
        } else {
            btnDelete.text = "编辑快捷项"
            btnDelete.setBackColor(UiColors.SecondaryBtnBack)
            btnDelete.foreground = UiColors.SecondaryBtnFore
        }

        // 更新动态按钮样式（提示可删除）
        for (c in flpButtonsInner.components) {
            if (c is JButton && c.getClientProperty("adbCommand") != null) {
                if (isDeleteMode) {
                    (c as? RoundedButton)?.setBackColor(UiColors.DangerBack)
                        ?: run { c.background = UiColors.DangerBack }
                    c.foreground = UiColors.DangerFore
                    c.text = "${c.name}（点击删除）"
                } else {
                    (c as? RoundedButton)?.setBackColor(UiColors.DynamicBack)
                        ?: run { c.background = UiColors.DynamicBack }
                    c.foreground = UiColors.DynamicFore
                    c.text = c.name
                }
            }
        }
    }

    // ==================== SplitButton 组合逻辑 ====================

    private fun onUninstallMainClick() {
        val pkg = currentUninstallPackage
        if (pkg.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "请先点击右侧 ▼ 选择要卸载的应用", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.uninstallApp(pkg)
    }

    private fun onPackageMainClick() {
        val item = currentPackage
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先点击右侧下拉按钮选择或添加启动项", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.launchByPackage(item)
    }

    private fun onActionMainClick() {
        val item = currentAction
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先点击右侧下拉按钮选择或添加启动项", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.launchByAction(item)
    }

    private fun onKillProcessMainClick() {
        val pkg = currentKillPackage
        if (pkg.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "请先点击右侧 ▼ 选择要结束的进程", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.forceStopProcess(pkg)
    }

    private fun onClearCacheMainClick() {
        val pkg = currentClearCachePackage
        if (pkg.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "请先点击右侧 ▼ 选择要清除缓存的进程", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.clearAppCache(pkg)
    }

    private fun onExtractMainClick() {
        val pkg = currentExtractPackage
        if (pkg.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "请先点击右侧 ▼ 选择要提取的应用", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.extractApk(pkg)
    }

    private fun onDisableMainClick() {
        val pkg = currentDisablePackage
        if (pkg.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "请先点击右侧 ▼ 选择要停用的应用", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.disableApp(pkg)
    }

    private fun onEnableMainClick() {
        val pkg = currentEnablePackage
        if (pkg.isNullOrEmpty()) {
            JOptionPane.showMessageDialog(this, "请先点击右侧 ▼ 选择要启用的应用", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        presenter?.enableApp(pkg)
    }

    /** 构建卸载应用下拉菜单：刷新 + 已安装应用列表。 */
    private fun buildUninstallMenu(menu: JPopupMenu) {
        menu.removeAll()
        val refresh = JMenuItem(REFRESH_PACKAGES_ITEM)
        refresh.addActionListener { presenter?.loadInstalledPackages() }
        menu.add(refresh)
        menu.addSeparator()
        for (p in installedPackages) {
            val item = JMenuItem(p)
            item.addActionListener {
                currentUninstallPackage = p
                updateSplitButtonTip(sbtUninstall, p)
            }
            menu.add(item)
        }
        // 已选中的包名若已不在最新列表（例如已被卸载），则清空选择。
        val cur = currentUninstallPackage
        if (!cur.isNullOrEmpty() && cur !in installedPackages) currentUninstallPackage = null
        updateSplitButtonTip(sbtUninstall, currentUninstallPackage)
    }

    /** 构建结束进程下拉菜单：刷新 + 当前运行中的应用进程列表。 */
    private fun buildProcessMenu(menu: JPopupMenu) {
        menu.removeAll()
        val refresh = JMenuItem(REFRESH_RUNNING_PROCESSES_ITEM)
        refresh.addActionListener { presenter?.loadRunningProcesses() }
        menu.add(refresh)
        menu.addSeparator()
        for (p in runningProcesses) {
            val item = JMenuItem(p)
            item.addActionListener {
                currentKillPackage = p
                updateSplitButtonTip(sbtKillProcess, p)
            }
            menu.add(item)
        }
        val cur = currentKillPackage
        if (!cur.isNullOrEmpty() && cur !in runningProcesses) currentKillPackage = null
        updateSplitButtonTip(sbtKillProcess, currentKillPackage)
    }

    /** 构建清除缓存下拉菜单：刷新 + 已安装应用列表。 */
    private fun buildClearCacheMenu(menu: JPopupMenu) {
        menu.removeAll()
        val refresh = JMenuItem(REFRESH_PACKAGES_ITEM)
        refresh.addActionListener { presenter?.loadInstalledPackages() }
        menu.add(refresh)
        menu.addSeparator()
        for (p in installedPackages) {
            val item = JMenuItem(p)
            item.addActionListener {
                currentClearCachePackage = p
                updateSplitButtonTip(sbtClearCache, p)
            }
            menu.add(item)
        }
        val cur = currentClearCachePackage
        if (!cur.isNullOrEmpty() && cur !in installedPackages) currentClearCachePackage = null
        updateSplitButtonTip(sbtClearCache, currentClearCachePackage)
    }

    /** 构建重启设备下拉菜单：系统重启 / Bootloader / Recovery。 */
    private fun buildRebootMenu(menu: JPopupMenu) {
        menu.removeAll()
        val modes = listOf(
            "system" to "重启系统",
            "bootloader" to "重启到 Bootloader",
            "recovery" to "重启到 Recovery"
        )
        for ((mode, label) in modes) {
            val item = JMenuItem(label)
            item.addActionListener {
                currentRebootMode = mode
                updateSplitButtonTip(sbtReboot, mode)
                presenter?.rebootDevice(mode)
            }
            menu.add(item)
        }
    }

    /** 构建端口转发下拉菜单：正向 / 反向 / 清除全部。 */
    private fun buildForwardMenu(menu: JPopupMenu) {
        menu.removeAll()
        val forward = JMenuItem("正向转发（本地 → 设备）")
        forward.addActionListener { presenter?.portForward() }
        menu.add(forward)
        val reverse = JMenuItem("反向转发（设备 → 本地）")
        reverse.addActionListener { presenter?.portReverse() }
        menu.add(reverse)
        val clear = JMenuItem("清除全部转发规则")
        clear.addActionListener { presenter?.clearPortForwards() }
        menu.add(clear)
    }

    /**
     * 构建「从已安装应用中选择」的通用下拉菜单：刷新项 + 已安装应用列表。
     * [current] 返回当前选中值用于失效清理（选中项已不在列表时清空），[onSelect] 回调选中项。
     */
    private fun buildPickAppMenu(menu: JPopupMenu, current: () -> String?, onSelect: (String) -> Unit) {
        menu.removeAll()
        val refresh = JMenuItem(REFRESH_PACKAGES_ITEM)
        refresh.addActionListener { presenter?.loadInstalledPackages() }
        menu.add(refresh)
        menu.addSeparator()
        for (p in installedPackages) {
            val item = JMenuItem(p)
            item.addActionListener { onSelect(p) }
            menu.add(item)
        }
        val cur = current()
        if (!cur.isNullOrEmpty() && cur !in installedPackages) onSelect("")
    }

    /** 构建包名启动下拉菜单：已保存包名 + 添加新项。 */
    private fun buildPackageMenu(menu: JPopupMenu) {
        menu.removeAll()
        val p = presenter ?: return
        val packages = p.getPackageLaunchItems()

        // 当前选中项可能在弹窗中被删除，重新绑定到最新列表中的对应项
        currentPackage = currentPackage?.let { cur -> packages.find { it.packageName == cur.packageName } }

        var first: LaunchPackage? = null
        for (pkg in packages) {
            if (first == null) first = pkg
            val item = JMenuItem(pkg.displayText)
            item.addActionListener {
                currentPackage = pkg
                updateSplitButtonTip(sbtPackage, pkg.displayName)
            }
            menu.add(item)
        }
        menu.addSeparator()
        val addItem = JMenuItem(ADD_LAUNCH_ITEM)
        addItem.addActionListener {
            p.launchByPackage()
            refreshLaunchMenus()
        }
        menu.add(addItem)

        // 无选中项时默认选中首个包名启动项
        if (currentPackage == null) currentPackage = first
        updateSplitButtonTip(sbtPackage, currentPackage?.displayName)
    }

    /** 构建参数启动下拉菜单：已保存 action + 添加新项。 */
    private fun buildActionMenu(menu: JPopupMenu) {
        menu.removeAll()
        val p = presenter ?: return
        val actions = p.getActionLaunchItems()

        // 当前选中项可能在弹窗中被删除，重新绑定到最新列表中的对应项
        currentAction = currentAction?.let { cur ->
            actions.find { it.appName == cur.appName && it.name == cur.name && it.action == cur.action }
        }

        var first: LaunchAction? = null
        for (act in actions) {
            if (first == null) first = act
            val item = JMenuItem(act.displayText)
            item.addActionListener {
                currentAction = act
                updateSplitButtonTip(sbtAction, act.displayName)
            }
            menu.add(item)
        }
        menu.addSeparator()
        val addItem = JMenuItem(ADD_LAUNCH_ITEM)
        addItem.addActionListener {
            p.launchByAction()
            refreshLaunchMenus()
        }
        menu.add(addItem)

        // 无选中项时默认选中首个 action 启动项
        if (currentAction == null) currentAction = first
        updateSplitButtonTip(sbtAction, currentAction?.displayName)
    }

    /** 刷新包名/参数启动的 SplitButton 菜单。 */
    private fun refreshLaunchMenus() {
        sbtPackage?.let { buildPackageMenu(it.menu) }
        sbtAction?.let { buildActionMenu(it.menu) }
    }

    // ==================== IMainView 接口实现 ====================

    // ---- IInfoOutput ----

    /** 获取或设置信息区完整文本（内存缓存，供「复制信息」使用）。 */
    override var infoText: String
        get() = infoBuffer.toString()
        set(value) {
            infoBuffer.setLength(0)
            infoBuffer.append(value)
            terminalOutput.writeOutput(value)
        }

    /** 线程安全地向 AS Terminal 追加普通文本。 */
    override fun appendInfo(text: String) {
        appendInfoBuffer(text)
        terminalOutput.writeOutput(text)
    }

    /** 以 [ERROR] 前缀向 AS Terminal 追加错误文本。 */
    override fun appendError(text: String) {
        appendInfoBuffer(text)
        terminalOutput.writeError(text)
    }

    /** 追加到信息缓存；超过上限时丢弃旧内容的一半，控制常驻内存。 */
    private fun appendInfoBuffer(text: String) {
        if (infoBuffer.length + text.length > INFO_BUFFER_LIMIT && infoBuffer.isNotEmpty()) {
            infoBuffer.delete(0, infoBuffer.length / 2)
        }
        infoBuffer.append(text)
    }

    /** 终端自带滚动，无需额外处理。 */
    override fun scrollToBottom() {
    }

    // ---- IFileDialogProvider ----

    /** 通用文件选择对话框（单选文件），返回选中文件绝对路径；取消返回 null。 */
    private fun openFileChooser(title: String, filter: javax.swing.filechooser.FileNameExtensionFilter?, initialDir: String? = null): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = title
        if (filter != null) chooser.fileFilter = filter
        if (initialDir != null && File(initialDir).isDirectory) chooser.currentDirectory = File(initialDir)
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }

    override fun chooseApkFile(defaultDirectory: String?): String? {
        // 默认定位：优先指定的文件夹，其次设置中配置的「待安装应用目录」
        val dir = defaultDirectory?.takeIf { it.isNotBlank() && File(it).isDirectory }
            ?: AdbSettingsState.getInstance().apkDirectory.takeIf { it.isNotBlank() && File(it).isDirectory }
        return openFileChooser("选择要安装的 APK", javax.swing.filechooser.FileNameExtensionFilter("APK 文件", "apk"), dir)
    }

    override fun chooseFileToPush(): String? =
        openFileChooser("选择要推送的文件", null)

    override fun chooseScreenshotSavePath(): String? {
        buildMediaSavePath(".png", "screenshot_")?.let { return it }
        return chooseSavePath("保存截图", "PNG 图片", "png", "screenshot_")
    }

    override fun chooseScreenRecordSavePath(): String? {
        buildMediaSavePath(".mp4", "screenrecord_")?.let { return it }
        return chooseSavePath("保存录屏", "MP4 视频", "mp4", "screenrecord_")
    }

    override fun chooseHostsFile(): String? =
        openFileChooser("选择要指定的 hosts 文件", javax.swing.filechooser.FileNameExtensionFilter("hosts 文件", "hosts"))

    override fun chooseLogSavePath(): String? {
        buildMediaSavePath(".txt", "log_")?.let { return it }
        return chooseSavePath("选择日志保存路径", "日志文件", "txt", "log_")
    }

    override fun chooseAdbFile(): String? =
        openFileChooser("选择 adb 可执行文件", javax.swing.filechooser.FileNameExtensionFilter("adb 可执行文件", "exe"))

    override fun chooseCacheDirectory(): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "请选择缓存文件保存目录（用于保存自定义的包名/Action 启动项）："
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }

    /** 选择保存路径对话框（截图/录屏/日志共用）。 */
    private fun chooseSavePath(title: String, desc: String, ext: String, prefix: String): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = title
        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("$desc (*.$ext)", ext)
        chooser.selectedFile = File("${prefix}${nowStamp()}.$ext")
        return if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile.absolutePath else null
    }

    /** 已配置媒体目录时，在该目录下生成带时间戳的文件名；未配置或目录不可用时返回 null。 */
    private fun buildMediaSavePath(extension: String, prefix: String): String? {
        val dir = AdbSettingsState.getInstance().mediaDirectory
        if (dir.isBlank()) return null
        return runCatching {
            val f = File(dir)
            f.mkdirs()
            File(dir, "${prefix}${nowStamp()}$extension").absolutePath
        }.getOrNull()
    }

    private fun nowStamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    // ---- IUserInputProvider ----

    override fun promptForRemotePath(): String? =
        InputDialog.prompt("推送文件", "请输入设备上的目标路径（如 /sdcard/）：", "/sdcard/", this)

    override fun promptForIp(title: String): String? = promptForIp(title, "")

    override fun promptForIp(title: String, defaultValue: String): String? =
        InputDialog.prompt(title, "请输入设备 IP 地址（如 192.168.1.100:5555）：", defaultValue, this)

    override fun promptForInput(title: String, labelText: String, defaultText: String): String? =
        InputDialog.prompt(title, labelText, defaultText, this)

    // ---- IAppManagementView ----

    override fun displayInstalledPackages(packageNames: List<String>) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { displayInstalledPackages(packageNames) }
            return
        }
        installedPackages = packageNames.toMutableList()
        sbtUninstall?.let { buildUninstallMenu(it.menu) }
        sbtClearCache?.let { buildClearCacheMenu(it.menu) }
        sbtExtract?.let { buildPickAppMenu(it.menu, { currentExtractPackage }) { currentExtractPackage = it; updateSplitButtonTip(sbtExtract, it) } }
        sbtDisable?.let { buildPickAppMenu(it.menu, { currentDisablePackage }) { currentDisablePackage = it; updateSplitButtonTip(sbtDisable, it) } }
        sbtEnable?.let { buildPickAppMenu(it.menu, { currentEnablePackage }) { currentEnablePackage = it; updateSplitButtonTip(sbtEnable, it) } }
    }

    override fun displayRunningProcesses(processNames: List<String>) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { displayRunningProcesses(processNames) }
            return
        }
        runningProcesses = processNames.toMutableList()
        sbtKillProcess?.let { buildProcessMenu(it.menu) }
    }

    // ---- IConfirmationProvider ----

    override fun confirm(message: String): Boolean {
        val dr = JOptionPane.showConfirmDialog(
            this, message, "提示", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
        )
        return dr == JOptionPane.OK_OPTION
    }

    override fun confirmClearHosts(): Boolean {
        val dr = JOptionPane.showConfirmDialog(
            this, "确定要清空设备上的 hosts 文件吗？", "确认",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
        )
        return dr == JOptionPane.OK_OPTION
    }

    // ---- ILogCaptureView ----

    override fun getLogCaptureMode(): String =
        if (this::rbLogThread.isInitialized && rbLogThread.isSelected) "threadtime" else "time"

    override fun getLogFilter(): String = logFilter

    /** 弹出日志 TAG 过滤设置弹窗，供抓取日志时只输出指定 TAG（留空表示不过滤）。 */
    private fun setLogFilterDialog() {
        val v = InputDialog.prompt("TAG过滤", "输入要过滤的日志 TAG（多个用空格分隔，留空表示不过滤）：", logFilter, this) ?: return
        logFilter = v.trim()
        appendInfo("已设置日志 TAG 过滤：${logFilter.ifEmpty { "（无）" }}\r\n")
    }

    // ---- ILaunchView ----

    override fun showPackageLaunch(packages: MutableList<LaunchPackage>): LaunchPackage? {
        val dlg = LaunchPackageForm(packages, this)
        dlg.isVisible = true
        return dlg.selectedPackage
    }

    override fun showActionLaunch(actions: MutableList<LaunchAction>): LaunchAction? {
        val dlg = LaunchActionForm(actions, this)
        dlg.isVisible = true
        return dlg.selectedAction
    }

    // ---- IFolderOpenProvider ----

    override fun promptOpenFolder(folderPath: String) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { promptOpenFolder(folderPath) }
            return
        }
        if (folderPath.isBlank() || !File(folderPath).isDirectory) return

        val dr = JOptionPane.showConfirmDialog(
            this, "操作完成，是否打开保存目录？\r\n$folderPath",
            "提示", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE
        )
        if (dr != JOptionPane.YES_OPTION) return
        runCatching { Desktop.getDesktop().open(File(folderPath)) }
            .onFailure { JOptionPane.showMessageDialog(this, "打开目录失败：${it.message}", "提示", JOptionPane.ERROR_MESSAGE) }
    }
}
