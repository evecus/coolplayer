package com.coolplayer.music.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Cool Player TV —— 纯 TV 布局参数注入。
 *
 * 本项目只面向电视：无需设备形态检测与多形态回退分支。
 * 这里统一把 TV 的字号放大 1.25 倍（仅缩放字体，不改 dp），
 * 让 10 英尺遥控交互下的文字更易读。
 */
private const val TV_FONT_SCALE = 1.25f

/** 根布局注入：TV 端统一放大字号（仅缩放字体，不改 dp） */
@Composable
fun ProvideAdaptive(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val tvDensity = remember(density) {
        Density(density.density, density.fontScale * TV_FONT_SCALE)
    }
    CompositionLocalProvider(LocalDensity provides tvDensity, content = content)
}
