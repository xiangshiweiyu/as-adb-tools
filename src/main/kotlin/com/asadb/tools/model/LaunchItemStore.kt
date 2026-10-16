// =====================================================================
// 启动项持久化存储（Model 层）
// 对应 C# 版 AdbTool.Model.LaunchItemStore。
// 负责把自定义的「包名启动项」「action 启动项」读写到本地文本文件，
// 以制表符分隔、UTF-8 编码，使重启 IDE 后仍可复用。
// =====================================================================

package com.asadb.tools.model

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 启动项存储抽象：Presenter 依赖此接口，不依赖具体文件实现。
 */
interface ILaunchItemStore {
    fun loadPackages(): List<LaunchPackage>
    fun savePackages(items: List<LaunchPackage>)
    fun loadActions(): List<LaunchAction>
    fun saveActions(items: List<LaunchAction>)

    /** 设置缓存保存目录（用于存放启动项缓存文件）。 */
    fun setCacheDirectory(dir: String)

    /** 确保缓存目录可用：已设置但目录被删除时自动重建，返回是否已可用。 */
    fun ensureCacheDirectory(): Boolean
}

/**
 * 基于文本文件的启动项存储实现。
 */
class LaunchItemStore : ILaunchItemStore {
    /** 缓存保存基目录：已设置用用户目录，否则用默认缓存目录。 */
    private val baseDir: String
        get() {
            val dir = AdbSettingsState.getInstance().cacheDirectory
            return if (dir.isBlank()) AdbSettingsState.defaultCacheDirectory() else dir
        }

    private val packagePath: String get() = Paths.get(baseDir, "AdbTool.packages.txt").toString()
    private val actionPath: String get() = Paths.get(baseDir, "AdbTool.actions.txt").toString()

    /** 设置缓存保存目录并持久化到统一配置。 */
    override fun setCacheDirectory(dir: String) {
        if (dir.isBlank()) return
        AdbSettingsState.getInstance().cacheDirectory = dir
    }

    /** 确保缓存目录可用：已设置但目录被删除时自动重建，返回是否已可用。 */
    override fun ensureCacheDirectory(): Boolean {
        val dir = AdbSettingsState.getInstance().cacheDirectory
        // 未设置缓存目录时返回 false，由上层引导用户设置
        if (dir.isBlank()) return false
        return try {
            if (!Files.isDirectory(Paths.get(dir))) Files.createDirectories(Paths.get(dir))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 加载全部包名启动项。 */
    override fun loadPackages(): List<LaunchPackage> {
        val list = mutableListOf<LaunchPackage>()
        try {
            val file = File(packagePath)
            if (!file.exists()) return list
            file.readLines(StandardCharsets.UTF_8).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t')
                if (parts.size < 2) return@forEach
                list.add(
                    LaunchPackage(
                        name = parts[0].trim(),
                        packageName = parts[1].trim()
                    )
                )
            }
        } catch (e: Exception) {
            // 读取失败时返回空列表
        }
        return list
    }

    /** 保存全部包名启动项。 */
    override fun savePackages(items: List<LaunchPackage>) {
        try {
            val sb = StringBuilder()
            for (p in items) {
                if (p.packageName.isBlank()) continue
                sb.append(p.name).append('\t').append(p.packageName).append('\n')
            }
            writeText(packagePath, sb.toString())
        } catch (e: Exception) {
            // 写入失败静默忽略
        }
    }

    /** 加载全部 action 启动项。 */
    override fun loadActions(): List<LaunchAction> {
        val list = mutableListOf<LaunchAction>()
        try {
            val file = File(actionPath)
            if (!file.exists()) return list
            file.readLines(StandardCharsets.UTF_8).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t')
                if (parts.size < 2) return@forEach

                val a = LaunchAction(
                    name = parts[0].trim(),
                    action = parts[1].trim()
                )

                // 新格式第 3 列为应用名称，参数从第 4 列开始成对出现；
                // 旧格式（无应用名称列）参数从第 3 列开始。通过列数奇偶判断：
                // 旧格式列数 = 2 + 2*参数数（恒为偶数），新格式在此基础上多一列应用名称（恒为奇数）。
                var paramStart = 2
                if (parts.size >= 3 && (parts.size - 3) % 2 == 0) {
                    a.appName = parts[2].trim()
                    paramStart = 3
                }

                // 参数 key/value 成对出现
                var i = paramStart
                while (i + 1 < parts.size) {
                    a.parameters.add(LaunchParameter(parts[i].trim(), parts[i + 1].trim()))
                    i += 2
                }
                list.add(a)
            }
        } catch (e: Exception) {
            // 读取失败时返回空列表
        }
        return list
    }

    /** 保存全部 action 启动项。 */
    override fun saveActions(items: List<LaunchAction>) {
        try {
            val sb = StringBuilder()
            for (a in items) {
                if (a.action.isBlank()) continue
                sb.append(a.name).append('\t').append(a.action)
                sb.append('\t').append(a.appName)
                for (p in a.parameters) {
                    sb.append('\t').append(p.key).append('\t').append(p.value)
                }
                sb.append('\n')
            }
            writeText(actionPath, sb.toString())
        } catch (e: Exception) {
            // 写入失败静默忽略
        }
    }

    /** 写入文本文件（自动创建父目录，UTF-8 编码）。 */
    private fun writeText(path: String, text: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(text, StandardCharsets.UTF_8)
    }
}
