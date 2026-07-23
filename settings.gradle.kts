pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // JAudioTagger 官方发布版（net.jthink）依赖 javax.imageio / java.awt 等桌面 JVM 专属类，
        // 在 Android 上无法使用；这里改用社区维护的 Android 兼容 fork（通过 JitPack 分发）。
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CoolPlayer"
include(":app")
