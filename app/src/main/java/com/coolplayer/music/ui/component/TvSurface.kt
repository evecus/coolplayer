package com.coolplayer.music.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface as TvSurface

private val TvFocusRing = Color(0xFFFF9800)

/**
 * 基于 Compose for TV（androidx.tv:tv-material）Surface 的可聚焦卡片：
 * 聚焦时平滑放大 + 橙色描边 + 柔和光晕；OK 键 / 点击触发 [onClick]，[onLongClick] 对应 OK 长按。
 *
 * 内容里的 Text / Icon 仍使用 material3，不受 tv 主题影响（颜色沿用外层 material3 的 LocalContentColor）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = containerColor,
    focusedScale: Float = 1.05f,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentColor = LocalContentColor.current
    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = contentColor,
            pressedContainerColor = focusedContainerColor,
            pressedContentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, TvFocusRing),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = TvFocusRing.copy(alpha = 0.4f),
                elevation = 12.dp,
            ),
        ),
        content = content,
    )
}

/** 圆形图标按钮（聚焦放大 + 描边 + 光晕），内容居中。默认 48dp，可用 [size] 调整。 */
@Composable
fun TvCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    TvFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = containerColor,
        focusedScale = 1.12f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
