// =====================================================================
// 应用管理 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.AppPresenter.cs。
// 唯一职责：应用的安装、卸载与已安装应用列表查询。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbSettingsState
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IAppManagementView
import com.asadb.tools.view.IFileDialogProvider
import com.asadb.tools.view.IInfoOutput
import java.io.File

/**
 * 应用管理编排器：负责 APK 安装、卸载与已安装应用列表查询。
 */
class AppPresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val files: IFileDialogProvider,
    private val appView: IAppManagementView
) : PresenterBase(adb, output) {

    /** 安装 APK。 */
    fun installApk() {
        val file = files.chooseApkFile() ?: return
        runCommandAsync("安装APK ${java.io.File(file).name}", "install -r \"$file\"")
    }

    /** 生成 APK 文件的 MD5 校验值（流式计算，支持大文件）。 */
    fun generateMd5() {
        val apkPath = files.chooseApkFile(signApkDefaultDirectory()) ?: return
        val apk = File(apkPath)
        if (!apk.isFile) {
            output.appendError("<<< 文件不存在：$apkPath\r\n")
            return
        }
        output.appendInfo(">>> 正在计算 ${apk.name} 的 MD5 ...\r\n")

        runInBackground("生成MD5", { md5Of(apk) }, { md5 ->
            val sb = StringBuilder()
            sb.appendLine("<<< ${apk.name} MD5：")
            sb.appendLine("    $md5")
            sb.appendLine("----------------------------------------")
            output.appendInfo(sb.toString())
        })
    }

    /** 卸载指定包名的应用。 */
    fun uninstallApp(packageName: String) {
        if (packageName.isBlank()) return
        runCommandAsync("卸载APK $packageName", "uninstall ${packageName.trim()}")
    }

    /** 强制结束指定包名应用的进程（am force-stop）。 */
    fun forceStopProcess(packageName: String) {
        if (packageName.isBlank()) return
        runCommandAsync("结束进程 $packageName", "shell am force-stop ${packageName.trim()}")
    }

    /** 清除指定包名应用的缓存数据（pm clear）。 */
    fun clearAppCache(packageName: String) {
        if (packageName.isBlank()) return
        runCommandAsync("清除缓存 $packageName", "shell pm clear ${packageName.trim()}")
    }

    /** 提取指定包名应用的 APK 到本地保存目录（设置中的媒体目录，未配置则用户主目录）。 */
    fun extractApk(packageName: String) {
        if (packageName.isBlank()) return
        val pkg = packageName.trim()
        output.appendInfo(">>> 提取APK $pkg 正在执行 ...\r\n")
        runInBackground(
            "提取APK $pkg",
            {
                // 1) 查询 APK 在设备上的路径
                val pm = adb.runAdb("shell pm path $pkg")
                if (pm.exitCode != 0 || pm.output.isBlank()) {
                    return@runInBackground "失败：未找到应用 $pkg 的 APK 路径"
                }
                val remote = parseApkPath(pm.output)
                    ?: return@runInBackground "失败：无法解析 $pkg 的 APK 路径"

                // 2) 拉取到本地（设置中的媒体目录，未配置则用户主目录）
                val saveDir = AdbSettingsState.getInstance().mediaDirectory
                    .takeIf { it.isNotBlank() }
                    ?: System.getProperty("user.home")
                val saveFile = File(saveDir, "$pkg.apk")
                val pull = adb.runAdb("pull \"$remote\" \"${saveFile.absolutePath}\"")
                if (pull.exitCode != 0) {
                    val err = pull.error.trim().ifEmpty { pull.output.trim() }
                    return@runInBackground "失败：$err"
                }
                return@runInBackground "成功：已保存到 ${saveFile.absolutePath}"
            },
            { msg ->
                if (isErrorMsg(msg)) output.appendError("<<< 提取APK：$msg\r\n")
                else output.appendInfo("<<< 提取APK：$msg\r\n")
            }
        )
    }

    /** 从 pm path 输出解析 APK 设备路径（取 package: 前缀且以 .apk 结尾的行）。 */
    private fun parseApkPath(output: String): String? {
        for (line in output.split('\n')) {
            var s = line.trim()
            if (s.startsWith("package:")) s = s.substring("package:".length).trim()
            if (s.endsWith(".apk", ignoreCase = true)) return s
        }
        return null
    }

    /** 停用指定包名应用（pm disable-user，仅当前用户）。 */
    fun disableApp(packageName: String) {
        if (packageName.isBlank()) return
        runCommandAsync("停用应用 $packageName", "shell pm disable-user --user 0 ${packageName.trim()}")
    }

    /** 启用指定包名应用（pm enable）。 */
    fun enableApp(packageName: String) {
        if (packageName.isBlank()) return
        runCommandAsync("启用应用 $packageName", "shell pm enable ${packageName.trim()}")
    }

    /** 后台获取已安装（第三方）应用列表并回填到卸载下拉框。 */
    fun loadInstalledPackages() {
        output.appendInfo(">>> 正在获取已安装应用列表 ...\r\n")
        runInBackground(
            "获取已安装应用列表",
            { adb.runAdb("shell pm list packages -3") },
            { r -> onInstalledPackagesLoaded(r) }
        )
    }

    /** 后台获取设备上当前运行中的应用进程列表并回填到「结束进程」下拉框。 */
    fun loadRunningProcesses() {
        output.appendInfo(">>> 正在获取运行中的进程列表 ...\r\n")
        runInBackground(
            "获取运行进程",
            { adb.runAdb("shell ps") },
            { r -> onRunningProcessesLoaded(r) }
        )
    }

    /** 处理运行进程列表查询结果。 */
    private fun onRunningProcessesLoaded(r: com.asadb.tools.model.AdbResult) {
        if (r.exitCode != 0) {
            appendCommandResult("获取运行进程", r)
            return
        }
        val names = parseRunningProcesses(r.output)
        appView.displayRunningProcesses(names)
        output.appendInfo(">>> 共获取到 ${names.size} 个运行中的应用进程。\r\n")
    }

    /**
     * 解析 ps 输出：提取当前运行中的应用进程名（包名），跳过系统 / 内核 / 通信类进程。
     * 应用进程的 USER 形如 u0_aXX，NAME 是包名（含点）；系统进程 USER 多为 root/system/shell。
     */
    private fun parseRunningProcesses(output: String): List<String> {
        val list = mutableListOf<String>()
        if (output.isBlank()) return list

        for (line in output.split('\n')) {
            val s = line.trim()
            if (s.isEmpty()) continue

            val parts = s.split(' ', '\t').filter { it.isNotEmpty() }
            if (parts.size < 2) continue

            val user = parts[0]                 // 首列：进程属主
            val name = parts[parts.size - 1]    // 末列：进程名（应用即为包名）

            // 只保留普通应用进程：跳过内核线程、非包名进程与系统属主进程
            if (name.startsWith("[")) continue
            if (!name.contains(".")) continue
            if (user.startsWith("root") || user.startsWith("system") || user.startsWith("shell")
                || user.startsWith("kernel") || user.startsWith("radio") || user.startsWith("bluetooth")
                || user.startsWith("logd") || user.startsWith("audit")) continue

            if (name !in list) list.add(name)
        }
        return list
    }

    /** 处理已安装应用列表查询结果。 */
    private fun onInstalledPackagesLoaded(r: com.asadb.tools.model.AdbResult) {
        if (r.exitCode != 0) {
            appendCommandResult("获取已安装应用列表", r)
            return
        }
        val packages = parseInstalledPackages(r.output)
        appView.displayInstalledPackages(packages)
        output.appendInfo(">>> 共获取到 ${packages.size} 个已安装应用。\r\n")
    }

    /** 解析 pm list packages 输出，去掉 "package:" 前缀并去重。 */
    private fun parseInstalledPackages(output: String): List<String> {
        val list = mutableListOf<String>()
        if (output.isBlank()) return list

        for (line in output.split('\n')) {
            var s = line.trim()
            if (s.startsWith("package:")) s = s.substring("package:".length).trim()
            if (s.isEmpty() || s in list) continue
            list.add(s)
        }
        return list
    }
}
