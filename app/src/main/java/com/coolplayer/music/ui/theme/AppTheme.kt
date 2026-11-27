package com.coolplayer.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Cool Player TV 主题入口（固定浅色，不使用动态取色）。
 *
 * TV 壁纸多为浅色，动态取色会把 primary 拉成浅紫，导致当前导航项、
 * 当前歌曲名、进度条等用 primary 高亮的元素在白底上发白看不清。
 * 因此固定使用 [seedColor]（默认蓝 #3D5AFE）保证对比度。
 */
@Composable
fun AppTheme(
    seedColor: Color = ThemeState.homeSeedDefault,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = seedColor),
        typography = Typography(),
        content = content
    )
}
