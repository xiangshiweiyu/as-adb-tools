// =====================================================================
// 主 Presenter 门面（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.MainPresenter.cs。
// 唯一职责：把各类用例委托给对应业务 Presenter，本身不包含业务细节。
// 通过构造函数注入全部依赖，由工具窗口工厂负责装配。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.IAdbService
import com.asadb.tools.model.ILaunchItemStore
import com.asadb.tools.model.LaunchAction
import com.asadb.tools.model.LaunchPackage
import com.asadb.tools.view.IMainView
import com.asadb.tools.view.IInfoOutput

/**
 * 主 Presenter 门面：聚合各业务 Presenter，向视图提供统一入口。
 */
class MainPresenter(
    private val view: IMainView,
    private val adb: IAdbService,
    store: ILaunchItemStore
) : IMainPresenter {

    private val output: IInfoOutput = view

    private val connectionInfo: ConnectionInfoPresenter
    private val connection: ConnectionPresenter
    private val command: CommandPresenter
    private val app: AppPresenter
    private val sign: SignPresenter
    private val screen: ScreenPresenter
    private val file: FilePresenter
    private val log: LogPresenter
    private val launch: LaunchPresenter
    private val deviceInfo: DeviceInfoPresenter

    init {
        // 各业务 Presenter 均按需注入相应的窄接口，遵循接口隔离原则。
        connectionInfo = ConnectionInfoPresenter(adb, output)
        connection = ConnectionPresenter(adb, output, view, view)
        command = CommandPresenter(adb, output)
        app = AppPresenter(adb, output, view, view)
        sign = SignPresenter(adb, output, view, view)
        screen = ScreenPresenter(adb, output, view, view, view)
        file = FilePresenter(adb, output, view, view, view)
        log = LogPresenter(adb, output, view, view, view)
        launch = LaunchPresenter(adb, output, store, view, view, view)
        deviceInfo = DeviceInfoPresenter(adb, output)
    }

    /** 启动后初始化。 */
    override fun initialize() {
        if (!adb.findAdb()) promptForAdbPath()
    }

    /** 获取当前生效的 adb 可执行文件路径。 */
    override fun getAdbPath(): String = adb.adbPath

    /** 应用新的 adb 路径：测试通过则保存并生效，否则提示失败。 */
    override fun applyAdbPath(path: String): Boolean {
        if (adb.trySetAdbPath(path)) {
            output.appendInfo("已设置 adb 路径：$path\r\n")
            return true
        }
        output.appendError("所选文件不是可用的 adb，请重新选择。\r\n")
        return false
    }

    // 未找到 adb 时提示用户手动选择，并校验所选的路径是否可用
    private fun promptForAdbPath() {
        if (!view.confirm("未在常见位置找到 adb 可执行文件，是否手动选择 adb 可执行文件路径？")) {
            output.appendError("未找到 adb 可执行文件，adb 相关功能可能无法使用。")
            return
        }

        val path = view.chooseAdbFile()
        if (path.isNullOrBlank()) {
            output.appendError("未设置 adb 路径，adb 相关功能可能无法使用。")
            return
        }

        if (adb.trySetAdbPath(path)) output.appendInfo("已手动设置 adb 路径：$path")
        else output.appendError("所选文件不是可用的 adb，请重新选择。")
    }

    /** 刷新连接信息。 */
    override fun refreshConnectionInfo() = connectionInfo.refreshConnectionInfo()

    // ---- 连接管理 ----
    override fun connectAdb() = connection.connectAdb()
    override fun connectIp() = connection.connectIp()
    override fun reConnect() = connection.reConnect()
    override fun showDevices() = connection.showDevices()
    override fun scanLanDevices() = connection.scanLanDevices()
    override fun disconnectIp() = connection.disconnectIp()
    override fun restartAdb() = connection.restartAdb()
    override fun rootAdb() = connection.rootAdb()
    override fun remountAdb() = connection.remountAdb()
    override fun enableWifiDebug() = connection.enableWifiDebug()
    override fun portForward() = connection.portForward()
    override fun portReverse() = connection.portReverse()
    override fun clearPortForwards() = connection.clearPortForwards()

    /** 强制结束当前正在执行的 adb 操作（终止最近一次命令进程）。 */
    override fun stopCurrentOperation() {
        adb.stopCurrentOperation()
        output.appendInfo(">>> 已请求结束当前 adb 操作。\r\n")
    }

    // ---- 应用管理 ----
    override fun installApk() = app.installApk()
    override fun generateMd5() = app.generateMd5()
    override fun signApk() = sign.signApk()
    override fun signOneStop() = sign.signOneStop()
    override fun verifyApkSignature() = sign.verifyApkSignature()
    override fun uninstallApp(packageName: String) = app.uninstallApp(packageName)
    override fun forceStopProcess(packageName: String) = app.forceStopProcess(packageName)
    override fun clearAppCache(packageName: String) = app.clearAppCache(packageName)
    override fun extractApk(packageName: String) = app.extractApk(packageName)
    override fun disableApp(packageName: String) = app.disableApp(packageName)
    override fun enableApp(packageName: String) = app.enableApp(packageName)
    override fun loadInstalledPackages() = app.loadInstalledPackages()
    override fun loadRunningProcesses() = app.loadRunningProcesses()

    // ---- 启动 APK ----
    override fun launchByPackage() = launch.launchByPackage()
    override fun launchByAction() = launch.launchByAction()
    override fun getPackageLaunchItems(): List<LaunchPackage> = launch.getPackages()
    override fun getActionLaunchItems(): List<LaunchAction> = launch.getActions()
    override fun launchByPackage(item: LaunchPackage) = launch.launchByPackage(item)
    override fun launchByAction(item: LaunchAction) = launch.launchByAction(item)

    // ---- 屏幕操作 ----
    override fun screenshot() = screen.screenshot()
    override fun startRecord() = screen.startRecord()
    override fun stopRecord() = screen.stopRecord()
    override fun getScreenResolution() = screen.getScreenResolution()
    override fun inputTap() = screen.inputTap()
    override fun inputSwipe() = screen.inputSwipe()
    override fun inputText() = screen.inputText()
    override fun inputKeyEvent() = screen.inputKeyEvent()
    override fun startScrcpy() = screen.startScrcpy()

    // ---- 设备信息 ----
    override fun dumpActivityStack() = deviceInfo.dumpActivityStack()
    override fun listProcesses() = deviceInfo.listProcesses()
    override fun showForegroundActivity() = deviceInfo.showForegroundActivity()
    override fun showDeviceInfo() = deviceInfo.showDeviceInfo()
    override fun showBatteryInfo() = deviceInfo.showBatteryInfo()
    override fun showCpuUsage() = deviceInfo.showCpuUsage()
    override fun showMemoryInfo() = deviceInfo.showMemoryInfo()
    override fun showStorageInfo() = deviceInfo.showStorageInfo()
    override fun rebootDevice(mode: String) = deviceInfo.rebootDevice(mode)

    // ---- 文件与 hosts ----
    override fun pushFile() = file.pushFile()
    override fun getHosts() = file.getHosts()
    override fun setHosts() = file.setHosts()
    override fun clearHosts() = file.clearHosts()
    override fun setProxy() = file.setProxy()
    override fun clearProxy() = file.clearProxy()

    // ---- 日志抓取 ----
    override fun startLogCapture() = log.startLogCapture()
    override fun stopLogCapture() = log.stopLogCapture()

    /** 同时开始抓日志与录屏：先启动日志抓取（含路径选择），成功后启动录屏。 */
    override fun startLogAndRecord() {
        log.startLogCapture()
        if (adb.isLogging) screen.startRecord()
        else output.appendInfo("<<< 日志抓取未开始，已取消同时录屏。\r\n")
    }

    /** 同时结束录屏与日志抓取：静默停止两者，合并为一次「是否打开目录」提示（避免重复弹窗）。 */
    override fun stopLogAndRecord() {
        screen.stopRecord(false)
        log.stopLogCapture(false)

        // 优先提示打开录屏所在目录，其次日志目录
        var dir = screen.lastSavedDir
        if (dir.isNullOrBlank() && !log.lastLogPath.isNullOrBlank()) {
            dir = runCatching { java.io.File(log.lastLogPath!!).parent }.getOrNull()
        }
        if (!dir.isNullOrBlank()) view.promptOpenFolder(dir)
    }

    // ---- 通用命令 ----
    override fun executeCommand(desc: String, args: String) = command.executeCommand(desc, args)

    /** 释放全部子 Presenter 资源。 */
    override fun dispose() {
        connectionInfo.dispose()
        connection.dispose()
        command.dispose()
        app.dispose()
        sign.dispose()
        screen.dispose()
        file.dispose()
        log.dispose()
        launch.dispose()
        deviceInfo.dispose()
        // 窗口关闭兜底：终止仍在运行的 adb 命令进程，避免后台残留
        adb.stopCurrentOperation()
    }
}
