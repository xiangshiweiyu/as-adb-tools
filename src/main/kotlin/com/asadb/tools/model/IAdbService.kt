// =====================================================================
// ADB 执行服务抽象（Model 层，依赖倒置）
// 对应 C# 版 AdbTool.Model.IAdbService。
// Presenter 仅依赖本接口，不依赖具体实现，便于替换实现与单元测试。
// =====================================================================

package com.asadb.tools.model

/**
 * ADB 执行服务接口：抽象了 adb 进程的定位、命令执行、截图/录屏/日志等能力。
 */
interface IAdbService {
    /** 当前解析出的 adb 可执行文件路径。 */
    val adbPath: String

    /** 是否正在录屏。 */
    val isRecording: Boolean

    /** 是否正在抓取日志。 */
    val isLogging: Boolean

    /** 在常见位置中定位 adb 可执行文件；返回是否成功找到。 */
    fun findAdb(): Boolean

    /** 通过 Android Studio 平台库获取本地 SDK 路径（仅 AS 中可用，其余环境返回 null）。 */
    fun detectSdkPath(): String?

    /** 手动设置 adb 路径；测试通过则更新并返回 true，否则返回 false。 */
    fun trySetAdbPath(path: String): Boolean

    /** 同步执行一条 adb 命令并返回结果（应在后台线程调用）。 */
    fun runAdb(args: String): AdbResult

    /** 直接运行一条外部进程命令并返回结果（不注入 -s）；统一管理进程销毁与超时，防止挂死残留。应在后台线程调用。 */
    fun runRawProcess(command: List<String>, toolName: String): AdbResult

    /** 启动一个外部进程并立即返回（进程分离，不等待退出、不收集输出），用于 scrcpy 等长驻 GUI 工具；返回启动结果描述。 */
    fun launchDetached(command: List<String>, toolName: String): String

    /** 扫描本机同网段的 adb 网络设备并尝试连接，返回成功发现的设备 IP 列表。 */
    fun scanLanDevices(): List<String>

    /** 设备截图并保存到本地，返回结果描述字符串。 */
    fun runScreenshot(savePath: String): String

    /** 启动设备端录屏，返回是否成功启动。 */
    fun startScreenRecord(): Boolean

    /** 结束录屏并保存到本地，返回结果描述字符串。 */
    fun stopScreenRecord(savePath: String): String

    /** 启动日志抓取，逐行回调输出，返回是否成功启动。 */
    fun startLogcat(args: String, onLine: (String) -> Unit): Boolean

    /** 停止日志抓取。 */
    fun stopLogcat()

    /** 强制结束当前正在执行的一次性 adb 命令（不影响录屏 / 日志抓取）。 */
    fun stopCurrentOperation()
}
