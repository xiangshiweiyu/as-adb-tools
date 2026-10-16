// =====================================================================
// APK 签名 Presenter（Presenter 层）
// 一键签名：选择 APK 后，使用签名文件 / 别名 / 密码直接签名，
// 统一使用 apksigner（v1/v2/v3 签名），后台执行并统一回显结果。
// 签名文件路径在设置中配置，签名别名与密码一律从环境变量读取，
// 代码中不内置任何凭据。
// =====================================================================

package com.asadb.tools.presenter

import com.asadb.tools.model.AdbResult
import com.asadb.tools.model.AdbSettingsState
import com.asadb.tools.model.IAdbService
import com.asadb.tools.view.IFileDialogProvider
import com.asadb.tools.view.IFolderOpenProvider
import com.asadb.tools.view.IInfoOutput
import java.io.File

/**
 * APK 签名编排器：固定签名参数，只让用户选择 APK 即可完成签名。
 */
class SignPresenter(
    adb: IAdbService,
    output: IInfoOutput,
    private val files: IFileDialogProvider,
    private val folders: IFolderOpenProvider
) : PresenterBase(adb, output) {

    companion object {
        // ---- 签名凭据环境变量（不在代码中内置任何别名 / 密码，避免公开泄露）----

        /** 签名密钥别名环境变量。 */
        private const val ENV_SIGN_ALIAS = "ADB_TOOLS_SIGN_ALIAS"

        /** 签名库密码环境变量。 */
        private const val ENV_SIGN_STORE_PASS = "ADB_TOOLS_SIGN_STORE_PASS"

        /** 签名私钥密码环境变量。 */
        private const val ENV_SIGN_KEY_PASS = "ADB_TOOLS_SIGN_KEY_PASS"
    }

    /** 一键签名：选择 APK 后直接签名。 */
    fun signApk() {
        val params = signParams("-signed") ?: return
        signApkInternal("APK签名", params)
    }

    /** 签名校验md5一条龙：签名 → 签名校验 → 生成 MD5 依次执行，签名失败即中断。 */
    fun signOneStop() {
        val params = signParams("-signed") ?: return
        signOneStopInternal("签名校验md5一条龙", "签名校验md5（一条龙）", params)
    }

    /** 签名文件路径：取设置中配置的路径；未配置时回显错误并返回 null。 */
    private fun keystorePath(): String? {
        val configured = AdbSettingsState.getInstance().signKeystorePath.trim()
        if (configured.isEmpty()) {
            output.appendError("<<< 未配置签名文件：请在插件「设置 → 签名配置」中选择签名所用的密钥库文件（.keystore / .jks）。\r\n")
            return null
        }
        return configured
    }

    /** 组装签名参数：签名文件取设置项，别名与密码从环境变量读取，任一缺失即回显错误并返回 null。 */
    private fun signParams(suffix: String): SignParams? {
        val alias = System.getenv(ENV_SIGN_ALIAS)
        val storePass = System.getenv(ENV_SIGN_STORE_PASS)
        val keyPass = System.getenv(ENV_SIGN_KEY_PASS)
        if (alias.isNullOrBlank() || storePass.isNullOrBlank() || keyPass.isNullOrBlank()) {
            output.appendError(credentialMissingHint("APK签名", ENV_SIGN_ALIAS, ENV_SIGN_STORE_PASS, ENV_SIGN_KEY_PASS))
            return null
        }
        val keystore = keystorePath() ?: return null
        return SignParams(keystore, alias, storePass, keyPass, suffix)
    }

    /** 签名凭据缺失提示：列出需要配置的环境变量（环境变量在 IDE 启动时读取，修改后需重启）。 */
    private fun credentialMissingHint(funcName: String, vararg envNames: String): String {
        val vars = envNames.joinToString(" / ")
        return "<<< $funcName 缺少签名凭据：请在启动 Android Studio 前设置环境变量 $vars，重启 IDE 后重试。\r\n"
    }

    /** 一键签名公共流程：选择 APK，校验签名文件后按统一参数执行签名。 */
    private fun signApkInternal(funcName: String, params: SignParams) {
        val apkPath = files.chooseApkFile(signApkDefaultDirectory()) ?: return
        val apk = File(apkPath)
        if (!apk.isFile) {
            output.appendError("<<< APK 文件不存在：$apkPath\r\n")
            return
        }

        val ks = File(params.keystore)
        if (!ks.isFile) {
            output.appendError("<<< 签名文件不存在：${params.keystore}，请检查签名文件路径。\r\n")
            return
        }

        // 输出到 APK 同目录下，追加对应后缀
        val outFile = File(apk.parentFile, "${apk.nameWithoutExtension}${params.suffix}.apk").absolutePath
        output.appendInfo(">>> $funcName ${apk.name} 正在执行 ...\r\n")

        runInBackground(funcName, { signProcess(apk, outFile, params) }, { r -> onSigned(r, apk.name, outFile, funcName) })
    }

    /** 执行签名：统一使用 apksigner；未找到 apksigner 时返回错误结果。 */
    private fun signProcess(apk: File, outFile: String, params: SignParams): AdbResult {
        val apksigner = findApksigner()
        if (apksigner != null) {
            return adb.runRawProcess(buildApksignerCommand(apksigner, apk, outFile, params.keystore, params.alias, params.storePass, params.keyPass), "apksigner")
        }
        return AdbResult().apply {
            exitCode = -1
            error = "未找到签名工具 apksigner：请在插件「设置 → 工具配置」中指定 apksigner 路径，或安装 Android SDK build-tools（并在「设置 → adb配置」中配置 adb 路径）后重试。"
        }
    }

    /** 签名校验：选择 APK 后执行 apksigner verify -v。 */
    fun verifyApkSignature() {
        val apkPath = files.chooseApkFile(signApkDefaultDirectory()) ?: return
        val apk = File(apkPath)
        if (!apk.isFile) {
            output.appendError("<<< APK 文件不存在：$apkPath\r\n")
            return
        }
        runVerifyInBackground(apk, "签名校验")
    }

    /** 后台执行签名校验并统一回显结果。 */
    private fun runVerifyInBackground(apk: File, funcName: String) {
        output.appendInfo(">>> $funcName ${apk.name} 正在执行 ...\r\n")
        runInBackground(funcName, { runVerifyProcess(apk) }, { r -> appendCommandResult("$funcName ${apk.name}", r) })
    }

    /** 后台执行签名校验（统一使用 apksigner），返回校验结果。 */
    private fun runVerifyProcess(apk: File): AdbResult {
        val apksigner = findApksigner()
        if (apksigner != null) {
            return adb.runRawProcess(buildVerifyCommand(apksigner, apk), "apksigner")
        }
        return AdbResult().apply {
            exitCode = -1
            error = "未找到签名校验工具 apksigner：请在插件「设置 → 工具配置」中指定 apksigner 路径，或安装 Android SDK build-tools（并在「设置 → adb配置」中配置 adb 路径）后重试。"
        }
    }

    /** 后台执行「签名 → 校验 → MD5」公共链路：签名失败即中断，不再执行校验与 MD5。 */
    private fun runSignVerifyMd5Chain(
        params: SignParams,
        apk: File,
        outFile: String
    ): Triple<AdbResult, AdbResult?, String?> {
        // 1. 签名
        val signResult = signProcess(apk, outFile, params)
        // 签名失败则中断，不再执行校验与 MD5
        if (signResult.exitCode != 0 || !File(outFile).isFile) {
            return Triple(signResult, null, null)
        }
        // 2. 签名校验
        val verifyResult = runVerifyProcess(File(outFile))
        // 3. 生成 MD5
        return Triple(signResult, verifyResult, md5Of(File(outFile)))
    }

    /** 一组签名参数（签名文件、别名、库/私钥密码与输出后缀）。 */
    private data class SignParams(
        val keystore: String,
        val alias: String,
        val storePass: String,
        val keyPass: String,
        val suffix: String
    )

    /** 签名一条龙公共流程：签名 → 校验 → MD5，任一前置失败即中断。 */
    private fun signOneStopInternal(funcName: String, resultDesc: String, params: SignParams) {
        val apkPath = files.chooseApkFile(signApkDefaultDirectory()) ?: return
        val apk = File(apkPath)
        if (!apk.isFile) {
            output.appendError("<<< APK 文件不存在：$apkPath\r\n")
            return
        }

        val ks = File(params.keystore)
        if (!ks.isFile) {
            output.appendError("<<< 签名文件不存在：${params.keystore}，请检查签名文件路径。\r\n")
            return
        }

        // 输出到 APK 同目录下，追加对应后缀
        val outFile = File(apk.parentFile, "${apk.nameWithoutExtension}${params.suffix}.apk").absolutePath
        output.appendInfo(">>> $funcName ${apk.name} 正在执行 ...\r\n")

        // 复用公共「签名 → 校验 → MD5」链路，签名失败即中断
        runInBackground(funcName, {
            runSignVerifyMd5Chain(params, apk, outFile)
        }, { t -> onOneStopDone(funcName, resultDesc, apk.name, outFile, t) })
    }

    /** 签名一条龙结果回显：签名成功则输出校验与 MD5，失败按统一格式回显。 */
    private fun onOneStopDone(funcName: String, resultDesc: String, apkName: String, outFile: String, t: Triple<AdbResult, AdbResult?, String?>) {
        val (signResult, verifyResult, md5) = t
        if (signResult.exitCode != 0 || !File(outFile).isFile) {
            appendCommandResult(resultDesc, signResult)
            return
        }
        val sb = StringBuilder()
        sb.appendLine("<<< $funcName 完成：$apkName")
        sb.appendLine("    输出文件：$outFile")
        verifyResult?.let { v ->
            if (v.exitCode == 0) {
                sb.appendLine("    签名校验：通过")
                if (v.output.isNotBlank()) sb.appendLine(v.output.trimEnd())
            } else {
                sb.appendLine("    签名校验：失败")
                if (v.error.isNotBlank()) sb.appendLine("[stderr] ${v.error.trimEnd()}")
            }
        }
        if (md5 != null) {
            sb.appendLine("    签名完成APK MD5：$md5")
        }
        sb.appendLine("----------------------------------------")
        output.appendInfo(sb.toString())
        File(outFile).parent?.let { folders.promptOpenFolder(it) }
    }

    /** 构造 apksigner verify 命令（verbose 模式输出完整签名信息）。 */
    private fun buildVerifyCommand(signer: String, apk: File): List<String> {
        val common = listOf("verify", "-v", apk.absolutePath)
        return when {
            signer.endsWith(".jar") -> listOf(javaBin(), "-jar", signer) + common
            // Windows 下 .bat 脚本不能直接执行，需经 cmd /c 包装
            signer.endsWith(".bat") && isWindows() -> listOf("cmd", "/c", signer) + common
            else -> listOf(signer) + common
        }
    }

    // ---- 签名工具定位 ----

    /** 定位 apksigner：优先使用设置中手动指定的路径，未指定时叠加 adb 反推、环境变量、AS 平台库与常见 SDK 位置，取版本号最高的 build-tools。 */
    private fun findApksigner(): String? {
        // 设置中手动指定的 apksigner 优先（支持 apksigner.jar / .bat / 可执行文件）
        val configured = AdbSettingsState.getInstance().apksignerPath.trim()
        if (configured.isNotEmpty()) return configured.takeIf { File(it).isFile }

        // 签名流程可能从不触发 adb 探测，导致 adbPath 停留在兜底值 "adb"；
        // 先强制刷新一次 adb 定位，确保拿到真实 adb 路径用于反推 SDK 根目录
        adb.findAdb()

        val sdkRoots = mutableListOf<String>()

        // adb 位于 <SDK>/platform-tools/adb.exe，其父级即 SDK 根目录；
        // 覆盖：当前实例 adbPath、设置中持久化的 adb 路径、PATH 中解析出的 adb
        val adbPaths = mutableListOf(adb.adbPath, AdbSettingsState.getInstance().adbPath)
        val adbName = if (isWindows()) "adb.exe" else "adb"
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir ->
            if (dir.isNotBlank()) adbPaths.add(File(dir, adbName).absolutePath)
        }
        adbPaths.forEach { adbPath ->
            if (adbPath.isNotBlank() && !adbPath.equals("adb", ignoreCase = true)) {
                File(adbPath).parentFile?.parentFile?.let {
                    if (it.isDirectory) sdkRoots.add(it.absolutePath)
                }
            }
        }
        // 环境变量与 Android Studio 平台库（AS 中配置的本地 SDK 路径，最可靠）
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { env ->
            System.getenv(env)?.takeIf { it.isNotBlank() }?.let { sdkRoots.add(it) }
        }
        adb.detectSdkPath()?.let { sdkRoots.add(it) }
        // 各操作系统常见 SDK 安装位置（macOS / Linux / Windows）
        val userHome = System.getProperty("user.home")
        listOf(
            File(userHome, "Library/Android/sdk").absolutePath,
            File(userHome, "Android/Sdk").absolutePath
        ).forEach {
            if (File(it).isDirectory) sdkRoots.add(it)
        }
        System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let {
            val p = File(it, "Android/Sdk")
            if (p.isDirectory) sdkRoots.add(p.absolutePath)
        }

        // 遍历 build-tools 下各版本目录，选版本号最高的 apksigner
        var best: File? = null
        for (root in sdkRoots) {
            val bt = File(root, "build-tools")
            if (!bt.isDirectory) continue
            bt.listFiles()?.filter { it.isDirectory }?.forEach { v ->
                val jar = File(v, "apksigner.jar")
                val bat = File(v, "apksigner.bat")
                val exe = when {
                    jar.isFile -> jar
                    bat.isFile -> bat
                    else -> null
                }
                if (exe != null && (best == null || v.name > best!!.name)) best = exe
            }
        }
        return best?.absolutePath
    }

    // ---- 签名命令构造 ----

    /** 构造 apksigner sign 命令；若找到的是 apksigner.jar，则用当前 JVM 直接启动，避免依赖环境 JAVA_HOME。 */
    private fun buildApksignerCommand(
        signer: String,
        apk: File,
        outFile: String,
        ksPath: String,
        alias: String,
        storePass: String,
        keyPass: String
    ): List<String> {
        val common = listOf(
            "sign",
            "--ks", ksPath,
            "--ks-key-alias", alias,
            "--ks-pass", "pass:$storePass",
            "--key-pass", "pass:$keyPass",
            "--out", outFile,
            apk.absolutePath
        )
        return when {
            signer.endsWith(".jar") -> listOf(javaBin(), "-jar", signer) + common
            // Windows 下 .bat 脚本不能直接执行，需经 cmd /c 包装
            signer.endsWith(".bat") && isWindows() -> listOf("cmd", "/c", signer) + common
            else -> listOf(signer) + common
        }
    }

    /** 签名完成：成功则回显输出文件并提示打开目录，失败按统一格式回显错误。 */
    private fun onSigned(r: AdbResult, apkName: String, outFile: String, funcName: String = "APK签名") {
        if (r.exitCode == 0 && File(outFile).isFile) {
            val sb = StringBuilder()
            sb.appendLine("<<< $funcName 成功：$apkName")
            sb.appendLine("    输出文件：$outFile")
            if (r.output.isNotBlank()) sb.appendLine(r.output.trimEnd())
            sb.appendLine("----------------------------------------")
            output.appendInfo(sb.toString())
            File(outFile).parent?.let { folders.promptOpenFolder(it) }
        } else {
            appendCommandResult(funcName, r)
        }
    }

    /**
     * java 可执行文件路径：优先取设置中手动指定的路径；
     * 未指定或指定路径无效时回退到当前运行 IDE 自带的 JVM（一定可用，避免签名链路整体失败）。
     */
    private fun javaBin(): String {
        val configured = AdbSettingsState.getInstance().javaPath.trim()
        if (configured.isNotEmpty() && File(configured).isFile) return configured
        val home = System.getProperty("java.home")
        return if (isWindows()) File(home, "bin/java.exe").absolutePath
        else File(home, "bin/java").absolutePath
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").contains("win", ignoreCase = true)
}
