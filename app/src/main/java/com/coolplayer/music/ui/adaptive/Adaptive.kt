package com.coolplayer.music.ui.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cool Player TV —— 自适应布局信息。
 *
 * 本项目为纯 TV 播放器，但保留 FMusic 的 Adaptive 结构：默认按设备检测，
 * 命中的就是 TV；在手机/模拟器上调试时回退成紧凑参数，方便预览。
 * 全应用经 [LocalAdaptive] 共享这些尺寸，TV 统一放大字号与控件。
 */
enum class DeviceType { PHONE, TABLET, TV }
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

data class AdaptiveInfo(
    val deviceType: DeviceType,
    val widthClass: WidthClass,
) {
    val isPhone: Boolean get() = deviceType == DeviceType.PHONE
    val isTablet: Boolean get() = deviceType == DeviceType.TABLET
    val isTv: Boolean get() = deviceType == DeviceType.TV
    val twoPane: Boolean get() = !isPhone

    val gridColumns: Int
        get() = when (deviceType) {
            DeviceType.PHONE -> 2
            DeviceType.TABLET -> if (widthClass == WidthClass.EXPANDED) 4 else 3
            DeviceType.TV -> 5
        }

    val pagePadding: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> 8.dp
            DeviceType.TABLET -> 20.dp
            DeviceType.TV -> 40.dp
        }

    val rowHeight: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> 64.dp
            DeviceType.TABLET -> 72.dp
            DeviceType.TV -> 88.dp
        }

    val iconButtonSize: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> 40.dp
            DeviceType.TABLET -> 48.dp
            DeviceType.TV -> 64.dp
        }

    val contentMaxWidth: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> Dp.Unspecified
            DeviceType.TABLET -> 780.dp
            DeviceType.TV -> 1600.dp
        }

    val fontScale: Float
        get() = when (deviceType) {
            DeviceType.PHONE -> 1f
            DeviceType.TABLET -> 1.05f
            DeviceType.TV -> 1.25f
        }
}

/** 默认 PHONE/COMPACT（编辑预览安全值） */
val LocalAdaptive = compositionLocalOf { AdaptiveInfo(DeviceType.PHONE, WidthClass.COMPACT) }

/** 根布局注入：提供设备形态信息，TV 端统一放大字号（仅缩放字体，不改 dp） */
@Composable
fun ProvideAdaptive(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val info = remember(context, configuration.screenWidthDp, configuration.smallestScreenWidthDp) {
        AdaptiveInfo(
            deviceType = detectDeviceType(context, configuration),
            widthClass = when {
                configuration.screenWidthDp < 600 -> WidthClass.COMPACT
                configuration.screenWidthDp < 840 -> WidthClass.MEDIUM
                else -> WidthClass.EXPANDED
            },
        )
    }
    val density = LocalDensity.current
    val scaled = remember(density, info) { Density(density.density, density.fontScale * info.fontScale) }
    CompositionLocalProvider(LocalAdaptive provides info, LocalDensity provides scaled, content = content)
}

private fun detectDeviceType(context: Context, configuration: Configuration): DeviceType {
    val pm = context.packageManager
    val leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val tvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (leanback || tvUiMode) return DeviceType.TV
    return if (configuration.smallestScreenWidthDp >= 600) DeviceType.TABLET else DeviceType.PHONE
}
