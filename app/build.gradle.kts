plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.coolplayer.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.coolplayer.music"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 之前是 false：release 包不做任何代码压缩/混淆，体积偏大，
            // 且包含了所有依赖库里未被实际用到的代码。proguard-rules.pro
            // 已经补全了 Media3 / Room / JAudioTagger（大量反射解析音频
            // 标签，容易被误裁剪）/ Coroutines / Kotlin Metadata / Widget
            // 与 Service 相关类的保留规则，并修正了其中一条写错包名、
            // 完全没生效的规则（com.salt.music.data.** -> 实际包名
            // com.coolplayer.music.data），现在可以安全开启。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Media3 ExoPlayer + MediaSession
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Palette (cover color extraction)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Room (playlists / favorites / history)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Window for tablet/phone layout detection
    implementation("androidx.window:window:1.3.0")

    // System UI controller
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    // Document file for scoped storage
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 说明：曾经引入过 Glide 用于 widget 封面加载，但实际全项目没有任何
    // 代码引用它（widget 封面改用了 BitmapDecodeUtil 手写降采样解码，
    // Compose 内的封面统一走 Coil），是完全未使用的死重量依赖，已移除。

    // JAudioTagger（Android 兼容 fork，来自 Kaned1as/jaudiotagger，JitPack 坐标沿用其曾用
    // GitHub 用户名 Adonai）：多格式（MP3/FLAC/OGG/M4A/WMA/APE/Opus 等）音频标签与内嵌
    // 封面读取。官方 net.jthink 版依赖 javax.imageio / java.awt 等桌面 JVM 专属类，在
    // Android 上无法运行，故使用这个移除了这些依赖、额外加强 Opus/MP4-DASH 支持的 fork。
    implementation("com.github.Adonai:jaudiotagger:2.3.14")
}
