plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.coolplayer.music"
    compileSdk = 34

    // TV 版：移除 native CMake 编译。EQ 的 biquad native 实现缺失时
    // ParametricEqAudioProcessor 会自动回退到纯 Kotlin 实现，行为与音质不变，
    // 但省去 NDK/CMake 依赖，命令行环境即可直接构建 APK。

    defaultConfig {
        applicationId = "com.coolplayer.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // 签名配置：与原 coolplayer 的 GitHub workflow 约定保持一致。
    // CI 通过 Secrets 解码出 release.keystore 并注入以下 4 个环境变量；
    // 本地/无环境变量时 signingConfig 为 null，release 仍可正常构建（未签名）。
    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 与原 coolplayer 一致：开启 R8 代码压缩 + 资源收缩以精简体积。
            // proguard-rules.pro 已保留 Media3 / Room / JAudioTagger 反射类 /
            // Kotlin 元数据 / Service 等入口，可安全开启。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 仅在 CI 提供了 keystore 时应用签名，避免本地构建因缺文件失败。
            if (!System.getenv("RELEASE_STORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    // Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Media3 ExoPlayer + MediaSession
    // 注：原 media3-exoplayer-hls 依赖已移除——本项目只播放本地音频文件，
    // 没有任何 HLS(m3u8) 网络流播放入口，该模块是纯粹的死重量（约 1.5 万方法数，
    // 影响 R8 后体积与启动时的类验证/编译开销）。
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Compose for TV：Surface 聚焦缩放/描边/光晕等 TV 标准焦点效果
    implementation("androidx.tv:tv-material:1.0.0")

    // Room (playlists / favorites / history)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JAudioTagger（Android 兼容 fork，来自 Kaned1as/jaudiotagger，JitPack 坐标沿用其曾用
    // GitHub 用户名 Adonai）：多格式（MP3/FLAC/OGG/M4A/WMA/APE/Opus 等）音频标签与内嵌
    // 封面读取。官方 net.jthink 版依赖 javax.imageio / java.awt 等桌面 JVM 专属类，在
    // Android 上无法运行，故使用这个移除了这些依赖、额外加强 Opus/MP4-DASH 支持的 fork。
    implementation("com.github.Adonai:jaudiotagger:2.3.14")

    // pinyin4j：中文按拼音首字母排序 / 右侧字母索引（爱→A，城→C）。
    // 原 TinyPinyin 只发布在已关闭的 JCenter，官方源已无法解析，改用 Maven Central 上的 pinyin4j。
    implementation("com.belerweb:pinyin4j:2.5.1")
}
