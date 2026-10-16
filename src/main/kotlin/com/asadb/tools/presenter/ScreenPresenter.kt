// =====================================================================
// 屏幕操作 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.ScreenPresenter.cs。
// 唯一职责：截图、录屏、分辨率查询等屏幕相关操作。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbSettingsState
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IFileDialogProvider
import com.asadb.tools.view.IFolderOpenProvider
import com.asadb.tools.view.IInfoOutput
import com.asadb.tools.view.IUserInputProvider
import java.io.File

/**
 * 屏幕操作编排器：负责截图 / 录屏 / 分辨率查询 / 模拟输入 / 屏幕投影。
 */
class ScreenPresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val files: IFileDialogProvider,
    private val folderOpen: IFolderOpenProvider,
    private val input: IUserInputProvider
) : PresenterBase(adb, output) {

    /** 最近一次成功保存录屏的目录（供同步结束合并提示）。 */
    var lastSavedDir: String? = null
        private set

    /** 设备截图并保存到本地。 */
    fun screenshot() {
        val savePath = files.chooseScreenshotSavePath() ?: return

        output.appendInfo(">>> 截图 正在执行 ...\r\n")
        runInBackground(
            "截图",
            { adb.runScreenshot(savePath) },
            { msg ->
                if (isErrorMsg(msg)) {
                    output.appendError("<<< 截图：$msg\r\n")
                } else {
                    output.appendInfo("<<< 截图：$msg\r\n")
                    promptOpenFolder(savePath)
                }
            }
        )
    }

    /** 开始录屏。已完成录屏时提示先结束。 */
    fun startRecord() {
        if (adb.isRecording) {
            output.appendInfo("<<< 录屏：已在录制中，请点击「结束录屏」结束并保存\r\n")
            return
        }

        val ok = adb.startScreenRecord()
        if (ok) output.appendInfo(">>> 录屏已开始，点击「结束录屏」结束并保存\r\n")
        else output.appendError(">>> 开始录屏失败\r\n")
    }

    /** 结束录屏并保存到本地。promptOpenFolder 为 false 时不弹「是否打开目录」，供同步结束场景合并提示。 */
    fun stopRecord(promptOpenFolder: Boolean = true) {
        if (!adb.isRecording) {
            output.appendInfo("<<< 录屏：尚未开始录屏\r\n")
            return
        }

        val savePath = files.chooseScreenRecordSavePath() ?: return

        output.appendInfo(">>> 正在结束录屏并保存 ...\r\n")
        runInBackground(
            "录屏",
            { adb.stopScreenRecord(savePath) },
            { msg ->
                if (isErrorMsg(msg)) {
                    output.appendError("<<< 录屏：$msg\r\n")
                } else {
                    output.appendInfo("<<< 录屏：$msg\r\n")
                    val dir = runCatching { java.io.File(savePath).parent }.getOrNull()
                    if (!dir.isNullOrBlank()) lastSavedDir = dir
                    if (promptOpenFolder) this.promptOpenFolder(savePath)
                }
            }
        )
    }

    /** 操作成功保存后提示是否打开所在文件夹。 */
    private fun promptOpenFolder(savePath: String) {
        val dir = runCatching { java.io.File(savePath).parent }.getOrNull()
        if (!dir.isNullOrBlank()) folderOpen.promptOpenFolder(dir)
    }

    /** 获取屏幕分辨率。 */
    fun getScreenResolution() {
        runCommandAsync("获取屏幕分辨率", "shell wm size")
    }

    /** 模拟点击：弹出输入框输入坐标 x y 后执行 input tap。 */
    fun inputTap() {
        val v = input.promptForInput("模拟点击", "输入点击坐标（x y），如 540 960：", "540 960") ?: return
        val p = v.trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (p.size < 2) {
            output.appendError("<<< 模拟点击：坐标格式应为 x y，如 540 960\r\n")
            return
        }
        runCommandAsync("模拟点击 ${p[0]} ${p[1]}", "shell input tap ${p[0]} ${p[1]}")
    }

    /** 模拟滑动：弹出输入框输入起止坐标后执行 input swipe。 */
    fun inputSwipe() {
        val v = input.promptForInput("模拟滑动", "输入滑动坐标（x1 y1 x2 y2），如 540 960 540 300：", "540 960 540 300") ?: return
        val p = v.trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (p.size < 4) {
            output.appendError("<<< 模拟滑动：坐标格式应为 x1 y1 x2 y2\r\n")
            return
        }
        runCommandAsync("模拟滑动", "shell input swipe ${p[0]} ${p[1]} ${p[2]} ${p[3]}")
    }

    /** 模拟输入文本：仅支持 ASCII 字符，空格需输入 %s。 */
    fun inputText() {
        val text = input.promptForInput("模拟输入", "输入要输入的文本（仅支持 ASCII，空格用 %s 表示）：", "") ?: return
        if (text.isBlank()) return
        runCommandAsync("模拟输入 $text", "shell input text ${text.trim()}")
    }

    /** 输入按键事件：弹出输入框输入 keycode（如 4=返回、3=Home、26=电源）。 */
    fun inputKeyEvent() {
        val key = input.promptForInput("按键事件", "输入 keycode（如 4=返回、3=Home、26=电源）：", "4") ?: return
        if (key.isBlank()) return
        runCommandAsync("按键事件 $key", "shell input keyevent ${key.trim()}")
    }

    /** 屏幕投影（scrcpy）：定位 scrcpy 后以独立进程启动，不阻塞 adb 工具窗口。 */
    fun startScrcpy() {
        val scrcpy = resolveScrcpy() ?: return
        output.appendInfo(">>> 正在启动 scrcpy（$scrcpy）...\r\n")
        val msg = adb.launchDetached(listOf(scrcpy), "scrcpy")
        if (msg.contains("失败")) output.appendError("<<< 屏幕投影：$msg\r\n")
        else output.appendInfo("<<< 屏幕投影：$msg\r\n")
    }

    /**
     * 解析 scrcpy 可执行文件：优先使用设置中手动指定的路径；
     * 已指定但文件不存在时明确报错（不静默回退，避免掩盖路径笔误），未指定时才自动探测。
     */
    private fun resolveScrcpy(): String? {
        val configured = AdbSettingsState.getInstance().scrcpyPath.trim()
        if (configured.isNotEmpty()) {
            if (File(configured).isFile) return configured
            output.appendError("<<< 屏幕投影：设置中的 scrcpy 路径不存在：$configured，请在「设置 → 工具配置」中重新选择\r\n")
            return null
        }
        val detected = findScrcpy()
        if (detected == null) {
            output.appendError("<<< 屏幕投影：未找到 scrcpy，请安装 scrcpy 并加入 PATH，或在「设置 → 工具配置」中指定 scrcpy 路径\r\n")
            return null
        }
        return detected
    }

    /** 自动探测 scrcpy 可执行文件：依次尝试 PATH 与各平台常见安装目录。 */
    private fun findScrcpy(): String? {
        val names = if (System.getProperty("os.name").lowercase().contains("win"))
            listOf("scrcpy.exe", "scrcpy") else listOf("scrcpy")

        // 先按 PATH 探测（直接返回命令名，交由系统解析）
        val path = System.getenv("PATH")
        if (!path.isNullOrBlank()) {
            for (dir in path.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                for (n in names) {
                    if (File(dir, n).isFile) return n
                }
            }
        }

        // 各平台常见安装目录
        val os = System.getProperty("os.name").lowercase()
        val dirs = mutableListOf<String>()
        if (os.contains("win")) {
            dirs.add("C:\\Program Files\\scrcpy")
            dirs.add("C:\\Program Files (x86)\\scrcpy")
        } else if (os.contains("mac")) {
            dirs.add("/opt/homebrew/bin")
            dirs.add("/usr/local/bin")
        } else {
            dirs.add("/usr/bin")
            dirs.add("/usr/local/bin")
        }
        for (dir in dirs) {
            for (n in names) {
                val f = File(dir, n)
                if (f.isFile) return f.absolutePath
            }
        }
        return null
    }
}
