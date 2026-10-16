// =====================================================================
// ADB 命令执行服务（Model 层）
// 对应 C# 版 AdbTool.Model.AdbService。
// 负责定位 adb 可执行文件、执行命令、截图等底层操作，不依赖任何 UI。
// =====================================================================

package com.asadb.tools.model

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * ADB 命令执行服务：实现 [IAdbService]，封装与 adb 进程的所有交互。
 */
class AdbService : IAdbService {

    /** 解析后的 adb 可执行文件路径。 */
    private var resolvedAdbPath = "adb"

    /** 录屏进行中的 adb 进程句柄（跨线程读写，volatile 保证可见性）。 */
    @Volatile
    private var recordProcess: Process? = null

    /** 日志抓取进行中的 adb 进程句柄（跨线程读写，volatile 保证可见性）。 */
    @Volatile
    private var logcatProcess: Process? = null

    /** 当前正在执行的一次性 adb 命令进程（供「结束当前操作」终止）。 */
    private var currentProcess: Process? = null

    /** currentProcess 访问锁。 */
    private val processLock = Any()

    /** 当前目标设备 serial（多设备连接时用于 -s 指定）。 */
    private var targetSerial: String? = null

    override val adbPath: String get() = resolvedAdbPath

    override val isRecording: Boolean
        get() = try { recordProcess != null && recordProcess!!.isAlive } catch (e: Exception) { recordProcess != null }

    override val isLogging: Boolean
        get() = try { logcatProcess != null && logcatProcess!!.isAlive } catch (e: Exception) { logcatProcess != null }

    /**
     * 在常见位置中定位 adb 可执行文件；找到返回 true。
     * 找不到时返回 false（由上层提示用户自行设置），并回退为 "adb"。
     */
    override fun findAdb(): Boolean {
        // 优先使用用户在设置中持久化的 adb 路径，避免每次启动重复扫描
        val settingsPath = AdbSettingsState.getInstance().adbPath
        if (settingsPath.isNotBlank() && testAdb(settingsPath)) {
            resolvedAdbPath = settingsPath
            return true
        }

        val candidates = mutableListOf("adb")
        // 环境变量（ANDROID_HOME / ANDROID_SDK_ROOT）
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { env ->
            val home = System.getenv(env)
            if (!home.isNullOrBlank()) {
                candidates.add(joinPath(home, "platform-tools", adbExecutableName()))
            }
        }
        // Android Studio 平台库探测：通过反射调用 AndroidSdkUtils，避免强依赖 android 插件
        detectSdkPath()?.let {
            candidates.add(joinPath(it, "platform-tools", adbExecutableName()))
        }
        // 各操作系统常见 SDK 安装位置（macOS / Linux / Windows）
        val userHome = System.getProperty("user.home")
        candidates.add(joinPath(userHome, "Library", "Android", "sdk", "platform-tools", adbExecutableName()))
        candidates.add(joinPath(userHome, "Android", "Sdk", "platform-tools", adbExecutableName()))
        val localAppData = System.getenv("LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            candidates.add(joinPath(localAppData, "Android", "Sdk", "platform-tools", adbExecutableName()))
        }

        for (c in candidates) {
            if (testAdb(c)) {
                resolvedAdbPath = c
                AdbSettingsState.getInstance().adbPath = c
                return true
            }
        }
        resolvedAdbPath = "adb"
        return false
    }

    /** 手动设置 adb 路径：测试通过则更新并返回 true，否则保持原路径返回 false。 */
    override fun trySetAdbPath(path: String): Boolean {
        if (path.isBlank() || !testAdb(path)) return false
        resolvedAdbPath = path
        AdbSettingsState.getInstance().adbPath = path
        return true
    }

    /** 测试指定路径的 adb 是否可用。 */
    private fun testAdb(path: String): Boolean {
        return try {
            val pb = ProcessBuilder(listOf(path, "version"))
            pb.redirectErrorStream(true)
            val p = pb.start()
            drainOutput(p)
            val done = p.waitFor(3, TimeUnit.SECONDS)
            // 超时未退出时强制销毁，避免残留探测进程
            if (!done) p.destroy()
            done && p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** 同步执行 adb 命令并返回结果。应在后台线程调用，采用异步读取避免死锁。 */
    override fun runAdb(args: String): AdbResult {
        // 服务器级命令不指定目标设备；其中会改变设备列表的命令需清空 serial 缓存
        if (isServerCommand(args)) {
            val r = runAdbRaw(args)
            if (changesDeviceList(args)) targetSerial = null
            return r
        }

        val serial = getTargetSerial()
        if (serial.isNullOrBlank()) return runAdbRaw(args)
        return runAdbRaw("-s $serial $args")
    }

    /** 直接执行 adb 命令（不注入 -s），供 runAdb 与设备列表解析复用。 */
    private fun runAdbRaw(args: String): AdbResult {
        val command = mutableListOf(resolvedAdbPath)
        command.addAll(splitCommandLine(args))
        return runRawProcess(command, "adb")
    }

    /**
     * 直接运行一条外部进程命令并返回结果：异步读取输出流防止死锁，
     * 等待超时后强制销毁进程，统一管理当前进程句柄，防止后台残留。
     */
    override fun runRawProcess(command: List<String>, toolName: String): AdbResult {
        val result = AdbResult()
        var process: Process? = null
        try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(false)
            process = pb.start()
            synchronized(processLock) { currentProcess = process }

            val outSb = StringBuilder()
            val errSb = StringBuilder()
            val outThread = Thread { readAll(process!!.inputStream, outSb) }
            val errThread = Thread { readAll(process!!.errorStream, errSb) }
            outThread.isDaemon = true
            errThread.isDaemon = true
            outThread.start()
            errThread.start()
            // 等待完成；超时则强制销毁，避免进程挂死造成后台残留
            process.waitFor(300, TimeUnit.SECONDS)
            if (process.isAlive) process.destroy()
            outThread.join(1000)
            errThread.join(1000)

            result.exitCode = process.exitValue()
            result.output = outSb.toString()
            result.error = errSb.toString()
        } catch (e: Exception) {
            result.exitCode = -1
            result.error = "$toolName 执行异常：${e.message}"
        } finally {
            synchronized(processLock) { if (currentProcess === process) currentProcess = null }
        }
        return result
    }

    /** 判断是否为 adb 服务器级命令（无需指定目标设备 serial）。 */
    private fun isServerCommand(args: String): Boolean {
        return firstToken(args) in setOf("version", "devices", "connect", "disconnect", "start-server", "kill-server", "help")
    }

    /** 判断命令是否会改变设备列表（执行后需失效 serial 缓存）。 */
    private fun changesDeviceList(args: String): Boolean {
        return firstToken(args) in setOf("connect", "disconnect", "start-server", "kill-server")
    }

    /** 取命令的首个 token（命令名）。 */
    private fun firstToken(args: String): String {
        val a = (args ?: "").trimStart()
        val i = a.indexOf(' ')
        return if (i < 0) a else a.substring(0, i)
    }

    /**
     * 解析并缓存当前目标设备 serial：从 adb devices 输出中取第一台状态为 device 的设备，
     * 忽略 offline / unauthorized 等不可用设备。无可用设备时返回 null。
     */
    private fun getTargetSerial(): String? {
        targetSerial?.let { return it }
        return try {
            val r = runAdbRaw("devices")
            val text = "${r.output}\n${r.error}"
            for (raw in text.split('\r', '\n')) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("List of devices")) continue
                val parts = line.split('\t', ' ').filter { it.isNotEmpty() }
                if (parts.size >= 2 && parts[1] == "device") {
                    targetSerial = parts[0]
                    break
                }
            }
            targetSerial
        } catch (e: Exception) {
            targetSerial
        }
    }

    /** 设备截图：先 adb shell screencap 在设备上生成 png，再 pull 到本地，最后清理设备端临时文件。 */
    override fun runScreenshot(savePath: String): String {
        val remotePath = "/sdcard/screenshot.png"

        // 1) 在设备端截图
        val cap = runAdb("shell screencap -p $remotePath")
        if (cap.exitCode != 0) {
            val err = if (cap.error.isBlank()) cap.output.trim() else cap.error.trim()
            return "截图失败：$err"
        }

        // 2) 拉取到本地（先拉到临时目录再复制，规避 adb 不能直接写磁盘根目录的限制）
        val pullErr = pullDeviceFile(remotePath, savePath)

        // 3) 清理设备端临时文件
        runAdb("shell rm -f $remotePath")

        return if (pullErr == null) "已保存到 $savePath" else "拉取失败：$pullErr"
    }

    /** 拉取设备文件并保存到本地 savePath，返回 null 表示成功，否则返回错误描述。 */
    private fun pullDeviceFile(remotePath: String, savePath: String): String? {
        val tempPath = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "adbtool_${java.util.UUID.randomUUID().toString().replace("-", "")}${File(savePath).extension.let { if (it.isEmpty()) "" else ".$it" }}"
        ).toString()

        val pull = runAdb("pull \"$remotePath\" \"$tempPath\"")
        if (pull.exitCode != 0) {
            runCatching { Files.deleteIfExists(Paths.get(tempPath)) }
            return pull.error.trim()
        }

        return try {
            ensureTargetDirectory(savePath)
            Files.copy(Paths.get(tempPath), Paths.get(savePath), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            null
        } catch (e: Exception) {
            e.message
        } finally {
            runCatching { Files.deleteIfExists(Paths.get(tempPath)) }
        }
    }

    /** 确保 savePath 所在目录存在（根目录已存在时跳过）。 */
    private fun ensureTargetDirectory(savePath: String) {
        val dir = File(savePath).parentFile
        if (dir != null) dir.mkdirs()
    }

    /** 开始录屏：在设备端启动 screenrecord（不设时长上限），并保持进程句柄。 */
    override fun startScreenRecord(): Boolean {
        return try {
            // 清理可能残留的旧文件
            runAdb("shell rm -f /sdcard/screenrecord.mp4")

            val sizeArg = getScreenSizeArg()
            val serial = getTargetSerial()
            val serialPrefix = if (serial.isNullOrBlank()) "" else "-s $serial "

            val args = StringBuilder()
                .append(serialPrefix).append("shell screenrecord")
                .append(if (sizeArg.isNotEmpty()) " $sizeArg" else "")
                .append(" /sdcard/screenrecord.mp4")
                .toString()

            val command = mutableListOf(resolvedAdbPath)
            command.addAll(splitCommandLine(args))
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            val p = pb.start()
            // 异步读取并丢弃输出，避免缓冲区积压
            Thread { drainOutput(p) }.start()
            recordProcess = p
            true
        } catch (e: Exception) {
            recordProcess = null
            false
        }
    }

    /** 结束录屏：向屏幕录制进程发送 SIGINT 使其正常收尾，然后 pull 到本地并清理。 */
    override fun stopScreenRecord(savePath: String): String {
        val remotePath = "/sdcard/screenrecord.mp4"
        return try {
            // 1) 向设备端 screenrecord 发送 SIGINT，使其正常收尾生成 MP4
            runAdb("shell pkill -2 screenrecord")

            // 2) 等待录屏客户端进程退出
            recordProcess?.let { p ->
                if (!p.waitFor(5, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    p.waitFor()
                }
                p.destroy()
            }
            recordProcess = null

            // 3) 拉取到本地
            val pullErr = pullDeviceFile(remotePath, savePath)

            // 4) 清理设备端临时文件
            runAdb("shell rm -f $remotePath")

            if (pullErr == null) "已保存到 $savePath" else "拉取失败：$pullErr"
        } catch (e: Exception) {
            recordProcess?.destroy()
            recordProcess = null
            "结束录屏异常：${e.message}"
        }
    }

    /** 开始抓取日志：启动 adb logcat 并持续回调每一行输出。 */
    override fun startLogcat(args: String, onLine: (String) -> Unit): Boolean {
        return try {
            val serial = getTargetSerial()
            val serialPrefix = if (serial.isNullOrBlank()) "" else "-s $serial "

            val command = mutableListOf(resolvedAdbPath)
            command.addAll(splitCommandLine("$serialPrefix logcat $args"))
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val reader = Thread {
                p.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                    r.forEachLine { line -> runCatching { onLine(line) } }
                }
            }
            reader.isDaemon = true
            reader.start()
            logcatProcess = p
            true
        } catch (e: Exception) {
            logcatProcess = null
            false
        }
    }

    /** 停止抓取日志：终止 logcat 进程。 */
    override fun stopLogcat() {
        try {
            logcatProcess?.let { p ->
                if (p.isAlive) {
                    p.destroyForcibly()
                    p.waitFor(2, TimeUnit.SECONDS)
                }
                p.destroy()
            }
        } catch (e: Exception) {
            // 忽略终止异常
        }
        logcatProcess = null
    }

    /** 启动一个外部进程并立即返回（进程分离，不等待退出、不收集输出），用于 scrcpy 等长驻 GUI 工具。 */
    override fun launchDetached(command: List<String>, toolName: String): String {
        return try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            val p = pb.start()
            // 异步读取并丢弃输出，避免长驻工具的持续输出积压阻塞进程
            Thread { drainOutput(p) }.start()
            "已启动 $toolName"
        } catch (e: Exception) {
            "$toolName 启动失败：${e.message}"
        }
    }

    /** 强制结束当前正在执行的一次性 adb 命令（即最近一次 runAdb 启动的进程）。 */
    override fun stopCurrentOperation() {
        val p: Process?
        synchronized(processLock) { p = currentProcess }
        if (p == null) return

        try {
            if (p.isAlive) {
                p.destroyForcibly()
                p.waitFor(2, TimeUnit.SECONDS)
            }
        } catch (e: Exception) {
            // 忽略终止异常
        }

        synchronized(processLock) { if (currentProcess === p) currentProcess = null }
    }

    /** 扫描本机同网段的 adb 网络设备并尝试连接，返回成功连接的设备 IP 列表。 */
    override fun scanLanDevices(): List<String> {
        val found = mutableListOf<String>()
        for (host in scanAdbPortCandidates()) {
            val r = runAdb("connect $host:5555")
            val all = "${r.output}\n${r.error}"
            if (r.exitCode == 0 || all.contains("connected", ignoreCase = true)) {
                found.add(host)
            }
        }
        return found
    }

    /** 并发探测本机各网段的 5555 端口，返回端口开放的主机 IP 列表。 */
    private fun scanAdbPortCandidates(): List<String> {
        val localIps = getLocalIpv4()
        val segments = mutableSetOf<String>()
        for (ip in localIps) {
            val lastDot = ip.lastIndexOf('.')
            if (lastDot > 0) segments.add(ip.substring(0, lastDot))
        }

        val hosts = mutableListOf<String>()
        for (seg in segments) {
            for (i in 1..254) {
                val host = "$seg.$i"
                if (host !in localIps) hosts.add(host)
            }
        }

        val openHosts = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val pool: ExecutorService = Executors.newFixedThreadPool(
            minOf(64, maxOf(8, hosts.size))
        )
        try {
            hosts.forEach { host ->
                pool.submit {
                    if (tcpPortOpen(host, 5555, 200)) openHosts.add(host)
                }
            }
            pool.shutdown()
            pool.awaitTermination(60, TimeUnit.SECONDS)
        } catch (e: Exception) {
            pool.shutdownNow()
        }

        return openHosts.sorted()
    }

    /** 获取本机所有可用的 IPv4 地址（排除回环与自动配置地址）。 */
    private fun getLocalIpv4(): List<String> {
        val ips = mutableListOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (!ni.isUp) return@forEach
                ni.inetAddresses.toList().forEach { addr ->
                    if (addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress
                        if (!ip.startsWith("127.") && !ip.startsWith("169.254.") && ip != "0.0.0.0") {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略网络接口枚举异常
        }
        return ips
    }

    /** 以异步方式探测主机端口是否开放，超时返回 false。 */
    private fun tcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getByName(host), port), timeoutMs)
                s.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 通过 adb shell wm size 读取设备的物理分辨率，
     * 返回 screenrecord 的 --size 参数（宽高向下对齐到 16 的倍数）。
     * AVC 编码器强制要求宽高均为 16 的倍数，否则 screenrecord 会自动
     * 向下修正分辨率，导致画面边缘被裁切；提前对齐可避免此问题。
     */
    private fun getScreenSizeArg(): String {
        return try {
            val r = runAdb("shell wm size")
            val text = "${r.output}\n${r.error}"
            val m = Pattern.compile("(\\d{3,5})\\s*x\\s*(\\d{3,5})").matcher(text)
            if (m.find()) {
                var w = m.group(1).toIntOrNull()
                var h = m.group(2).toIntOrNull()
                if (w != null && h != null && w > 0 && h > 0) {
                    w = w and 15.inv()   // 向下对齐到 16 的倍数
                    h = h and 15.inv()
                    if (w > 0 && h > 0) return "--size ${w}x$h"
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    // ==================== 内部工具 ====================

    /** 将命令行字符串按空格拆分，双引号包裹的内容视为一个整体参数。 */
    private fun splitCommandLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> inQuote = !inQuote
                c == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    /** 读取输入流的全部内容追加到 StringBuilder。 */
    private fun readAll(input: java.io.InputStream, sb: StringBuilder) {
        try {
            val bytes = input.readBytes()
            sb.append(decodeProcessText(bytes))
        } catch (e: Exception) {
            // 忽略读取异常
        }
    }

    /**
     * 解码外部进程输出文本：优先严格按 UTF-8 解码（adb、apksigner 等输出 UTF-8），
     * 解码失败则回退 GBK（中文 Windows 下 jarsigner 等 JDK 工具按控制台代码页输出 GBK）。
     * 注意：AS 的 JBR 强制 file.encoding=UTF-8，Charset.defaultCharset() 恒为 UTF-8，
     * 因此不能依赖 defaultCharset 兜底，Windows 上必须显式尝试 GBK。
     */
    private fun decodeProcessText(bytes: ByteArray): String {
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            // 非 UTF-8，进入回退
        }
        // Windows：显式按 GBK 解码（常见于 jarsigner 等 JDK 工具的中文输出）
        if (System.getProperty("os.name").lowercase().contains("win")) {
            runCatching { return String(bytes, Charset.forName("GBK")) }
        }
        // 其余平台回退系统默认编码
        return String(bytes, Charset.defaultCharset())
    }

    /** 读取进程输出直到结束（用于丢弃输出 / 等待进程完成）。 */
    private fun drainOutput(p: Process) {
        try {
            p.inputStream.bufferedReader(Charsets.UTF_8).use { r -> r.forEachLine { } }
        } catch (e: Exception) {
            // 忽略读取异常
        }
    }

    /** 拼接路径片段。 */
    private fun joinPath(vararg parts: String): String =
        parts.joinToString(separator = File.separator)

    /** 当前平台的 adb 可执行文件名（Windows 为 adb.exe，其余为 adb）。 */
    private fun adbExecutableName(): String =
        if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"

    /**
     * 多路探测 Android Studio 本地 SDK 路径：
     * 1. 反射 AndroidSdkUtils（android 插件类，optional 依赖解析成功才可见）；
     * 2. 反射 LocalSdk（部分新版本 AS 的路径来源）；
     * 3. 读取平台 SDK 表（ProjectJdkTable）中名为 "Android SDK" 的 SDK 主目录（不依赖 android 插件类，最可靠）。
     * 仅在 Android Studio 中可用；其余环境返回 null（不产生强依赖）。
     */
    override fun detectSdkPath(): String? {
        // 方式一/二：反射 android 插件内部类
        val reflectCandidates = listOf(
            "com.android.tools.idea.sdk.AndroidSdkUtils" to "getLocalSdkPath",
            "com.android.tools.idea.sdk.LocalSdk" to "getPath"
        )
        for ((clsName, methodName) in reflectCandidates) {
            try {
                val clazz = Class.forName(clsName)
                val method = clazz.getMethod(methodName)
                val path = method.invoke(null) as? java.io.File
                if (path != null && path.isDirectory) return path.absolutePath
            } catch (e: Exception) {
                // 该类或方法在当前 AS 版本不可用，尝试下一路
            }
        }
        // 方式三：平台 SDK 表（ProjectJdkTable），查找 Android SDK 类型
        return try {
            ReadAction.compute<String?, RuntimeException> {
                ProjectJdkTable.getInstance().allJdks
                    .firstOrNull { it.sdkType.name.contains("Android", ignoreCase = true) }
                    ?.homePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
