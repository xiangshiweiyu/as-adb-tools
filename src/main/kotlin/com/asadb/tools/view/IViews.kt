// =====================================================================
// 主窗体视图窄接口（View 层，接口隔离）
// 对应 C# 版 AdbTool.View.IViews.cs。
// 按「消费方」维度拆分主视图为多个职责单一的窄接口，
// 每个 Presenter 只依赖自己真正用到的接口，避免被迫实现无关方法。
// =====================================================================

package com.asadb.tools.view

import com.asadb.tools.model.LaunchAction
import com.asadb.tools.model.LaunchPackage

/** 信息输出能力：向信息区追加文本 / 错误 / 滚动。 */
interface IInfoOutput {
    /** 获取或设置信息区的完整文本。 */
    var infoText: String

    /** 线程安全地向信息区追加普通文本。 */
    fun appendInfo(text: String)

    /** 线程安全地向信息区追加错误文本（红色字体）。 */
    fun appendError(text: String)

    /** 将信息区滚动到底部。 */
    fun scrollToBottom()
}

/** 文件选择能力：提供各类打开 / 保存对话框。 */
interface IFileDialogProvider {
    /** 选择要安装的 APK 文件；defaultDirectory 非空时默认定位到该文件夹；取消返回 null。 */
    fun chooseApkFile(defaultDirectory: String? = null): String?

    /** 选择要推送的文件；取消返回 null。 */
    fun chooseFileToPush(): String?

    /** 选择截图保存路径；取消返回 null。 */
    fun chooseScreenshotSavePath(): String?

    /** 选择录屏保存路径；取消返回 null。 */
    fun chooseScreenRecordSavePath(): String?

    /** 选择本地 hosts 文件；取消返回 null。 */
    fun chooseHostsFile(): String?

    /** 选择日志保存路径；取消返回 null。 */
    fun chooseLogSavePath(): String?

    /** 选择 adb 可执行文件；取消返回 null。 */
    fun chooseAdbFile(): String?

    /** 选择缓存保存目录；取消返回 null。 */
    fun chooseCacheDirectory(): String?
}

/** 用户输入能力：提供各类单行文本输入框。 */
interface IUserInputProvider {
    /** 输入设备目标路径；取消返回 null。 */
    fun promptForRemotePath(): String?

    /** 输入 IP 地址；取消返回 null。 */
    fun promptForIp(title: String): String?

    /** 输入 IP 地址并预填默认值（供「再次连接」回显上次失败的 IP）；取消返回 null。 */
    fun promptForIp(title: String, defaultValue: String): String?

    /** 通用单行输入弹窗：title 为弹窗标题，labelText 为提示文案；取消返回 null。 */
    fun promptForInput(title: String, labelText: String, defaultText: String): String?

    /** 输入端口映射（本地端口 设备端口），用于端口转发；取消返回 null。 */
    fun promptForPorts(title: String): String? =
        promptForInput(title, "输入端口映射（本地端口 设备端口），如 8080 8080", "8080 8080")
}

/** 应用管理视图能力：接收并展示已安装应用 / 运行中进程列表，供下拉框使用。 */
interface IAppManagementView {
    /** 回填已安装的应用包名列表。 */
    fun displayInstalledPackages(packageNames: List<String>)

    /** 回填当前运行中的应用进程列表（供「结束进程」下拉框使用）。 */
    fun displayRunningProcesses(processNames: List<String>)
}

/** 确认能力：提供确认 / 二次确认弹窗。 */
interface IConfirmationProvider {
    /** 通用确认弹窗；返回是否点击确定。 */
    fun confirm(message: String): Boolean

    /** 确认是否清空设备 hosts；返回是否确认。 */
    fun confirmClearHosts(): Boolean
}

/** 日志抓取视图：提供当前选择的日志抓取方式与 TAG 过滤。 */
interface ILogCaptureView {
    /** 获取当前选择的日志抓取方式（"time"=按时间，"threadtime"=时间线程）。 */
    fun getLogCaptureMode(): String

    /** 获取当前设置的 TAG 过滤（空串表示不过滤）。 */
    fun getLogFilter(): String
}

/** 打开文件夹能力：截图 / 录屏 / 日志等操作完成后，提示用户是否打开对应保存目录。 */
interface IFolderOpenProvider {
    /** 提示用户是否打开指定文件夹；用户确认后打开系统资源管理器。 */
    fun promptOpenFolder(folderPath: String)
}

/** 启动 APK 视图能力：弹出「包名启动 / action 启动」窗口并返回用户确认的启动项。
 * 弹窗中新增 / 删除的启动项会写入传入的可变列表，供上层统一持久化。
 */
interface ILaunchView {
    /** 弹出「包名启动」窗口；用户可从下拉框筛选或自定义新增，确认启动返回所选启动项，取消返回 null。 */
    fun showPackageLaunch(packages: MutableList<LaunchPackage>): LaunchPackage?

    /** 弹出「action 启动」窗口；用户可从下拉框筛选或自定义新增，确认启动返回所选启动项，取消返回 null。 */
    fun showActionLaunch(actions: MutableList<LaunchAction>): LaunchAction?
}
