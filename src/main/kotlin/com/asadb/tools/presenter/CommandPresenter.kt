// =====================================================================
// 通用命令执行 Presenter（Presenter 层）
// 对应 C# 版 AdbTool.Presenter.CommandPresenter.cs。
// 唯一职责：执行任意一条 adb 命令并回显结果。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IInfoOutput

/**
 * 通用命令执行器：执行任意 adb 命令并统一回显。
 */
class CommandPresenter(
    adb: IAdbService,
    output: IInfoOutput
) : PresenterBase(adb, output) {

    /** 执行任意 adb 命令并回显结果。 */
    fun executeCommand(desc: String, args: String) {
        runCommandAsync(desc, args)
    }
}
