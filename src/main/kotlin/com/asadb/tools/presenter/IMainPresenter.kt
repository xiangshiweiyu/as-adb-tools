// =====================================================================
// 主 Presenter 门面接口（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.IMainPresenter.cs。
// 向 View 暴露统一命令入口，隐藏内部多个业务 Presenter 的编排细节。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.LaunchAction
import com.asadb.tools.model.LaunchPackage

/**
 * 主 Presenter 门面：视图通过此接口调用全部用例。
 */
interface IMainPresenter {
    /** 启动后初始化：解析 adb 路径并加载动态命令。 */
    fun initialize()

    /** 获取当前生效的 adb 可执行文件路径。 */
    fun getAdbPath(): String

    /** 应用新的 adb 路径：测试通过则保存并生效，否则返回 false。 */
    fun applyAdbPath(path: String): Boolean

    /** 刷新 adb 连接信息。 */
    fun refreshConnectionInfo()

    // ---- 连接管理 ----
    fun connectAdb()
    fun connectIp()
    fun reConnect()
    fun showDevices()

    /** 扫描本机同网段的 adb 网络设备并尝试连接。 */
    fun scanLanDevices()
    fun disconnectIp()
    fun restartAdb()
    fun rootAdb()
    fun remountAdb()

    /** 开启 WiFi 调试：adb tcpip 5555。 */
    fun enableWifiDebug()

    /** 端口转发（正向）：弹窗输入「本地端口 设备端口」。 */
    fun portForward()

    /** 反向端口转发：弹窗输入「设备端口 本地端口」。 */
    fun portReverse()

    /** 清除全部端口转发规则。 */
    fun clearPortForwards()

    /** 强制结束当前正在执行的 adb 操作。 */
    fun stopCurrentOperation()

    // ---- 应用管理 ----
    fun installApk()

    /** 生成MD5：选择 APK 后计算其 MD5 校验值。 */
    fun generateMd5()

    /** 签名：选择 APK 后使用设置中的签名文件与 apksigner 一键签名。 */
    fun signApk()

    /** 签名校验md5一条龙：签名 → 签名校验 → 生成 MD5 依次执行，签名失败即中断。 */
    fun signOneStop()

    /** 签名校验：选择 APK 后使用 apksigner verify -v 校验签名。 */
    fun verifyApkSignature()

    /** 卸载指定包名的应用。 */
    fun uninstallApp(packageName: String)

    /** 强制结束指定包名应用的进程。 */
    fun forceStopProcess(packageName: String)

    /** 清除指定包名应用的缓存数据。 */
    fun clearAppCache(packageName: String)

    /** 提取指定包名应用的 APK 到本地保存目录。 */
    fun extractApk(packageName: String)

    /** 停用指定包名应用（pm disable-user）。 */
    fun disableApp(packageName: String)

    /** 启用指定包名应用（pm enable）。 */
    fun enableApp(packageName: String)

    /** 获取已安装（第三方）应用列表并回填到卸载下拉框。 */
    fun loadInstalledPackages()

    /** 获取设备上当前运行中的应用进程列表并回填到「结束进程」下拉框。 */
    fun loadRunningProcesses()

    // ---- 启动 APK ----
    /** 弹出「包名启动」窗口（自定义新增 / 删除 / 筛选后启动）。 */
    fun launchByPackage()

    /** 弹出「action 启动」窗口（自定义新增 / 删除 / 筛选后启动）。 */
    fun launchByAction()

    /** 读取全部包名启动项（供主界面下拉框回填）。 */
    fun getPackageLaunchItems(): List<LaunchPackage>

    /** 读取全部 action 启动项（供主界面下拉框回填）。 */
    fun getActionLaunchItems(): List<LaunchAction>

    /** 直接以指定包名启动应用（主界面下拉框选中项）。 */
    fun launchByPackage(item: LaunchPackage)

    /** 直接以指定 action 启动应用（主界面下拉框选中项）。 */
    fun launchByAction(item: LaunchAction)

    // ---- 屏幕操作 ----
    fun screenshot()
    fun startRecord()
    fun stopRecord()
    fun getScreenResolution()

    /** 模拟点击：弹窗输入坐标后执行 input tap。 */
    fun inputTap()

    /** 模拟滑动：弹窗输入起止坐标后执行 input swipe。 */
    fun inputSwipe()

    /** 模拟输入文本（input text）。 */
    fun inputText()

    /** 输入按键事件（input keyevent）。 */
    fun inputKeyEvent()

    /** 屏幕投影：以独立进程启动 scrcpy。 */
    fun startScrcpy()

    // ---- 设备信息 ----
    fun dumpActivityStack()
    fun listProcesses()

    /** 查看当前前台 Activity。 */
    fun showForegroundActivity()

    /** 查看设备信息（厂商 / 型号 / 系统版本 / 分辨率等）。 */
    fun showDeviceInfo()

    /** 查看电池信息。 */
    fun showBatteryInfo()

    /** 查看 CPU 占用。 */
    fun showCpuUsage()

    /** 查看内存占用。 */
    fun showMemoryInfo()

    /** 查看存储空间。 */
    fun showStorageInfo()

    /** 重启设备：mode 为 system / bootloader / recovery。 */
    fun rebootDevice(mode: String)

    // ---- 文件与 hosts ----
    fun pushFile()
    fun getHosts()
    fun setHosts()
    fun clearHosts()

    /** 设置设备全局代理（http_proxy）。 */
    fun setProxy()

    /** 清除设备全局代理。 */
    fun clearProxy()

    // ---- 日志抓取 ----
    fun startLogCapture()
    fun stopLogCapture()

    /** 同时开始抓日志与录屏。 */
    fun startLogAndRecord()

    /** 同时结束录屏与日志抓取。 */
    fun stopLogAndRecord()

    // ---- 通用命令 ----
    fun executeCommand(desc: String, args: String)

    /** 释放资源（关闭工具窗口时调用）。 */
    fun dispose()
}
