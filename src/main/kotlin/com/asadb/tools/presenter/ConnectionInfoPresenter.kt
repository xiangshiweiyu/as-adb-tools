// =====================================================================
// 连接信息刷新 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.ConnectionInfoPresenter.cs。
// 唯一职责：后台检测 adb 版本与设备列表，并生成连接状态展示文本。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbResult
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IInfoOutput

/**
 * 连接信息编排器：负责刷新并展示 adb 连接状态。
 */
class ConnectionInfoPresenter(
    adb: IAdbService,
    output: IInfoOutput
) : PresenterBase(adb, output) {

    /** 刷新连接信息：后台检测 adb 版本与设备列表。 */
    fun refreshConnectionInfo() {
        output.appendInfo("[刷新连接信息] 正在检测 ...\r\n")
        runInBackground(
            "刷新连接信息",
            { arrayOf(adb.runAdb("version"), adb.runAdb("devices")) },
            { arr -> showConnectionStatus(arr[0], arr[1]) }
        )
    }

    /** 根据 adb 版本与设备检测结果，生成并显示连接状态。 */
    private fun showConnectionStatus(ver: AdbResult, dev: AdbResult) {
        val sb = StringBuilder()
        sb.appendLine("==================== ADB 连接信息 ====================")
        sb.appendLine("ADB 路径 : ${adb.adbPath}")

        val adbOk = ver.exitCode == 0
        if (adbOk) sb.appendLine("ADB 版本 : ${ver.output.trim()}")
        else sb.appendLine("ADB 版本 : 无法获取")

        var devicesText = ""
        var hasDevice = false
        if (dev.exitCode == 0) {
            devicesText = dev.output.trim()
            for (line in devicesText.split('\n')) {
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("List of devices")) continue
                hasDevice = true
            }
        }

        if (!adbOk) {
            sb.appendLine("")
            sb.appendLine("【连接状态】失败")
            sb.appendLine("失败原因  : adb 命令无法执行，请检查 adb 是否已安装并加入 PATH")
            if (ver.error.isNotBlank()) sb.appendLine("错误详情  : ${ver.error.trim()}")
        } else if (dev.exitCode != 0) {
            sb.appendLine("")
            sb.appendLine("【连接状态】失败")
            sb.appendLine("失败原因  : adb devices 执行失败")
            sb.appendLine("错误详情  : ${dev.error.trim()}")
        } else if (hasDevice) {
            sb.appendLine("")
            sb.appendLine("【连接状态】成功")
            sb.appendLine("设备列表  :")
            sb.appendLine(devicesText)
        } else {
            sb.appendLine("")
            sb.appendLine("【连接状态】成功（ADB 服务正常，但未检测到设备）")
            sb.appendLine("提示      : 请用 USB 连接设备，或使用 network/connect 命令连接网络设备")
        }
        sb.appendLine("======================================================")

        var oldLog = output.infoText
        oldLog = oldLog.replace("[刷新连接信息] 正在检测 ...\r\n", "")
        output.infoText = oldLog + "\r\n" + sb.toString()
        output.scrollToBottom()
    }
}
