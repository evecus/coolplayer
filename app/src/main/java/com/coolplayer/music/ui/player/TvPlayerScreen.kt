package com.coolplayer.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.App
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.player.MusicPlayer
import com.coolplayer.music.player.PlayMode
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.player.SleepMode
import androidx.compose.ui.focus.focusRequester
import com.coolplayer.music.ui.component.LxIconButton
import com.coolplayer.music.ui.component.TvAccent
import com.coolplayer.music.ui.component.TvAccentSoft
import com.coolplayer.music.ui.component.TvTextDark
import com.coolplayer.music.ui.component.TvCircleButton
import com.coolplayer.music.ui.component.TvFocusSurface
import com.coolplayer.music.ui.component.requestFocusWhenReady
import kotlinx.coroutines.launch

/**
 * TV 播放页 —— FMusic 风格移植版。
 *
 * 布局（TV 大屏横屏）：
 *  - 顶部：歌名 / 歌手大字（无返回按钮，遥控器返回键承担）
 *  - 中部：左侧方形封面 + 右侧逐行高亮自动滚动歌词
 *  - 进度行：[快退10s] ---- 进度条 ---- [快进10s] 时间
 *  - 控制行（同一排）：收藏 / EQ ｜ 上一首 / 播放暂停（居中突出）/ 下一首 ｜ 播放模式 / 队列
 * 全部控件带 TV 焦点框；进入页面自动把焦点落到播放/暂停键。
 */
@Composable
fun TvPlayerScreen(
    onBack: () -> Unit,
    onOpenAudioFx: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = PlayerConnection.player.collectAsState().value
    if (player == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("等待播放器就绪…", style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val playMode by player.playMode.collectAsState()
    val title by player.title.collectAsState()
    val artist by player.artist.collectAsState()
    val playlist by player.playlist.collectAsState()
    val currentIdx by player.currentIdx.collectAsState()
    val coverBytes by player.coverBytes.collectAsState()
    val lyrics by player.lyrics.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()

    val current: SongEntry? = playlist.getOrNull(currentIdx)
    val displayTitle = title.ifBlank { current?.title?.ifBlank { current.name } ?: "暂无播放" }
    val displayArtist = artist.ifBlank { current?.artist ?: "" }

    var showQueue by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = remember { MusicRepository(App.database) }

    // 收藏状态
    LaunchedEffect(current?.path) {
        current?.path?.let { path ->
            isFavorite = repo.isFavorite(path)
        } ?: run { isFavorite = false }
    }

    // TV：进入播放页自动落焦点到播放/暂停键
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocusWhenReady() } }

    Column(modifier = modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 6.dp)) {
        // ── 顶部标题 ──
        Text(
            displayTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            displayArtist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(12.dp))

        // ── 中部：左封面 + 右歌词 ──
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            BoxWithConstraints(
                modifier = Modifier.weight(0.85f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // 封面随可用空间自适应放大（取宽高较小者的 92%），不再固定 320dp
                CoverSurface(coverBytes = coverBytes, title = displayTitle, size = minOf(maxWidth, maxHeight) * 0.92f)
            }
            Box(modifier = Modifier.weight(1.15f).fillMaxSize()) {
                LyricsPane(lyrics = lyrics, positionMs = position)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 进度行（缩短并居中，宽度与下方控制行大致对齐）──
        Row(
            modifier = Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LxIconButton(
                onClick = { player.seekTo((position - 10_000).coerceAtLeast(0)) },
                imageVector = Icons.Default.FastRewind,
                contentDescription = "快退10秒",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                compact = true,
            )
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f },
                color = TvAccent,
                trackColor = TvAccentSoft,
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            LxIconButton(
                onClick = { if (duration > 0) player.seekTo((position + 10_000).coerceAtMost(duration)) },
                imageVector = Icons.Default.FastForward,
                contentDescription = "快进10秒",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                compact = true,
            )
            Text(
                "${formatTime(position)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        Spacer(Modifier.height(4.dp))

        // ── 控制行：左 2（收藏 / EQ）｜ 上一首 / 播放暂停 / 下一首 ｜ 右 2（播放模式 / 队列）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxIconButton(
                    onClick = {
                        current?.let { song ->
                            scope.launch {
                                repo.toggleFavorite(song)
                                isFavorite = repo.isFavorite(song.path)
                            }
                        }
                    },
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    // 固定色：已收藏=深蓝紫高亮（不再因 primary 被取成白色而"消失"），
                    // 未收藏=黑色。不再依赖会被主题/取色污染的 primary。
                    tint = if (isFavorite) TvAccent else TvTextDark,
                    compact = true,
                )
                LxIconButton(
                    onClick = onOpenAudioFx,
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "音效 EQ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
            }

            Spacer(Modifier.width(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxIconButton(
                    onClick = { player.prev() },
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    compact = true,
                )
                TvCircleButton(
                    onClick = { player.togglePlay() },
                    modifier = Modifier.focusRequester(playFocus),
                    size = 56.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = TvTextDark,
                        modifier = Modifier.size(30.dp),
                    )
                }
                LxIconButton(
                    onClick = { player.next() },
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    compact = true,
                )
            }

            Spacer(Modifier.width(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxIconButton(
                    onClick = { player.cyclePlayMode() },
                    imageVector = when (playMode) {
                        PlayMode.LIST -> Icons.Default.Repeat
                        PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                    },
                    contentDescription = "播放模式",
                    tint = if (playMode != PlayMode.LIST) TvAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
                LxIconButton(
                    onClick = { showQueue = true },
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "播放队列",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
            }
        }
    }

    // ── 播放队列弹层 ──
    if (showQueue) {
        QueueDialog(
            playlist = playlist,
            currentIdx = currentIdx,
            onPlayAt = { player.playAt(it) },
            onDismiss = { showQueue = false },
        )
    }
}

/** 封面：优先用内存中的封面字节，无封面显示占位图标 */
@Composable
private fun CoverSurface(coverBytes: ByteArray?, title: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = remember(coverBytes) {
            coverBytes?.let {
                runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
            }
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.35f),
            )
        }
    }
}

/** 歌词：逐行显示，当前行高亮并自动滚动到偏上位置 */
@Composable
private fun LyricsPane(lyrics: String, positionMs: Long) {
    val lines = remember(lyrics) { parseLrc(lyrics) }
    val listState = rememberLazyListState()
    val currentIndex = if (lines.isEmpty()) -1 else lines.indexOfLast { it.first <= positionMs }

    LaunchedEffect(lines) { if (lines.isNotEmpty()) listState.scrollToItem(0) }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }

    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Spacer(Modifier.height(32.dp)) }
        itemsIndexed(lines) { index, line ->
            val current = index == currentIndex
            Text(
                line.second.ifEmpty { " " },
                fontSize = if (current) 20.sp else 16.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) TvAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun QueueDialog(
    playlist: List<SongEntry>,
    currentIdx: Int,
    onPlayAt: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocusWhenReady() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放队列（${playlist.size}）") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                itemsIndexed(playlist) { index, song ->
                    val active = index == currentIdx
                    TvFocusSurface(
                        onClick = { onPlayAt(index); onDismiss() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.02f,
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}. ${song.title.ifBlank { song.name }}",
                            color = if (active) TvAccent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private val lrcTagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")

private fun parseLrc(raw: String): List<Pair<Long, String>> {
    val out = mutableListOf<Pair<Long, String>>()
    for (line in raw.lines()) {
        val tags = lrcTagRegex.findAll(line).toList()
        if (tags.isEmpty()) continue
        val text = line.replace(lrcTagRegex, "").trim()
        for (m in tags) {
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = when (val f = m.groupValues[3]) {
                "" -> 0L
                else -> (f + "000").take(3).toLong()
            }
            out += (min * 60_000 + sec * 1000 + frac) to text
        }
    }
    return out.sortedBy { it.first }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
