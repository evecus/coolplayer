# ── Media3 (ExoPlayer / MediaSession) ────────────────────────────────────
-keep class androidx.media3.** { *; }

# ── 本项目数据模型 / Room Entity ─────────────────────────────────────────
# 注意：原规则误写成了 com.salt.music.data.**（疑似从其他项目复制时忘记
# 同步包名），实际包名是 com.coolplayer.music.data，之前这条规则形同虚设，
# 数据模型类完全没有被 keep 保护。SongEntry / *Entity 等类通过反射
# （Room 生成代码、Gson/手写 JSON 序列化）读写字段，混淆改名或裁剪字段
# 会直接导致运行时崩溃或数据错乱，必须整体保留。
-keep class com.coolplayer.music.data.** { *; }
-keepclassmembers class com.coolplayer.music.data.** { *; }

# ── Room ──────────────────────────────────────────────────────────────
# Room 的 KSP 生成代码通常能被 R8 自动识别，但 Entity/Dao 接口本身依赖
# 注解 + 反射匹配，显式保留以防止意外裁剪。
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── JAudioTagger ──────────────────────────────────────────────────────
# 音频标签库内部大量使用反射解析不同容器格式（ID3v2/Vorbis Comment/MP4
# atom 等），字段名和类结构必须保持原样，否则解析会静默失败或抛异常。
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ── Coil ──────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Kotlin 协程 ───────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Kotlin 元数据（反射、data class 等依赖） ──────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class kotlin.Metadata { *; }

# ── AppWidget / RemoteViews 入口类 ───────────────────────────────────────
-keep class com.coolplayer.music.ui.widget.** { *; }

# ── MediaSession / 通知栏控制回调（系统通过反射/隐式 Intent 调起） ─────────
-keep class com.coolplayer.music.service.** { *; }
-keep class com.coolplayer.music.player.** { *; }
