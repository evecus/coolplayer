package com.coolplayer.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

// 焦点框配色：淡橙底 + 4dp 橙色描边 + 1dp 白色内线，白底/黑底都醒目
private val FocusFill = Color(0x33FF9800)
private val FocusRing = Color(0xFFFF9800)
private val FocusWhite = Color(0xCCFFFFFF)

// TV 当前态高亮色：固定深蓝/深紫，不随系统取色或主题深浅变化。
// 用于"当前导航项 / 当前播放歌曲 / 当前歌词 / 选中项"等文字与图标，
// 杜绝浅色主题下白字压在浅底上糊成一片的问题。
val TvAccent = Color(0xFF3949AB)      // 深蓝紫，浅色底上对比度高
val TvAccentSoft = Color(0x1A3949AB)  // 同色系 10% 透明，做浅色高亮背景

// TV 固定文字色：不依赖主题 colorScheme，任何情况下都不会被渲染成白色。
// TvTextDark = 近黑（主文字）；TvTextMuted = 深灰（次要文字）。
val TvTextDark = Color(0xFF1A1A1A)
val TvTextMuted = Color(0xFF565B66)

/**
 * 彩色焦点框。修饰链结构恒定：未聚焦时颜色透明，避免焦点节点被重建导致焦点丢失。
 */
private fun Modifier.focusFrame(focused: Boolean, shape: Shape): Modifier = this
    .background(if (focused) FocusFill else Color.Transparent, shape)
    .border(4.dp, if (focused) FocusRing else Color.Transparent, shape)
    .border(1.dp, if (focused) FocusWhite else Color.Transparent, shape)

/**
 * TV 统一可聚焦修饰：遥控器可聚焦 + 橙色焦点框，
 * 方向键由系统按空间位置找下一个焦点。
 * 注意顺序：必须放在 .clickable{} 之前（更靠外），否则 onFocusChanged 观察不到焦点。
 */
fun Modifier.lxFocusable(
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    showBorder: Boolean = true,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    var result = this
        .onFocusChanged { focused = it.hasFocus }
        .focusable()
        .zIndex(if (focused) 1f else 0f)
    if (showBorder) result = result.focusFrame(focused, shape)
    if (focusRequester != null) result.focusRequester(focusRequester) else result
}

/** 等节点挂载后再请求焦点（LazyColumn 条目在布局阶段才组合） */
suspend fun FocusRequester.requestFocusWhenReady(maxFrames: Int = 12) {
    repeat(maxFrames) {
        androidx.compose.runtime.withFrameNanos { }
        val ok = try {
            requestFocus()
            true
        } catch (_: IllegalStateException) {
            false
        }
        if (ok) return
    }
}

/** 只显示焦点框，不额外增加焦点目标（用于 Slider 等自带焦点的组件） */
fun Modifier.lxFocusRing(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.hasFocus }.focusFrame(focused, shape)
}

/** TV 友好的图标按钮：TV 放大尺寸并显示焦点框 */
@Composable
fun LxIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    compact: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val size = if (compact) 48.dp else 64.dp
    val iconSize = if (compact) 20.dp else 64.dp * 0.6f
    TvFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = CircleShape,
        focusedScale = 1.12f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

// ── DropdownMenu TV 支持 ─────────────────────────────────────────

private val LocalMenuFirstFocus = compositionLocalOf<FocusRequester?> { null }

/** TV 友好的下拉菜单：弹出后自动把焦点移到标记 first 的第一项 */
@Composable
fun LxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        CompositionLocalProvider(LocalMenuFirstFocus provides firstFocus) {
            content()
        }
        if (expanded) {
            LaunchedEffect(expanded) {
                delay(60)
                runCatching { firstFocus.requestFocus() }
            }
        }
    }
}

/** 菜单项修饰：TV 聚焦高亮；first=true 的项在菜单弹出时自动获焦 */
fun Modifier.lxMenuItem(first: Boolean = false): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    var m = this.onFocusChanged { focused = it.hasFocus }
        .focusFrame(focused, RoundedCornerShape(8.dp))
    if (first) {
        val requester = LocalMenuFirstFocus.current
        if (requester != null) m = m.focusRequester(requester)
    }
    m
}

/** TV 列表行焦点导航：行聚焦时按右键显式把焦点移到行尾更多按钮 */
fun Modifier.lxRowFocusNav(
    moreFocus: FocusRequester,
    enabled: () -> Boolean = { true },
): Modifier = composed {
    this.onKeyEvent { event ->
        if (enabled() && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
            runCatching { moreFocus.requestFocus() }
            true
        } else false
    }
}
