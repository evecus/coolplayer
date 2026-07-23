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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn

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

// ── 平板 / 大屏适配辅助 ──────────────────────────────────────────────

/**
 * 屏幕宽度分级（参照 Material 窗口尺寸类）。
 */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 当前窗口的尺寸信息，用于平板/大屏自适应布局。
 *
 * - [isTablet]：宽度 ≥ 600dp（MEDIUM/EXPANDED）即视为平板。
 * - [maxContentWidth]：建议的内容最大宽度。COMPACT 时不限制（[Dp.Infinity]），
 *   平板上居中并限宽，避免列表/设置项在大屏上拉得过宽而难以阅读。
 * - [isLandscapeTablet]：平板且横屏，播放页等可使用双栏布局。
 */
data class WindowInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isLandscape: Boolean,
    val widthClass: WindowWidthClass,
    val isTablet: Boolean,
    val isLargeTablet: Boolean,
    val isLandscapeTablet: Boolean,
    val maxContentWidth: Dp,
    val contentPadding: Dp
) {
    companion object {
        val Compact = WindowInfo(
            screenWidthDp = 360,
            screenHeightDp = 640,
            isLandscape = false,
            widthClass = WindowWidthClass.COMPACT,
            isTablet = false,
            isLargeTablet = false,
            isLandscapeTablet = false,
            maxContentWidth = Dp.Infinity,
            contentPadding = 0.dp
        )
    }
}

private val LocalWindowInfo = staticCompositionLocalOf { WindowInfo.Compact }

/**
 * 获取当前窗口的尺寸信息。在 Compose 树中由 [provideWindowInfo] 注入；
 * 若未注入则回退到 [WindowInfo.Compact]（手机布局）。
 */
val currentWindowInfo: WindowInfo
    @Composable
    @ReadOnlyComposable
    get() = LocalWindowInfo.current

/**
 * 读取 [android.content.res.Configuration] 构造 [WindowInfo]，并用
 * [CompositionLocalProvider] 注入到子树，便于深层组件直接通过 [currentWindowInfo] 读取。
 */
@Composable
fun rememberWindowInfo(): WindowInfo {
    val configuration = LocalConfiguration.current
    val w = configuration.screenWidthDp
    val h = configuration.screenHeightDp
    val widthClass = when {
        w < 600 -> WindowWidthClass.COMPACT
        w < 840 -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
    val maxContentWidth = when (widthClass) {
        WindowWidthClass.COMPACT -> Dp.Infinity
        WindowWidthClass.MEDIUM -> 600.dp
        WindowWidthClass.EXPANDED -> 720.dp
    }
    val contentPadding = when (widthClass) {
        WindowWidthClass.COMPACT -> 0.dp
        WindowWidthClass.MEDIUM -> 24.dp
        WindowWidthClass.EXPANDED -> 32.dp
    }
    val isTablet = widthClass != WindowWidthClass.COMPACT
    val isLargeTablet = widthClass == WindowWidthClass.EXPANDED
    val isLandscape = w >= h
    return WindowInfo(
        screenWidthDp = w,
        screenHeightDp = h,
        isLandscape = isLandscape,
        widthClass = widthClass,
        isTablet = isTablet,
        isLargeTablet = isLargeTablet,
        isLandscapeTablet = isTablet && isLandscape,
        maxContentWidth = maxContentWidth,
        contentPadding = contentPadding
    )
}

/**
 * 在根 Composable 中包裹内容，注入 [WindowInfo]，使整棵树可通过 [currentWindowInfo] 读取窗口尺寸。
 */
@Composable
fun ProvideWindowInfo(content: @Composable () -> Unit) {
    val info = rememberWindowInfo()
    CompositionLocalProvider(LocalWindowInfo provides info, content = content)
}

/**
 * 将元素最大宽度限制为 [WindowInfo.maxContentWidth]，用于平板上居中限宽列表/详情/设置内容。
 *
 * - 手机（COMPACT）：[WindowInfo.maxContentWidth] 为 [Dp.Infinity]，本修饰符为 no-op，
 *   元素仍可铺满父容器宽度。
 * - 平板：限制最大宽度；调用方通常将父容器 [Box] 的 `contentAlignment` 设为
 *   [androidx.compose.ui.Alignment.TopCenter] 以居中显示。
 *
 * 该扩展读取 [currentWindowInfo]，需在 Composable 上下文中使用。
 */
@Composable
fun Modifier.boundTabletWidth(): Modifier {
    val max = currentWindowInfo.maxContentWidth
    return if (max == Dp.Infinity) this else this.widthIn(max = max)
}
