// =====================================================================
// 应用配置（Model 层）
// 对应 C# 版 AdbTool.Model.AppSettings，通过 IntelliJ PersistentStateComponent
// 持久化全部运行配置：信息展示区外观、adb 路径、缓存目录与媒体保存目录、
// 签名文件路径，以及外部工具（scrcpy / apksigner / java）路径。
// =====================================================================

package com.asadb.tools.model

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.awt.Color

/**
 * 应用配置：统一保存 / 加载所有设置项，作为程序唯一的配置来源。
 * 注册为 applicationService（见 plugin.xml），由 IDE 自动持久化。
 */
@Service(Service.Level.APP)
@State(name = "AdbSettingsState", storages = [Storage("adb-tools.xml")])
class AdbSettingsState : PersistentStateComponent<AdbSettingsState> {

    /** 字体大小。 */
    @JvmField
    var fontSize: Int = 20

    /** 字体名（默认按平台：macOS 用 Menlo，其余平台用 Consolas）。 */
    @JvmField
    var fontName: String = defaultFontName()

    /** 字体颜色，格式 "R,G,B"。 */
    @JvmField
    var fontColor: String = "99,217,139"

    /** 背景颜色，格式 "R,G,B"。 */
    @JvmField
    var backColor: String = "16,23,34"

    /** adb 可执行文件路径。 */
    @JvmField
    var adbPath: String = ""

    /** 启动项缓存保存目录。 */
    @JvmField
    var cacheDirectory: String = ""

    /** 日志 / 视频 / 截图保存目录。 */
    @JvmField
    var mediaDirectory: String = ""

    /** 安装应用（APK）时默认定位的文件夹。 */
    @JvmField
    var apkDirectory: String = ""

    /** 默认签名 APK 文件夹（签名 / 校验 / MD5 选择 APK 时默认定位的文件夹）。 */
    @JvmField
    var signApkDirectory: String = ""

    /** 签名文件（密钥库文件 .keystore / .jks）路径。 */
    @JvmField
    var signKeystorePath: String = ""

    /** scrcpy 可执行文件路径（未配置时自动探测 PATH 与各平台常见安装目录）。 */
    @JvmField
    var scrcpyPath: String = ""

    /** apksigner 可执行文件 / jar 路径（未配置时按 adb 与 SDK 位置自动探测）。 */
    @JvmField
    var apksignerPath: String = ""

    /** java 可执行文件路径（未配置时使用当前运行 IDE 自带的 JVM）。 */
    @JvmField
    var javaPath: String = ""

    override fun getState(): AdbSettingsState = this

    override fun loadState(state: AdbSettingsState) {
        fontSize = state.fontSize
        fontName = state.fontName
        fontColor = state.fontColor
        backColor = state.backColor
        adbPath = state.adbPath
        cacheDirectory = state.cacheDirectory
        mediaDirectory = state.mediaDirectory
        apkDirectory = state.apkDirectory
        signApkDirectory = state.signApkDirectory
        signKeystorePath = state.signKeystorePath
        scrcpyPath = state.scrcpyPath
        apksignerPath = state.apksignerPath
        javaPath = state.javaPath
    }

    companion object {
        /** 获取全局配置单例。 */
        fun getInstance(): AdbSettingsState = service()

        /** 默认缓存目录：IDE 系统目录下的 adb-tools/cache（未另行设置时生效）。 */
        fun defaultCacheDirectory(): String =
            java.nio.file.Paths.get(com.intellij.openapi.application.PathManager.getSystemPath(), "adb-tools", "cache").toString()

        /** 默认命令窗字体：macOS 无 Consolas，使用 Menlo；其余平台用 Consolas。 */
        fun defaultFontName(): String =
            if (System.getProperty("os.name").contains("mac", ignoreCase = true)) "Menlo" else "Consolas"

        /** 将 "R,G,B" 形式的颜色字符串解析为 Color；解析失败返回 null。 */
        fun parseColor(text: String): Color? {
            if (text.isBlank()) return null
            val parts = text.split(',')
            if (parts.size >= 3) {
                val r = parts[0].trim().toIntOrNull()
                val g = parts[1].trim().toIntOrNull()
                val b = parts[2].trim().toIntOrNull()
                if (r != null && g != null && b != null) return Color(r, g, b)
            }
            return null
        }

        /** 将 Color 格式化为 "R,G,B" 字符串。 */
        fun colorString(color: Color): String = "${color.red},${color.green},${color.blue}"
    }
}
