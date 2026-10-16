# ADB Toolkit（Android Studio 插件）

将日常开发中高频使用的 adb 命令行操作封装成可视化按钮，在 Android Studio 右侧「ADB 工具」窗口中一键执行，命令输出直接展示在内置 Terminal 工具窗口，无需记忆和手敲 adb 命令。

| 项 | 值 |
| --- | --- |
| 插件 ID | `com.asadb.tools` |
| 兼容范围 | Android Studio 2024.1（241）~ 2025.1（251） |
| 当前版本 | 1.0.70（详见 [CHANGELOG.md](CHANGELOG.md)） |
| 许可证 | [Apache License 2.0](LICENSE) |

## 一行构建 / 启动

```bash
./gradlew buildPlugin && ./gradlew runIde
```

Windows 环境使用 `gradlew.bat`（如 `gradlew.bat buildPlugin`）。

## 环境要求

- JDK 17（构建与运行目标字节码均为 17）
- Gradle 8.9（随 `gradlew` 提供，无需本机安装）
- Android Studio 2024.1 ~ 2025.1（本机开发模式下作为 IntelliJ Platform 使用）

## 构建与运行

| 场景 | 命令 |
| --- | --- |
| 本地构建插件包 | `./gradlew buildPlugin`（需先配置 `localAsPath`，见下方说明） |
| 本地构建（改用 JetBrains 仓库远程 IDE） | `./gradlew buildPlugin -PciBuild` |
| 启动沙箱 IDE 调试插件 | `./gradlew runIde` |
| 插件兼容性校验（Plugin Verifier） | `./gradlew verifyPlugin` |
| 清理构建输出 | `./gradlew clean` |

说明：本机开发模式下 [build.gradle.kts](build.gradle.kts) 的 `intellij.localPath` **不在源码中硬编码**，需自行提供本机 Android Studio 安装根目录（该目录下应直接包含 `build.txt`、`lib/`、`plugins/`，注意不要填成 `bin` 子目录）。三种提供方式任选其一：

1. 命令行属性：`./gradlew buildPlugin -PlocalAsPath=/path/to/AndroidStudio`
2. 环境变量：`AS_LOCAL_PATH`
3. 用户级 `~/.gradle/gradle.properties` 写入 `localAsPath=<安装根目录>`（推荐，配置在仓库之外，不会随代码公开）

未提供该配置、或路径下缺少 `build.txt` 时，构建会立即失败并给出明确提示；也可改用 `-PciBuild`（联网从 JetBrains 仓库下载 Android Studio 发行包）。

## 构建产物

- **产物路径**：`build/distributions/AsAdbTools-<version>.zip`
- **产物内容**：可直接安装的插件分发包（含 `CHANGELOG.md`）
- **本地安装方式**：Android Studio → `Settings` → `Plugins` → 右上角齿轮 → `Install Plugin from Disk…` → 选择该 zip → 重启 IDE
- **CI 产物**：每次 push / PR 由 [.github/workflows/ci.yml](.github/workflows/ci.yml) 构建，并上传 `build/distributions/*.zip` 为 GitHub Actions artifact（命名 `AsAdbTools`，保留 14 天）
- **发布**：手动触发 workflow（`workflow_dispatch`）执行 `publishPlugin`，使用仓库 Secrets 中的 `JETBRAINS_TOKEN` 发布到 JetBrains Marketplace

## 插件设置

设置入口：ADB 工具窗口 → 设置（弹窗左侧导航分页）。

| 页 | 可配置项 |
| --- | --- |
| 页面修改 | 信息展示区字号、字体、字体颜色、背景色 |
| 缓存配置 | adb 路径、缓存目录、媒体目录、待安装 APK 目录、待签名 APK 目录 |
| 签名配置 | APK 签名所用的密钥库文件（`.keystore` / `.jks`）路径 |
| 工具配置 | scrcpy、apksigner、java 三个外部工具的可执行文件路径 |
| 关于 | 插件版本（运行时读取，与安装版本一致） |

### 外部工具路径（「工具配置」页）

三个路径**均可留空**，留空时按原有逻辑自动探测；三项都是「设置项优先 → 自动探测兜底」：

| 工具 | 留空（自动探测） | 指定路径后 |
| --- | --- | --- |
| scrcpy | 按 PATH 与各平台常见安装目录查找（屏幕投影使用） | 优先使用指定路径；指定但文件不存在时明确报错，不静默回退 |
| apksigner | adb 反推 SDK → PATH → 环境变量 → 常见 SDK 目录 → 取版本号最高的 build-tools（签名与签名校验使用） | 优先使用指定路径 |
| java | 当前运行 IDE 自带的 JVM（签名 `.jar` 时使用） | 优先使用指定路径；指定但文件无效时回退 IDE 自带 JVM |

工具装在非默认位置时（如 scrcpy 解压在自定义目录、JDK 未加入 PATH），直接在「工具配置」页指定即可，无需修改 PATH。

## 环境变量清单

### 签名凭据（使用「应用签名」功能时必需）

出于安全考虑，签名别名与密码**不内置默认值**，必须通过环境变量提供，且需在**启动 Android Studio 之前**设置（IDE 进程需要能读到）：

| 变量名 | 用途 |
| --- | --- |
| `ADB_TOOLS_SIGN_ALIAS` | 签名密钥别名 |
| `ADB_TOOLS_SIGN_STORE_PASS` | 签名密钥库密码 |
| `ADB_TOOLS_SIGN_KEY_PASS` | 签名私钥密码 |

未设置时执行签名会直接报错并提示缺失的变量名。密钥库文件路径请在「设置 → 签名配置」中选择（不再内置默认路径），未配置时签名同样会直接报错并提示。

### adb 定位（可选）

插件按以下顺序定位 adb：设置中保存的 adb 路径 → `ANDROID_HOME` / `ANDROID_SDK_ROOT` → 本机 Android Studio SDK 探测 → 各系统常见 SDK 目录。

| 变量名 | 用途 |
| --- | --- |
| `ANDROID_HOME` | Android SDK 根目录 |
| `ANDROID_SDK_ROOT` | Android SDK 根目录（兼容旧变量） |

### 本机构建（开发）

| 变量名 | 用途 |
| --- | --- |
| `AS_LOCAL_PATH` | 本机 Android Studio 安装根目录（含 `build.txt`），等价于 `-PlocalAsPath`；未提供时本机构建直接报错 |

### CI / 发布（GitHub Actions 使用）

| 变量名 | 用途 |
| --- | --- |
| `JETBRAINS_TOKEN` | 发布到 JetBrains Marketplace 的访问令牌（配置在仓库 Secrets） |
| `CERTIFICATE_CHAIN` | 插件本地签名证书链（未提供则跳过本地签名，Marketplace 发布会自动签名） |
| `PRIVATE_KEY` | 插件本地签名私钥 |
| `PRIVATE_KEY_PASSWORD` | 插件本地签名私钥密码 |

## 主要功能

- **连接管理**：启动 adb、连接 / 断开局域网 IP 设备、再次连接（自动预填上次 IP）、查看连接状态、扫描局域网设备、重启 adb、结束当前 adb 操作
- **设备管理**：设备权限操作（Root / Remount）；设备信息查询（前台 Activity 栈、前台进程、前台 Activity、设备信息、电池、CPU、内存、存储）；设备操作（重启设备、开启 WiFi 调试、端口转发）
- **应用管理**：安装 APK、卸载应用、按包名或 Intent 启动、结束进程、清除缓存、提取 APK、停用 / 启用应用
- **应用签名**：一键 APK 签名、签名校验、签名校验 MD5 一条龙、生成 APK MD5（凭据见上方环境变量）
- **文件操作**：向设备推送文件、设备 hosts 文件管理、设置 / 清除设备全局代理
- **屏幕操作**：截图、录屏、获取屏幕分辨率、模拟点击 / 滑动 / 输入 / 按键、屏幕投影（scrcpy）
- **日志操作**：logcat 日志抓取（支持两种抓取格式与 TAG 过滤），可与录屏同步开始 / 结束
- **我的命令**：自定义 adb 命令快捷按钮，可新增 / 删除

## 项目结构

```
src/main/kotlin/com/asadb/tools/
├── model/       # 数据与基础设施层（adb 服务、设置持久化等）
├── presenter/   # 业务逻辑层（MVP Presenter，异步统一使用 RxJava3）
├── view/        # 视图层（Swing 主面板、设置弹窗、工具窗口等）
├── AdbToolWindowFactory.kt   # 工具窗口注册
├── AdbProjectListener.kt     # 项目级生命周期监听
└── OpenAdbToolsAction.kt     # 打开工具窗口的 Action
src/main/resources/META-INF/plugin.xml   # 插件描述符（ID、名称、介绍、更新日志）
```

架构采用 MVP（Model-View-Presenter），各层通过窄接口（[IViews.kt](src/main/kotlin/com/asadb/tools/view/IViews.kt)）解耦；视图为手写绝对定位的 Swing 组件，自动跟随 IDE 深浅色主题。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源，Copyright © 2026 xiangshiweiyu。
