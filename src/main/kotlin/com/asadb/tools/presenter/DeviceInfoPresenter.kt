// =====================================================================
// 设备信息 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.DeviceInfoPresenter.cs。
// 唯一职责：查询设备诊断信息（前台 Activity 栈信息、前台进程列表）。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IInfoOutput

/**
 * 设备信息编排器：负责前台 Activity 栈与前台进程等信息查询。
 */
class DeviceInfoPresenter(
    adb: IAdbService,
    output: IInfoOutput
) : PresenterBase(adb, output) {

    /** 打印当前正在前台运行的 Activity 栈信息。 */
    fun dumpActivityStack() {
        output.appendInfo(">>> 栈信息（前台 Activity）正在执行 ...\r\n")
        runInBackground(
            "栈信息",
            {
                val r = adb.runAdb("shell dumpsys activity activities")
                if (r.exitCode != 0) return@runInBackground describeAdbResult(r)

                val line = parseForegroundActivityLine(r.output)
                if (line.isNullOrEmpty()) return@runInBackground "未找到前台 Activity"
                return@runInBackground "<<< 前台 Activity：$line"
            },
            { msg ->
                if (isErrorMsg(msg)) output.appendError("<<< 栈信息：$msg\r\n")
                else output.appendInfo("$msg\r\n")
            }
        )
    }

    /** 查看当前正在前台运行的应用进程。 */
    fun listProcesses() {
        output.appendInfo(">>> 查看前台进程 正在执行 ...\r\n")
        runInBackground(
            "查看进程",
            {
                val act = adb.runAdb("shell dumpsys activity activities")
                if (act.exitCode != 0) return@runInBackground describeAdbResult(act)

                val pkg = parseForegroundPackage(act.output)
                if (pkg.isNullOrEmpty()) return@runInBackground "未找到前台应用"

                val pid = adb.runAdb("shell pidof $pkg")
                val pidText = (pid.output ?: "").trim()
                if (pidText.isEmpty()) return@runInBackground "前台应用 $pkg 的进程未在运行"

                return@runInBackground "<<< 前台进程：$pkg (pid $pidText)"
            },
            { msg ->
                if (isErrorMsg(msg)) output.appendError("<<< 查看进程：$msg\r\n")
                else output.appendInfo("$msg\r\n")
            }
        )
    }

    /** 从 dumpsys activity activities 输出中解析前台 Activity 记录行。 */
    private fun parseForegroundActivityLine(output: String?): String? {
        if (output.isNullOrEmpty()) return null

        for (raw in output.split('\n')) {
            val line = raw.trim()
            if (line.startsWith("topResumedActivity") || line.startsWith("mResumedActivity"))
                return line
        }
        return null
    }

    /** 从 dumpsys activity activities 输出中解析前台应用包名。 */
    private fun parseForegroundPackage(output: String?): String? {
        val line = parseForegroundActivityLine(output) ?: return null

        // 格式形如：mResumedActivity: ActivityRecord{... u0 com.android.settings/.Settings t123}
        val m1 = Regex("""u\d+\s+([a-zA-Z0-9_.]+)/""").find(line)
        if (m1 != null) return m1.groupValues[1]

        // 兼容无 u 前缀的格式：... com.android.settings/.Settings ...
        val m2 = Regex("""\s([a-zA-Z0-9_.]+)/\.""").find(line)
        return m2?.groupValues?.get(1)
    }

    /** 打印当前正在前台运行的 Activity（解析 dumpsys window windows 的焦点窗口，展示包名/类名）。 */
    fun showForegroundActivity() {
        output.appendInfo(">>> 前台Activity 正在执行 ...\r\n")
        runInBackground(
            "前台Activity",
            {
                val r = adb.runAdb("shell dumpsys window windows")
                if (r.exitCode != 0) return@runInBackground describeAdbResult(r)

                val line = parseFocusLine(r.output)
                if (line.isNullOrEmpty()) return@runInBackground "未找到前台 Activity"
                return@runInBackground "<<< 前台 Activity：${line}"
            },
            { msg ->
                if (isErrorMsg(msg)) output.appendError("<<< 前台Activity：$msg\r\n")
                else output.appendInfo("$msg\r\n")
            }
        )
    }

    /** 从 dumpsys window windows 输出中解析前台焦点窗口行。 */
    private fun parseFocusLine(output: String?): String? {
        if (output.isNullOrEmpty()) return null
        for (raw in output.split('\n')) {
            val line = raw.trim()
            if (line.startsWith("mCurrentFocus") || line.startsWith("mFocusedApp"))
                return line
        }
        return null
    }

    /** 查看设备信息：厂商、型号、系统版本、SDK、序列号、分辨率、密度。 */
    fun showDeviceInfo() {
        output.appendInfo(">>> 设备信息 正在执行 ...\r\n")
        runInBackground(
            "设备信息",
            {
                val props = listOf(
                    "ro.product.manufacturer" to "厂商",
                    "ro.product.model" to "型号",
                    "ro.build.version.release" to "Android版本",
                    "ro.build.version.sdk" to "SDK版本",
                    "ro.serialno" to "序列号"
                )
                val sb = StringBuilder()
                for ((key, label) in props) {
                    val r = adb.runAdb("shell getprop $key")
                    val v = (r.output ?: "").trim()
                    sb.appendLine("  $label：${v.ifEmpty { "未知" }}")
                }
                val size = adb.runAdb("shell wm size")
                sb.appendLine("  分辨率：${size.output.trim().ifEmpty { "未知" }}")
                val density = adb.runAdb("shell wm density")
                sb.appendLine("  密度：${density.output.trim().ifEmpty { "未知" }}")
                return@runInBackground sb.toString().trimEnd()
            },
            { msg -> output.appendInfo("<<< 设备信息：\r\n$msg\r\n") }
        )
    }

    /** 查看电池信息。 */
    fun showBatteryInfo() {
        output.appendInfo(">>> 电池信息 正在执行 ...\r\n")
        runInBackground(
            "电池信息",
            { adb.runAdb("shell dumpsys battery") },
            { r -> appendCommandResult("电池信息", r) }
        )
    }

    /** 查看 CPU 占用率（top 采样一次）。 */
    fun showCpuUsage() {
        output.appendInfo(">>> CPU占用 正在执行 ...\r\n")
        runInBackground(
            "CPU占用",
            { adb.runAdb("shell top -b -n 1 -d 1") },
            { r -> appendCommandResult("CPU占用", r) }
        )
    }

    /** 查看内存占用信息。 */
    fun showMemoryInfo() {
        output.appendInfo(">>> 内存占用 正在执行 ...\r\n")
        runInBackground(
            "内存占用",
            { adb.runAdb("shell dumpsys meminfo") },
            { r -> appendCommandResult("内存占用", r) }
        )
    }

    /** 查看存储空间使用情况。 */
    fun showStorageInfo() {
        output.appendInfo(">>> 存储空间 正在执行 ...\r\n")
        runInBackground(
            "存储空间",
            { adb.runAdb("shell df -h") },
            { r -> appendCommandResult("存储空间", r) }
        )
    }

    /** 重启设备：mode 为 system / bootloader / recovery，空值或 system 表示正常重启。 */
    fun rebootDevice(mode: String) {
        val arg = if (mode.isBlank() || mode.equals("system", ignoreCase = true)) "" else " $mode"
        runCommandAsync("重启设备${arg.ifEmpty { "（系统）" }}", "reboot$arg")
    }
}
