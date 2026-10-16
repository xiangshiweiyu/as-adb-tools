// =====================================================================
// 启动 APK Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.LaunchPresenter.cs。
// 唯一职责：编排「包名启动」「action 启动」的完整流程——
// 读取启动项、弹出选择窗口、持久化新增项、组装并执行 adb 启动命令。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.IAdbService
import com.asadb.tools.model.ILaunchItemStore
import com.asadb.tools.model.LaunchAction
import com.asadb.tools.model.LaunchPackage
import com.asadb.tools.view.IConfirmationProvider
import com.asadb.tools.view.IFileDialogProvider
import com.asadb.tools.view.IInfoOutput
import com.asadb.tools.view.ILaunchView

/**
 * 启动 APK 编排器：负责以包名或 Intent action 启动应用。
 */
class LaunchPresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val store: ILaunchItemStore,
    private val view: ILaunchView,
    private val confirmation: IConfirmationProvider,
    private val fileDialog: IFileDialogProvider
) : PresenterBase(adb, output) {

    // 每次点击启动时校验缓存目录：已设置但被删除则自动重建（不提示），
    // 未设置时才引导用户选择目录。
    private fun ensureCacheDirectory() {
        // 已设置缓存目录时，无论目录是否被删除都自动重建，无需提示用户
        if (store.ensureCacheDirectory()) return

        if (!confirmation.confirm("检测到尚未设置缓存保存目录（用于保存自定义的启动项）。是否立即设置？")) return

        val dir = fileDialog.chooseCacheDirectory()
        if (!dir.isNullOrBlank()) store.setCacheDirectory(dir)
    }

    /** 以包名方式启动应用。 */
    fun launchByPackage() {
        ensureCacheDirectory()

        val packages = store.loadPackages().toMutableList()
        val chosen = view.showPackageLaunch(packages)

        // 无论是否启动都统一落盘：用户在弹窗中可能新增或删除了启动项。
        store.savePackages(packages)

        if (chosen == null || chosen.packageName.isBlank()) return

        runCommandAsync("启动APK ${chosen.displayText}", "shell am start ${chosen.packageName.trim()}")
    }

    /** 以 Intent action 方式启动应用。 */
    fun launchByAction() {
        ensureCacheDirectory()

        val actions = store.loadActions().toMutableList()
        val chosen = view.showActionLaunch(actions)

        // 无论是否启动都统一落盘：用户在弹窗中可能新增或删除了启动项。
        store.saveActions(actions)

        if (chosen == null || chosen.action.isBlank()) return

        runCommandAsync("启动APK ${chosen.displayText}", buildActionArgs(chosen))
    }

    /** 读取全部包名启动项（供主界面下拉框回填）。 */
    fun getPackages(): List<LaunchPackage> = store.loadPackages()

    /** 读取全部 action 启动项（供主界面下拉框回填）。 */
    fun getActions(): List<LaunchAction> = store.loadActions()

    /** 直接以指定包名启动应用（主界面下拉框选中项，跳过弹窗）。 */
    fun launchByPackage(item: LaunchPackage) {
        if (item.packageName.isBlank()) return
        runCommandAsync("启动APK ${item.displayText}", "shell am start ${item.packageName.trim()}")
    }

    /** 直接以指定 action 启动应用（主界面下拉框选中项，跳过弹窗）。 */
    fun launchByAction(item: LaunchAction) {
        if (item.action.isBlank()) return
        runCommandAsync("启动APK ${item.displayText}", buildActionArgs(item))
    }

    /** 将 action 启动项组装为 adb shell am start 命令参数。 */
    private fun buildActionArgs(action: LaunchAction): String {
        val sb = StringBuilder()
        sb.append("shell am start -a ").append(action.action.trim())

        for (p in action.parameters) {
            if (p.key.isBlank()) continue
            // key/value 均加引号，避免含空格的参数被拆分。
            sb.append(" --es \"").append(p.key.trim())
                .append("\" \"").append(p.value).append("\"")
        }

        return sb.toString()
    }
}
