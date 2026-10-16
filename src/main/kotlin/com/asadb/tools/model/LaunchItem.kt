// =====================================================================
// 启动 APK 数据模型（Model 层）
// 对应 C# 版 AdbTool.Model.LaunchItem，纯数据结构，不含 UI 与业务逻辑。
// =====================================================================

package com.asadb.tools.model

/** action 启动携带的单个 key-value 参数。 */
data class LaunchParameter(var key: String = "", var value: String = "")

/** 包名启动项：以包名方式启动一个应用。 */
data class LaunchPackage(
    var name: String = "",
    var packageName: String = ""
) {
    /** 下拉框展示文本：名称（包名）。 */
    val displayText: String
        get() = if (name.isBlank()) packageName else "$name（$packageName）"

    /** 简洁显示名：优先用户自定义名称，为空时退回包名。 */
    val displayName: String
        get() = if (name.isBlank()) packageName else name
}

/** action 启动项：以 Intent action 方式启动，可附带参数。 */
data class LaunchAction(
    var name: String = "",
    var appName: String = "",
    var action: String = "",
    var parameters: MutableList<LaunchParameter> = mutableListOf()
) {
    /** 下拉框展示文本：应用名称 / 页面名称（action）。 */
    val displayText: String
        get() {
            val text = if (name.isBlank()) action else "$name（$action）"
            return if (appName.isBlank()) text else "$appName / $text"
        }

    /** 简洁显示名：应用名称 / 页面名称，优先用户自定义名称，为空时退回 action。 */
    val displayName: String
        get() {
            val n = if (name.isBlank()) action else name
            return if (appName.isBlank()) n else "$appName / $n"
        }
}
