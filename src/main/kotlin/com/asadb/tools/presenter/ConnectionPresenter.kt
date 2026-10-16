// =====================================================================
// 连接管理 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.ConnectionPresenter.cs。
// 唯一职责：连接 / 断开 / 重启 adb 以及与网络设备的连接管理。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbResult
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IConfirmationProvider
import com.asadb.tools.view.IInfoOutput
import com.asadb.tools.view.IUserInputProvider
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * 连接管理编排器：负责各类 adb 连接操作。
 */
class ConnectionPresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val input: IUserInputProvider,
    private val confirm: IConfirmationProvider
) : PresenterBase(adb, output) {

    /** 最近一次尝试连接的 IP（供「再次连接」使用）。 */
    private var lastConnectTarget: String? = null

    /** 最近一次连接失败的 IP（供「再次连接」弹窗回显）。 */
    private var lastFailedTarget: String? = null

    /** 连接 adb：启动 adb 服务。 */
    fun connectAdb() {
        runCommandAsync("连接adb", "start-server")
    }

    /** 连接 IP：先检测是否已连接网络设备，再弹出输入框执行 adb connect。 */
    fun connectIp() {
        Single.fromCallable { getCurrentConnectedIp() }
            .subscribeOn(Schedulers.io())
            .observeOn(EdtSchedulers.edt)
            .subscribe { current ->
                if (!current.isNullOrBlank()) {
                    if (!confirm.confirm("已经连接了 $current ip 的网络设备，是否连接其余设备？")) return@subscribe
                }

                val ip = input.promptForIp("连接IP") ?: return@subscribe
                val target = ip.trim()
                lastConnectTarget = target

                output.appendInfo(">>> 连接IP $target 正在执行 ...\r\n")
                runInBackground(
                    "连接IP",
                    { adb.runAdb("connect $target") },
                    { r ->
                        // 连接失败时记录该 IP，供「再次连接」弹窗回显
                        if (isConnectFailed(r)) lastFailedTarget = target
                        appendCommandResult("连接IP $target", r)
                    }
                )
            }
            .also { disposables.add(it) }
    }

    /** 再次连接：弹出输入框，预填上次连接的 IP（可修改），无记录则空白输入框，确认后重连。 */
    fun reConnect() {
        Single.fromCallable { getCurrentConnectedIp() }
            .subscribeOn(Schedulers.io())
            .observeOn(EdtSchedulers.edt)
            .subscribe { current ->
                if (!current.isNullOrBlank()) {
                    output.appendInfo("<<< 再次连接：已经成功连接ip $current ，无需再次连接\r\n")
                    return@subscribe
                }

                // 回显上次连接的 IP：失败记录优先，其次最近一次尝试，均无则空白输入框
                val defaultIp = lastFailedTarget ?: lastConnectTarget ?: ""
                val target = input.promptForIp("再次连接IP", defaultIp) ?: return@subscribe
                val trimmed = target.trim()
                if (trimmed.isEmpty()) {
                    output.appendError("<<< 再次连接：IP 地址不能为空\r\n")
                    return@subscribe
                }
                lastConnectTarget = trimmed

                output.appendInfo(">>> 再次连接 $trimmed 正在执行 ...\r\n")
                runInBackground(
                    "再次连接",
                    { adb.runAdb("connect $trimmed") },
                    { r ->
                        // 再次失败则更新失败记录，供下次「再次连接」回显
                        if (isConnectFailed(r)) lastFailedTarget = trimmed
                        appendCommandResult("再次连接 $trimmed", r)
                    }
                )
            }
            .also { disposables.add(it) }
    }

    /** 判断 adb connect 是否失败：退出码非 0 或输出含失败关键字。 */
    private fun isConnectFailed(r: AdbResult): Boolean {
        if (r.exitCode != 0) return true
        val text = "${r.output}\n${r.error}"
        return text.contains("failed", ignoreCase = true)
            || text.contains("cannot connect", ignoreCase = true)
            || text.contains("unable to connect", ignoreCase = true)
    }

    /** 查看连接状态：列出已连接设备。 */
    fun showDevices() {
        runCommandAsync("查看连接状态", "devices")
    }

    /** 扫描局域网内设备：探测本机同网段 5555 端口并自动 adb connect，回显发现的设备。 */
    fun scanLanDevices() {
        output.appendInfo(">>> 扫描局域网内设备 正在执行（探测本机同网段 5555 端口）...\r\n")
        runInBackground(
            "扫描局域网内设备",
            { adb.scanLanDevices() },
            { list ->
                if (list.isNullOrEmpty()) {
                    output.appendInfo("<<< 扫描完成：未发现局域网内可连接的 adb 设备\r\n")
                    return@runInBackground
                }
                val sb = StringBuilder()
                sb.appendLine("<<< 扫描完成，发现并已连接 ${list.size} 台设备：")
                for (ip in list) sb.appendLine("    $ip")
                output.appendInfo(sb.toString())
            }
        )
    }

    /** 断开 IP：直接执行 adb disconnect 断开所有已连接设备。 */
    fun disconnectIp() {
        runCommandAsync("断开ip", "disconnect")
    }

    /** 获取 adb 权限：执行 adb root 使 adbd 以 root 身份运行。 */
    fun rootAdb() {
        runCommandAsync("获取adb权限", "root")
    }

    /** 获取 remount 权限：执行 adb remount 重新挂载系统分区为可读写。 */
    fun remountAdb() {
        runCommandAsync("获取remount权限", "remount")
    }

    /** 重启 adb：先 kill-server 再 start-server。 */
    fun restartAdb() {
        output.appendInfo(">>> 重启adb 正在执行 ...\r\n")
        runInBackground(
            "重启adb",
            { adb.runAdb("kill-server"); adb.runAdb("start-server") },
            { r -> appendCommandResult("重启adb", r) }
        )
    }

    /** 解析 adb devices 输出，返回当前已连接的网络设备 IP（不含端口），无则返回空串。 */
    private fun getCurrentConnectedIp(): String {
        return try {
            val r = adb.runAdb("devices")
            if (r.exitCode != 0) return ""
            val text = "${r.output}\n${r.error}"
            for (raw in text.split('\r', '\n')) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("List of devices")) continue
                val parts = line.split('\t', ' ').filter { it.isNotEmpty() }
                if (parts.size >= 2 && parts[1] == "device" && parts[0].contains(":")) {
                    val idx = parts[0].lastIndexOf(':')
                    return if (idx > 0) parts[0].substring(0, idx) else parts[0]
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /** 开启 WiFi 调试：执行 adb tcpip 5555 使设备开放网络调试端口，并回显设备局域网 IP。 */
    fun enableWifiDebug() {
        output.appendInfo(">>> 开启WiFi调试 正在执行 ...\r\n")
        runInBackground(
            "开启WiFi调试",
            { adb.runAdb("tcpip 5555") },
            { r ->
                appendCommandResult("开启WiFi调试", r)
                if (r.exitCode == 0) {
                    val ip = getDeviceIp()
                    if (ip.isNotBlank()) {
                        output.appendInfo("<<< 开启WiFi调试：设备局域网 IP 为 $ip，可执行「连接ip」输入 $ip:5555 连接\r\n")
                    }
                }
            }
        )
    }

    /** 解析设备当前局域网 IPv4 地址（跳过回环与自动配置地址）。 */
    private fun getDeviceIp(): String {
        return try {
            val r = adb.runAdb("shell ip addr")
            val text = "${r.output}\n${r.error}"
            for (m in Regex("""inet (\d{1,3}(?:\.\d{1,3}){3})/""").findAll(text)) {
                val ip = m.groupValues[1]
                if (!ip.startsWith("127.") && !ip.startsWith("169.254.") && !ip.startsWith("0.0.0.0")) return ip
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /** 端口转发（正向）：输入「本地端口 设备端口」，执行 adb forward。 */
    fun portForward() {
        val mapping = input.promptForPorts("端口转发") ?: return
        val parts = mapping.trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 2) {
            output.appendError("<<< 端口转发：输入格式应为「本地端口 设备端口」，如 8080 8080\r\n")
            return
        }
        runCommandAsync("端口转发 ${parts[0]} -> ${parts[1]}", "forward tcp:${parts[0]} tcp:${parts[1]}")
    }

    /** 反向端口转发：输入「设备端口 本地端口」，执行 adb reverse。 */
    fun portReverse() {
        val mapping = input.promptForPorts("反向端口转发") ?: return
        val parts = mapping.trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 2) {
            output.appendError("<<< 反向端口转发：输入格式应为「设备端口 本地端口」，如 8080 8080\r\n")
            return
        }
        runCommandAsync("反向端口转发 ${parts[0]} -> ${parts[1]}", "reverse tcp:${parts[0]} tcp:${parts[1]}")
    }

    /** 清除全部端口转发规则。 */
    fun clearPortForwards() {
        runCommandAsync("清除端口转发", "forward --remove-all")
    }
}
