// =====================================================================
// Android Studio 插件工程 —— Gradle 设置
// 插件基于 IntelliJ Platform 构建，使用本地安装的 Android Studio 作为平台。
// =====================================================================

pluginManagement {
    repositories {
        maven("https://plugins.gradle.org/m2/")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
    }
}

rootProject.name = "as-adb-tools"
