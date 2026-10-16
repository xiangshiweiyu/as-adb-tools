// =====================================================================
// Presenter 基类（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.PresenterBase。
// 封装「后台执行 + 统一回显」的通用流程，异步统一使用 RxJava，
// 避免各业务 Presenter 重复编写线程切换、异常处理与结果格式化代码。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbResult
import com.asadb.tools.model.AdbSettingsState
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IInfoOutput
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.security.MessageDigest

/**
 * Presenter 基类：提供后台执行与结果回显的通用能力。
 */
abstract class PresenterBase(
    protected val adb: IAdbService,
    protected val output: IInfoOutput
) {
    /** 全部异步订阅句柄，统一在 [dispose] 时释放，避免订阅泄漏。 */
    protected val disposables = CompositeDisposable()

    /** 后台执行一条 adb 命令，完成后按统一格式回显结果。 */
    protected fun runCommandAsync(desc: String, args: String) {
        output.appendInfo(">>> $desc 正在执行 ...\r\n")
        runInBackground(desc, { adb.runAdb(args) }, { r -> appendCommandResult(desc, r) })
    }

    /**
     * 在后台线程执行任意工作，完成后回到 EDT 回调；
     * 执行过程中的异常统一以红色格式回显。
     */
    protected fun <T : Any> runInBackground(desc: String, work: () -> T, onCompleted: (T) -> Unit) {
        Single.fromCallable { work() }
            .subscribeOn(Schedulers.io())
            .observeOn(EdtSchedulers.edt)
            .subscribe(
                { result -> onCompleted(result) },
                { e -> output.appendError(">>> $desc 异常：${e.message}\r\n") }
            )
            .also { disposables.add(it) }
    }

    /**
     * 解析默认签名 APK 文件夹：返回设置中配置且真实存在的目录；
     * 未配置或目录不存在时返回 null，由调用方回退到常规默认定位。
     */
    protected fun signApkDefaultDirectory(): String? {
        val p = AdbSettingsState.getInstance().signApkDirectory
        return if (p.isNotBlank() && File(p).isDirectory) p else null
    }

    /** 流式计算文件的 MD5（大写十六进制）。 */
    protected fun md5Of(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    /** 将命令执行结果格式化后追加到信息区。 */
    protected fun appendCommandResult(desc: String, r: AdbResult) {
        val sb = StringBuilder()
        sb.appendLine("<<< $desc 执行完成，退出码 ${r.exitCode}")
        if (r.output.isNotBlank()) sb.appendLine(r.output.trimEnd())
        if (r.error.isNotBlank()) {
            sb.appendLine("[stderr]")
            sb.appendLine(r.error.trimEnd())
        }
        sb.appendLine("----------------------------------------")
        if (r.exitCode == 0) output.appendInfo(sb.toString())
        else output.appendError(sb.toString())
    }

    /** 将 AdbResult 概括为「成功 / 失败：原因」文案（用于分步回显）。 */
    protected fun describeAdbResult(r: AdbResult): String {
        if (r.exitCode == 0) return "成功"
        val err = if (r.error.isBlank()) r.output.trim() else r.error.trim()
        return "失败：$err"
    }

    /** 判断一段结果消息是否为错误信息（含「失败/异常」即视为错误）。 */
    protected fun isErrorMsg(msg: String?): Boolean {
        if (msg.isNullOrBlank()) return true
        return msg.contains("失败") || msg.contains("异常")
    }

    /** 释放全部异步订阅。 */
    open fun dispose() {
        disposables.clear()
    }
}
