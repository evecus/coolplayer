package com.coolplayer.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Salt Music 主题入口：
 *
 * - Android 12+ 且用户开启动态取色 → 用系统壁纸取色 ([dynamicLightColorScheme]/[dynamicDarkColorScheme])
 * - 否则使用 [seedColor] 生成 colorScheme
 *
 * 播放页可单独传入 [playerSeed] 实现「分控主题」（首页与播放页不同色）。
 */
@Composable
fun AppTheme(
    seedColor: Color = ThemeState.homeSeed,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = ThemeState.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(primary = seedColor)
        else -> lightColorScheme(primary = seedColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 播放页独立主题包装：传入 [playerSeed] 后内部用新的 colorScheme 渲染。
 * 优先级：[coverSeed] > [playerSeed] > 父主题色。
 */
@Composable
fun PlayerTheme(
    coverSeed: Color? = null,
    playerSeed: Color = ThemeState.playerSeed,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val effectiveSeed = coverSeed ?: playerSeed
    val useDynamic = ThemeState.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && coverSeed == null
    val context = LocalContext.current
    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(primary = effectiveSeed)
        else -> lightColorScheme(primary = effectiveSeed)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// ── 字体：使用 Material3 默认 ──────────────────────────────────────────

private val Typography = androidx.compose.material3.Typography()
