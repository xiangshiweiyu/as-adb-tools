// =====================================================================
// 日志抓取 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.LogPresenter.cs。
// 唯一职责：日志抓取的开始与停止，以及日志文件的落盘。
// 格式选择通过 ILogFormat 策略完成（开闭原则），不在本类中进行 if-else 分支。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IFileDialogProvider
import com.asadb.tools.view.IFolderOpenProvider
import com.asadb.tools.view.IInfoOutput
import com.asadb.tools.view.ILogCaptureView
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets

/**
 * 日志抓取编排器：负责 adb logcat 的开始 / 停止与文件写入。
 */
class LogPresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val logView: ILogCaptureView,
    private val files: IFileDialogProvider,
    private val folderOpen: IFolderOpenProvider
) : PresenterBase(adb, output) {

    /** 日志文件写入流（后台 logcat 读线程会持续写入，需加锁保护）。 */
    private var logWriter: BufferedWriter? = null

    /** 日志保存路径。 */
    private var logSavePath: String? = null

    /** 写入锁：保护 [logWriter] 的写入与关闭操作。 */
    private val logLock = Any()

    /** 最近一次日志保存路径；未抓取过则为 null。 */
    val lastLogPath: String?
        get() = logSavePath

    /** 开始抓取日志：选择保存路径后按所选格式启动 logcat 并写入文件。 */
    fun startLogCapture() {
        if (adb.isLogging) {
            output.appendInfo("<<< 日志抓取：已在抓取中，请先点击「停止抓取」\r\n")
            return
        }

        val savePath = files.chooseLogSavePath() ?: return

        // 通过策略工厂获取日志格式，避免在业务逻辑中硬编码 if-else（开闭原则）
        val format = LogFormatFactory.create(logView.getLogCaptureMode())

        // 追加 TAG 过滤参数：设置了过滤时以 -s TAG 限制只输出指定 TAG 的日志
        var args = format.arguments
        val filter = logView.getLogFilter().trim()
        if (filter.isNotEmpty()) {
            args = if (args.isBlank()) "-s $filter" else "$args -s $filter"
        }

        // 创建文件写入流失败时提示并中止
        val writer = try {
            BufferedWriter(FileWriter(File(savePath), StandardCharsets.UTF_8, false))
        } catch (e: Exception) {
            output.appendError("<<< 无法创建日志文件：${e.message}\r\n")
            return
        }

        synchronized(logLock) { logWriter = writer }
        logSavePath = savePath

        output.appendInfo(">>> 开始日志抓取（${format.label}${if (filter.isNotEmpty()) "，TAG 过滤：$filter" else ""}），将保存到 $savePath ...\r\n")
        val ok = adb.startLogcat(args) { line ->
            synchronized(logLock) {
                logWriter?.let { w ->
                    runCatching { w.write(line); w.newLine() }
                }
            }
        }

        if (!ok) {
            closeLogWriter()
            output.appendError("<<< 开始日志抓取失败\r\n")
        }
    }

    /** 停止抓取日志：终止 logcat 并关闭文件写入流。promptOpenFolder 为 false 时不弹「是否打开目录」，供同步结束场景合并提示。 */
    fun stopLogCapture(promptOpenFolder: Boolean = true) {
        if (!adb.isLogging) {
            output.appendInfo("<<< 日志抓取：当前未在抓取中\r\n")
            return
        }
        adb.stopLogcat()
        closeLogWriter()
        output.appendInfo("<<< 日志抓取成功，已保存到 $logSavePath\r\n")

        if (!promptOpenFolder) return

        val dir = logSavePath?.let { runCatching { File(it).parent }.getOrNull() }
        if (!dir.isNullOrBlank()) folderOpen.promptOpenFolder(dir)
    }

    /** 关闭日志文件写入流。 */
    private fun closeLogWriter() {
        synchronized(logLock) {
            logWriter?.let { w ->
                runCatching { w.flush(); w.close() }
            }
            logWriter = null
        }
    }

    /** 释放资源：停止日志抓取并关闭文件写入流，防止残留 logcat 进程与文件句柄。 */
    override fun dispose() {
        super.dispose()
        if (adb.isLogging) adb.stopLogcat()
        closeLogWriter()
    }
}
