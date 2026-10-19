package com.coolplayer.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.coolplayer.music.data.StorageService

/**
 * 主题状态管理（单例）：
 *
 * - [themeMode]：0 跟随系统 / 1 浅色 / 2 深色
 * - [dynamicColor]：Android 12+ 是否使用 Material You 壁纸取色
 * - [useCoverColor]：是否从当前播放歌曲的封面提取颜色覆盖播放页主题
 * - [homeSeed]：首页主题色（持久化）
 * - [playerSeed]：播放页主题色（持久化，独立于首页 —— Salt 风格「分控主题」）
 *
 * Compose 通过 [AppTheme] 读取 [ThemeState] 各字段动态切换 colorScheme。
 */
object ThemeState {

    var themeMode: Int
        get() = StorageService.getInt(StorageService.kThemeMode, 0)
        set(value) = StorageService.setInt(StorageService.kThemeMode, value).let {}

    var dynamicColor: Boolean
        get() = StorageService.getBoolean(StorageService.kDynamicColor, true)
        set(value) = StorageService.setBoolean(StorageService.kDynamicColor, value).let {}

    var useCoverColor: Boolean
        get() = StorageService.getBoolean(StorageService.kCoverColor, true)
        set(value) = StorageService.setBoolean(StorageService.kCoverColor, value).let {}

    val homeSeedDefault: Color = Color(0xFF3D5AFE)
    val playerSeedDefault: Color = Color(0xFFE91E63)

    var homeSeed: Color
        get() = Color(StorageService.getInt(StorageService.kHomeSeedColor, homeSeedDefault.value.toInt()))
        set(value) = StorageService.setInt(StorageService.kHomeSeedColor, value.value.toInt()).let {}

    var playerSeed: Color
        get() = Color(StorageService.getInt(StorageService.kPlayerSeedColor, playerSeedDefault.value.toInt()))
        set(value) = StorageService.setInt(StorageService.kPlayerSeedColor, value.value.toInt()).let {}

    val supportsDynamicColor: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 从封面位图提取主题色（Palette）。
 * 找不到合适色时回退到 [fallback]。
 */
fun extractSeedFromBitmap(bitmap: Bitmap?, fallback: Color): Color {
    if (bitmap == null) return fallback
    return runCatching {
        val palette = Palette.from(bitmap).generate()
        val dominant = palette.dominantSwatch?.rgb
        val vibrant = palette.vibrantSwatch?.rgb
        val muted = palette.mutedSwatch?.rgb
        val argb = vibrant ?: dominant ?: muted
        if (argb != null) Color(argb) else fallback
    }.getOrDefault(fallback)
}

/**
 * 从字节数组解码封面后提取主题色。
 *
 * 用 [BitmapDecodeUtil.decodeSampled] 降采样到 100px 量级：Palette 取色
 * 只需要色块分布，不需要原图分辨率，避免为了算个主题色就完整解码一张
 * 可能几百万像素的封面图。
 */
fun extractSeedFromBytes(bytes: ByteArray?, fallback: Color): Color {
    if (bytes == null) return fallback
    val bmp = com.coolplayer.music.data.BitmapDecodeUtil.decodeSampled(bytes, maxDimenPx = 100)
        ?: return fallback
    return extractSeedFromBitmap(bmp, fallback)
}
