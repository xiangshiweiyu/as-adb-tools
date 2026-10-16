// =====================================================================
// 文件与 hosts 操作 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.FilePresenter.cs。
// 唯一职责：文件推送与设备 hosts 文件的获取 / 指定 / 清空。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbResult
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IConfirmationProvider
import com.asadb.tools.view.IFileDialogProvider
import com.asadb.tools.view.IInfoOutput
import com.asadb.tools.view.IUserInputProvider
import java.io.File

/**
 * 文件操作编排器：负责文件推送与 hosts 管理。
 */
class FilePresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val files: IFileDialogProvider,
    private val input: IUserInputProvider,
    private val confirm: IConfirmationProvider
) : PresenterBase(adb, output) {

    /** 推送本地文件到设备指定路径。 */
    fun pushFile() {
        val file = files.chooseFileToPush() ?: return

        val remote = input.promptForRemotePath() ?: return
        if (remote.isNotBlank()) {
            runCommandAsync("推送文件 ${File(file).name}", "push \"$file\" \"${remote.trim()}\"")
        }
    }

    /** 获取设备 hosts 内容；设备没有 hosts 时在信息窗口提示。 */
    fun getHosts() {
        output.appendInfo(">>> 获取hosts 正在执行 ...\r\n")
        runInBackground(
            "获取hosts",
            { adb.runAdb("shell cat /system/etc/hosts") },
            { r ->
                val content = (r.output ?: "").trim()
                if (r.exitCode != 0 || content.isEmpty()) {
                    output.appendInfo("<<< 获取hosts：没有任何指定的 hosts\r\n")
                    output.appendInfo("----------------------------------------\r\n")
                    return@runInBackground
                }
                appendCommandResult("获取hosts", r)
            }
        )
    }

    /** 指定 hosts：依次获取 root、remount 权限，再推送本地 hosts。 */
    fun setHosts() {
        val file = files.chooseHostsFile() ?: return
        runHostsSteps("指定hosts", "指定 hosts", "push \"$file\" /system/etc/hosts")
    }

    /** 清空 hosts：依次获取 root、remount 权限，再清空设备 hosts。 */
    fun clearHosts() {
        if (!confirm.confirmClearHosts()) return
        runHostsSteps("清空hosts", "清空 hosts", "shell \"echo -n > /system/etc/hosts\"")
    }

    /** hosts 操作公共流程：root → remount → 最后一步，逐项回显结果。 */
    private fun runHostsSteps(funcName: String, stepDesc: String, lastCommand: String) {
        output.appendInfo(">>> $funcName 正在执行（root → remount → ...）...\r\n")
        runInBackground(
            funcName,
            { runRootRemountThen(lastCommand) },
            { arr ->
                output.appendInfo("  [1/3] 获取 root 权限：${describeAdbResult(arr[0])}\r\n")
                output.appendInfo("  [2/3] 获取 remount 权限：${describeAdbResult(arr[1])}\r\n")
                output.appendInfo("  [3/3] $stepDesc：${describeAdbResult(arr[2])}\r\n")
                output.appendInfo("----------------------------------------\r\n")
            }
        )
    }

    /** 依次执行 root、remount，再执行调用方给定的最后一步，返回三步结果。 */
    private fun runRootRemountThen(lastCommand: String): List<AdbResult> {
        val root = adb.runAdb("root")
        val remount = adb.runAdb("remount")
        val last = adb.runAdb(lastCommand)
        return listOf(root, remount, last)
    }

    /** 设置设备全局代理：输入 host:port 后写入 http_proxy。 */
    fun setProxy() {
        val v = input.promptForInput("设置代理", "输入代理地址（host:port），如 192.168.1.100:8888：", "192.168.1.100:8888") ?: return
        val hp = v.trim()
        if (hp.isEmpty() || !hp.contains(":")) {
            output.appendError("<<< 设置代理：格式应为 host:port，如 192.168.1.100:8888\r\n")
            return
        }
        runCommandAsync("设置代理 $hp", "shell settings put global http_proxy $hp")
    }

    /** 清除设备全局代理。 */
    fun clearProxy() {
        runCommandAsync("清除代理", "shell settings delete global http_proxy")
    }
}
