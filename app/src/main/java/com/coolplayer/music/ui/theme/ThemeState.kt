package com.coolplayer.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * TV 版主题常量：固定浅色 + 默认深蓝种子色。
 *
 * 不读取任何持久化值（TV 版没有设置页），避免历史残留的主题色
 * 让 scheme.primary 变白、导致高亮文字在浅色背景上看不清。
 */
object ThemeState {
    val homeSeedDefault: Color = Color(0xFF3D5AFE)
}
