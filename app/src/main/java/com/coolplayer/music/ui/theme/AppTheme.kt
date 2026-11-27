package com.coolplayer.music.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Cool Player TV 主题入口（固定浅色）。
 *
 * TV 壁纸多为浅色，动态取色会把 primary 拉成浅紫，导致当前导航项、
 * 当前歌曲名、进度条等用 primary 高亮的元素在白底上发白看不清。
 * 因此入口默认关闭动态取色，使用 [seedColor]（默认蓝 #3D5AFE）保证对比度。
 */
@Composable
fun AppTheme(
    seedColor: Color = ThemeState.homeSeed,
    darkTheme: Boolean = false,
    allowDynamic: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = allowDynamic && ThemeState.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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

// ── 字体：使用 Material3 默认 ──────────────────────────────────────────
private val Typography = androidx.compose.material3.Typography()
