// =====================================================================
// ADB 命令执行结果（Model 层）
// 对应 C# 版 AdbTool.Model.AdbResult，纯数据结构。
// =====================================================================

package com.asadb.tools.model

/**
 * 表示一次 ADB 命令的执行结果。
 *
 * @param exitCode 退出码：0 表示成功，非 0 表示失败，-1 表示执行过程中发生异常
 * @param output   标准输出内容
 * @param error    标准错误内容
 */
data class AdbResult(
    var exitCode: Int,
    var output: String,
    var error: String
) {
    constructor() : this(-1, "", "")
}
