// =====================================================================
// ADB 操作可视化工具 —— Android Studio 插件工程
// 将 C# WinForms 版（d:\ai_demo\adb_tools）移植为 IntelliJ 平台插件，
// 架构沿用 MVP（Model-View-Presenter），异步统一使用 RxJava。
// =====================================================================

import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    // AS 251 平台类为 Kotlin 2.1.0 编译（metadata 2.1.0），编译器必须 >= 2.1 才能读取
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
}

group = "com.asadb"
version = "1.0.70"

// intellij 插件会向项目注入 localPath 的 flatDir 仓库并覆盖 settings 中的仓库，
// 因此必须在此显式声明 Maven 仓库，否则第三方依赖（如 RxJava）无法解析。
repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/central")
    mavenCentral()
}

dependencies {
    // 异步编排统一使用 RxJava（对应 C# 版的 BackgroundWorker / async）
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
}

intellij {
    // CI（-PciBuild=true）无本机 IDE，改用 JetBrains 仓库中的 Android Studio 发行包；
    // 本机开发使用 localPath（真实安装根目录，含 build.txt / lib / plugins），无需下载大文件
    val ciBuild = providers.gradleProperty("ciBuild").isPresent
    if (ciBuild) {
        // 注意：Android Studio 在 1.x 插件中的产品代号是 AI（不存在 AS 这个取值），填错会在配置阶段直接报错
        version.set("251.25410.109.2511.13752376")
        type.set("AI")
    } else {
        // 本机 IDE 安装根目录属于机器私有配置，不写死在源码中，按以下顺序读取：
        // 1) Gradle 属性 -PlocalAsPath=<路径>；2) 环境变量 AS_LOCAL_PATH。
        // 建议写入用户级 ~/.gradle/gradle.properties（在仓库之外），避免私有路径进入公开仓库
        val localAsPath = providers.gradleProperty("localAsPath")
            .orElse(providers.environmentVariable("AS_LOCAL_PATH"))
            .orNull
            ?: throw GradleException(
                "未配置本机 Android Studio 安装根目录。请任选其一：" +
                    "① 用 -PlocalAsPath=<安装根目录> 运行（如 -PlocalAsPath=/path/to/AndroidStudio）；" +
                    "② 设置环境变量 AS_LOCAL_PATH；" +
                    "③ 在用户级 ~/.gradle/gradle.properties 中写入 localAsPath=<安装根目录>；" +
                    "④ 改用 -PciBuild，从 JetBrains 仓库下载 Android Studio 发行包（需联网）。"
            )
        val localAsRoot = file(localAsPath)
        // build.txt 是 IDE 安装根目录的标志文件：缺失说明路径填错（常见错误是误填到 bin 目录）
        if (!File(localAsRoot, "build.txt").isFile) {
            throw GradleException(
                "localPath 不是有效的 Android Studio 安装根目录（未找到 build.txt）：${localAsRoot.absolutePath}。" +
                    "请填写 IDE 安装根目录（该目录下应直接包含 build.txt、lib、plugins），而不是 bin 子目录。"
            )
        }
        localPath.set(localAsRoot.absolutePath)
    }
    pluginName.set("AsAdbTools")
    downloadSources.set(false)
    instrumentCode.set(true)
    // 编译期依赖 AS 内置 Terminal 插件类：由 JetBrains 仓库按平台版本解析，避免绑定本机 jar 路径
    plugins.set(listOf("terminal"))
}

kotlin {
    compilerOptions {
        // 与 IntelliJ Platform 运行时要求保持一致，目标字节码 17
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    patchPluginXml {
        // 兼容范围：Android Studio 2024.1（241）~ 2025.1（251）
        sinceBuild.set("241.0")
        untilBuild.set("251.*")
        // 插件介绍（Overview 展示内容）统一维护在 src/main/resources/META-INF/plugin.xml 的 <description> 中，
        // 此处不再设置 pluginDescription，避免构建时覆盖 plugin.xml 里的详细介绍
    }

    // 分发包内附更新日志：项目约定安装包必须包含 CHANGELOG.md（prepareSandbox 暂存插件目录，buildPlugin 据此打 zip）
    prepareSandbox {
        from("CHANGELOG.md") {
            into(intellij.pluginName.get())
        }
    }

    // 平台为 AS 251，无头启动 IDE 索引设置项的任务无法执行；仅影响插件市场搜索索引，本地插件无需
    buildSearchableOptions {
        enabled = false
    }

    // 发布到 JetBrains Marketplace：token 来自环境变量 JETBRAINS_TOKEN（CI 中通过 Secrets 注入），
    // 未提供时不设置 token，publishPlugin 执行时会因缺少认证失败，避免误发布
    publishPlugin {
        System.getenv("JETBRAINS_TOKEN")?.let { token.set(it) }
        channels.set(listOf("stable"))
    }

    // 插件本地签名：优先使用环境变量中的 JetBrains 证书（certificateChain / privateKey / password），
    // 未提供时跳过签名（Marketplace 发布会自动签名）
    signPlugin {
        System.getenv("CERTIFICATE_CHAIN")?.let { certificateChain.set(it) }
        System.getenv("PRIVATE_KEY")?.let { privateKey.set(it) }
        System.getenv("PRIVATE_KEY_PASSWORD")?.let { password.set(it) }
    }

    // Plugin Verifier 兼容性检查：默认以 intellij 目标平台为检查对象，
    // 本机（localPath）与 CI（-PciBuild）均可运行
    verifyPlugin {
        // verifier 解析第三方插件依赖失败时可在此追加参数，如 -mute 指定问题 ID
    }
}
