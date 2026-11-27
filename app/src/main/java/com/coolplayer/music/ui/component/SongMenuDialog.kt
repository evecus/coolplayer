package com.coolplayer.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.coolplayer.music.data.PlaylistEntity
import com.coolplayer.music.data.SongEntry

/**
 * TV 遥控器「菜单键」在歌曲上触发的操作弹窗。
 *
 * 选项：
 *  1. 下一首播放
 *  2. 添加到《歌单名》（逐个已存在的歌单）
 *  3. 从音乐库隐藏（列表不显示）
 * 选中任一项后执行动作并关闭弹窗。首个选项自动获焦，全部选项带 TV 焦点框。
 */
@Composable
fun SongMenuDialog(
    song: SongEntry,
    playlists: List<PlaylistEntity>,
    onPlayNext: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocusWhenReady() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                song.title.ifBlank { song.name },
                color = TvTextDark,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            LazyColumn {
                item {
                    MenuRow(
                        icon = Icons.Default.QueueMusic,
                        label = "下一首播放",
                        first = true,
                        focusRequester = firstFocus,
                    ) { onPlayNext(); onDismiss() }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        Text(
                            "添加到歌单",
                            style = MaterialTheme.typography.labelMedium,
                            color = TvTextMuted,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    itemsIndexed(playlists) { index, p ->
                        MenuRow(
                            icon = Icons.Default.Add,
                            label = p.name.ifBlank { "未命名歌单" },
                            first = false,
                        ) { onAddToPlaylist(p.id); onDismiss() }
                    }
                } else {
                    item {
                        Text(
                            "暂无歌单，可先到「歌单」页新建",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextMuted,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
                item {
                    MenuRow(
                        icon = Icons.Default.DeleteOutline,
                        label = "从音乐库隐藏（列表不显示）",
                        first = false,
                    ) { onHide(); onDismiss() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.lxFocusRing()) {
                Text("关闭", color = TvAccent)
            }
        },
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    first: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (first && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = RoundedCornerShape(10.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        focusedScale = 1.03f,
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TvAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TvTextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
}
