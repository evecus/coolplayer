package com.coolplayer.music.ui.player

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.toSongEntry
import com.coolplayer.music.player.MusicPlayer
import com.coolplayer.music.player.PlayMode
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.player.SleepMode
import com.coolplayer.music.ui.theme.PlayerTheme
import com.coolplayer.music.ui.theme.ThemeState
import com.coolplayer.music.ui.theme.boundTabletWidth
import com.coolplayer.music.ui.theme.currentWindowInfo
import com.coolplayer.music.ui.theme.extractSeedFromBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Salt 风格沉浸式播放页：
 *
 * - 封面高斯模糊做背景（Android 12+ RenderEffect）+ 主色径向渐变叠加 = 流光
 * - 大封面 / 歌词左右滑动切换
 * - 顶部：返回 + 收藏按钮 + 睡眠定时器入口
 * - 底部：进度条 + 控制按钮 + 播放模式 + 队列入口
 * - 上滑打开播放队列（与原 MEPlayer 一致）
 *
 * 主题色：优先从封面提取（useCoverColor=true 时），其次用 [ThemeState.playerSeed]。
 */
@Composable
fun PlayerScreen(
    navController: NavHostController,
    onBack: () -> Unit
) {
    val player = PlayerConnection.player.collectAsState().value
    if (player == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("等待播放器就绪…", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    val title by player.title.collectAsState()
    val artist by player.artist.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val coverBytes by player.coverBytes.collectAsState()
    val lyrics by player.lyrics.collectAsState()
    val playMode by player.playMode.collectAsState()
    val currentIdx by player.currentIdx.collectAsState()
    val sleepActive by player.sleepTimer.active.collectAsState()
    val sleepMode by player.sleepTimer.mode.collectAsState()
    val sleepRemaining by player.sleepTimer.remainingMs.collectAsState()
    val playlist by player.playlist.collectAsState()

    // 从封面提取主题色
    var coverSeed by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(coverBytes) {
        coverSeed = if (ThemeState.useCoverColor) {
            extractSeedFromBytes(coverBytes, ThemeState.playerSeed)
        } else null
    }

    val lrcState = rememberLrcViewState()
    LaunchedEffect(position) { lrcState.updateProgress(position) }

    // 收藏状态：每次切歌时重新查询
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentSong = playlist.getOrNull(currentIdx)
    var isFav by remember { mutableStateOf(false) }
    LaunchedEffect(currentSong?.path) {
        val path = currentSong?.path
        if (path != null) {
            val repo = MusicRepository(AppDatabase.get(com.coolplayer.music.App.context))
            repo.isFavoriteFlow(path).collectLatest { isFav = it }
        } else {
            isFav = false
        }
    }

    // 睡眠定时器弹窗
    var showSleepDialog by remember { mutableStateOf(false) }

    // 歌词设置弹窗
    var showLyricsSettings by remember { mutableStateOf(false) }

    // 队列面板
    var showQueue by remember { mutableStateOf(false) }

    val windowInfo = currentWindowInfo
    val twoPane = windowInfo.isLandscapeTablet

    PlayerTheme(coverSeed = coverSeed) {
        val scheme = MaterialTheme.colorScheme
        Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
            // 1) 沉浸背景层
            ImmersiveBackground(
                coverBytes = coverBytes,
                seedColor = scheme.primary,
                modifier = Modifier.fillMaxSize()
            )

            // 2) 内容层
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // 顶部栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = scheme.onBackground,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onBack)
                            .padding(8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    // 收藏
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFav) scheme.primary else scheme.onBackground,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                val song = playlist.getOrNull(currentIdx)
                                if (song != null) {
                                    scope.launch {
                                        MusicRepository(AppDatabase.get(com.coolplayer.music.App.context)).toggleFavorite(song)
                                    }
                                }
                            }
                            .padding(8.dp)
                    )
                    // 音效均衡器（AudioFX）
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "音效",
                        tint = scheme.onBackground,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { com.coolplayer.music.ui.navigation.AppNavigator.toAudioFx(navController) }
                            .padding(8.dp)
                    )
                    // 睡眠定时器
                    Box {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "睡眠定时器",
                            tint = if (sleepActive) scheme.primary else scheme.onBackground,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showSleepDialog = true }
                                .padding(8.dp)
                        )
                    }
                }

                // 中间：封面/歌词（左右划切换，上划开队列）
                // 横屏平板使用双栏（封面 | 歌词），其余情况使用左右滑动 Pager。
                val pagerState = rememberPagerState(pageCount = { 2 })
                var totalDrag by remember { mutableStateOf(0f) }
                val density = LocalDensity.current
                val openThresholdPx = with(density) { 80.dp.toPx() }
                val coverSize = when {
                    windowInfo.isLargeTablet -> 320.dp
                    windowInfo.isTablet -> 300.dp
                    else -> 280.dp
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = {
                                    if (totalDrag < -openThresholdPx) showQueue = true
                                    totalDrag = 0f
                                },
                                onDragCancel = { totalDrag = 0f }
                            ) { _, dragAmount -> totalDrag += dragAmount }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (twoPane) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CoverDisplay(
                                    coverBytes = coverBytes,
                                    size = coverSize,
                                    isPlaying = isPlaying
                                )
                            }
                            LyricsPane(
                                lyrics = lyrics,
                                highlightColor = scheme.primary,
                                normalColor = scheme.onBackground,
                                state = lrcState,
                                onOpenSettings = { showLyricsSettings = true },
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                        }
                    } else {
                        HorizontalPager(state = pagerState) { page ->
                            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val a = (1f - kotlin.math.abs(offset)).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier.fillMaxSize().alpha(a),
                                contentAlignment = Alignment.Center
                            ) {
                                if (page == 0) {
                                    CoverDisplay(
                                        coverBytes = coverBytes,
                                        size = coverSize,
                                        isPlaying = isPlaying
                                    )
                                } else {
                                    LyricsPane(
                                        lyrics = lyrics,
                                        highlightColor = scheme.primary,
                                        normalColor = scheme.onBackground,
                                        state = lrcState,
                                        onOpenSettings = { showLyricsSettings = true },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部：进度条 + 控制按钮
                ProgressBar(
                    position = position,
                    duration = duration,
                    onSeek = { player.seekTo(it) }
                )
                ControlBar(
                    isPlaying = isPlaying,
                    playMode = playMode,
                    onTogglePlay = { player.togglePlay() },
                    onPrev = { player.prev() },
                    onNext = { player.next() },
                    onCycleMode = { player.cyclePlayMode() },
                    onQueue = { showQueue = true }
                )
                Spacer(Modifier.size(8.dp))
            }

            // 队列面板
            QueuePanel(
                visible = showQueue,
                playlist = playlist,
                currentIdx = currentIdx,
                playMode = playMode,
                onClose = { showQueue = false },
                onPlayAt = { player.playAt(it); showQueue = false }
            )
        }

        if (showSleepDialog) {
            SleepTimerDialog(
                active = sleepActive,
                mode = sleepMode,
                remainingMs = sleepRemaining,
                onSetCountdown = { player.sleepTimer.setCountdown(it) },
                onSetFinishCurrent = { player.sleepTimer.setFinishCurrent() },
                onCancel = { player.sleepTimer.cancel() },
                onDismiss = { showSleepDialog = false }
            )
        }

        if (showLyricsSettings) {
            LyricsSettingsDialog(
                onDismiss = { showLyricsSettings = false }
            )
        }
    }
}

// ── 沉浸式背景：封面模糊 + 主色径向渐变 ─────────────────────────────────

@Composable
private fun ImmersiveBackground(
    coverBytes: ByteArray?,
    seedColor: Color,
    modifier: Modifier = Modifier
) {
    val bmp = remember(coverBytes) {
        if (coverBytes == null) null
        else runCatching {
            BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
        }.getOrNull()
    }
    Box(modifier = modifier) {
        if (bmp != null) {
            // 用 Modifier.blur 实现高斯模糊（Compose 内部会按版本选择 RenderEffect 或降级）
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .run {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            this.blur(radius = 32.dp)
                        } else {
                            this.alpha(0.5f)
                        }
                    }
            )
        } else {
            // 无封面：单色背景 + 渐变
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(seedColor.copy(alpha = 0.2f))
            )
        }
        // 流光：从主色到透明 + 从透明到主色，多层叠加
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            seedColor.copy(alpha = 0.55f),
                            seedColor.copy(alpha = 0.35f),
                            seedColor.copy(alpha = 0.65f)
                        )
                    )
                )
        )
        // 黑色半透明压暗，提高前景可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )
    }
}

// ── 封面展示（带旋转动画） ──────────────────────────────────────────────

@Composable
private fun CoverDisplay(
    coverBytes: ByteArray?,
    size: Dp,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // 黑胶旋转动画
    val rotation = produceCoverRotation(isPlaying)
    if (coverBytes != null) {
        val bmp = remember(coverBytes) {
            runCatching {
                BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
            }.getOrNull()
        }
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer { this.rotationZ = rotation }
            )
            return
        }
    }
    Column(
        modifier = modifier
            .graphicsLayer { this.rotationZ = rotation },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = scheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size((size.value * 0.38f).dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "暂无封面",
            fontSize = 14.sp,
            color = scheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

/** 黑胶旋转动画：播放时每 20 秒转一圈，暂停时停止。 */
@Composable
private fun produceCoverRotation(isPlaying: Boolean): Float {
    var rotation by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(50)
            rotation = (rotation + 0.9f) % 360f  // 360 / 0.9 = 400 帧 * 50ms = 20s/圈
        }
    }
    return rotation
}

// ── 进度条 ─────────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = formatTime(position),
            fontSize = 12.sp,
            color = scheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.size(width = 44.dp, height = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Slider(
            value = fraction,
            onValueChange = { v ->
                if (duration > 0) onSeek((v * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = scheme.onPrimary,
                activeTrackColor = scheme.onPrimary,
                inactiveTrackColor = scheme.onPrimary.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = formatTime(duration),
            fontSize = 12.sp,
            color = scheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.size(width = 44.dp, height = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ── 控制按钮 ────────────────────────────────────────────────────────────

@Composable
private fun ControlBar(
    isPlaying: Boolean,
    playMode: PlayMode,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleMode: () -> Unit,
    onQueue: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = playModeIcon(playMode),
                contentDescription = playModeLabel(playMode),
                tint = if (playMode != PlayMode.LIST) scheme.onPrimary else scheme.onBackground,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onCycleMode)
                    .padding(10.dp)
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                tint = scheme.onBackground,
                modifier = Modifier
                    .size(52.dp)
                    .clickable(onClick = onPrev)
                    .padding(8.dp)
            )
        }
        Box(Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = scheme.onPrimary,
                modifier = Modifier
                    .size(64.dp)
                    .clickable(onClick = onTogglePlay)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = scheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                tint = scheme.onBackground,
                modifier = Modifier
                    .size(52.dp)
                    .clickable(onClick = onNext)
                    .padding(8.dp)
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = "播放队列",
                tint = scheme.onBackground,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onQueue)
                    .padding(10.dp)
            )
        }
    }
}

private fun playModeIcon(mode: PlayMode) = when (mode) {
    PlayMode.LIST -> Icons.Default.Repeat
    PlayMode.SHUFFLE -> Icons.Default.Shuffle
    PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
}

private fun playModeLabel(mode: PlayMode) = when (mode) {
    PlayMode.LIST -> "顺序播放"
    PlayMode.SHUFFLE -> "随机播放"
    PlayMode.REPEAT_ONE -> "单曲循环"
}

// ── 队列面板 ────────────────────────────────────────────────────────────

@Composable
private fun QueuePanel(
    visible: Boolean,
    playlist: List<com.coolplayer.music.data.SongEntry>,
    currentIdx: Int,
    playMode: PlayMode,
    onClose: () -> Unit,
    onPlayAt: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        var totalDrag by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val closeThresholdPx = with(density) { 80.dp.toPx() }
        val closeModifier = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { totalDrag = 0f },
                onDragEnd = {
                    if (totalDrag > closeThresholdPx) onClose()
                    totalDrag = 0f
                },
                onDragCancel = { totalDrag = 0f }
            ) { _, dragAmount -> totalDrag += dragAmount }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background.copy(alpha = 0.96f)),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .boundTabletWidth()
                    .then(closeModifier)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            ) {
                Text(
                    text = "此处向下轻扫以返回",
                    fontSize = 13.sp,
                    color = scheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "播放队列",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
                Text(
                    text = "共 ${playlist.size} 首 · ${playModeLabel(playMode)}",
                    fontSize = 12.sp,
                    color = scheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(playlist) { i, item ->
                        val isCurrent = i == currentIdx
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAt(i) }
                                .padding(vertical = 8.dp)
                        ) {
                            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = item.title,
                                fontSize = 15.sp,
                                color = if (isCurrent) scheme.primary else scheme.onBackground,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClose() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = playModeIcon(playMode),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = playModeLabel(playMode),
                        fontSize = 14.sp,
                        color = scheme.primary
                    )
                }
            }
        }
    }
}

// ── 睡眠定时器对话框 ────────────────────────────────────────────────────

@Composable
private fun SleepTimerDialog(
    active: Boolean,
    mode: SleepMode,
    remainingMs: Long,
    onSetCountdown: (Long) -> Unit,
    onSetFinishCurrent: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时器") },
        text = {
            Column {
                if (active) {
                    Text(
                        text = when (mode) {
                            SleepMode.COUNTDOWN -> "剩余 ${formatTime(remainingMs)}"
                            SleepMode.FINISH_CURRENT -> "播完当前歌曲后停止"
                            SleepMode.OFF -> "未开启"
                        },
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text("设置倒计时", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf("15分" to 15L, "30分" to 30L, "45分" to 45L, "60分" to 60L).forEach { (label, min) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetCountdown(min * 60 * 1000L); onDismiss() }
                                .padding(8.dp),
                            color = scheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "播完当前歌曲后停止",
                    modifier = Modifier.clickable { onSetFinishCurrent(); onDismiss() },
                    color = scheme.primary
                )
                if (active) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "取消定时",
                        modifier = Modifier.clickable { onCancel(); onDismiss() },
                        color = scheme.error
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ── 工具 ────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

// ── 歌词面板：歌词视图 + 右上角「词」设置入口 ───────────────────────────

/**
 * 歌词区域：包含 [LrcView] 与右上角的「词」设置按钮。
 *
 * 「词」按钮在歌词区域右上角以小圆角徽标形式出现，点击后弹出 [LyricsSettingsDialog]
 * 调整字号 / 行距 / 对齐 / 字重，保存后立即作用于歌词显示。
 */
@Composable
private fun LyricsPane(
    lyrics: String,
    highlightColor: Color,
    normalColor: Color,
    state: LrcViewState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        LrcView(
            lrcText = lyrics,
            highlightColor = highlightColor,
            normalColor = normalColor,
            state = state,
            modifier = Modifier.fillMaxSize()
        )
        // 「词」设置入口
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.28f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(width = 30.dp, height = 26.dp)
                .clickable(onClick = onOpenSettings)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "词",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── 歌词设置弹窗 ──────────────────────────────────────────────────────────

/**
 * 歌词显示设置弹窗：
 *
 * - 字号（滑块）
 * - 行距（滑块）
 * - 对齐：居中 / 靠左
 * - 字重：极细 / 细 / 常规 / 中等 / 粗
 *
 * 顶部带一行实时预览。点击「保存」写入 [LyricsSettingsState]（持久化 + 触发重组），
 * 对所有 [LrcView] 立即生效；点击「取消」丢弃改动。
 *
 * 大屏上限制弹窗最大宽度，避免在平板上被拉得过宽。
 */
@Composable
private fun LyricsSettingsDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val current = LyricsSettingsState.settings
    var size by remember { mutableStateOf(current.fontSizeSp) }
    var spacing by remember { mutableStateOf(current.lineSpacingDp) }
    var alignment by remember { mutableStateOf(current.alignment) }
    var weight by remember { mutableStateOf(current.fontWeight) }

    val previewAlign = if (alignment == LyricsAlignment.CENTER)
        androidx.compose.ui.text.style.TextAlign.Center
    else androidx.compose.ui.text.style.TextAlign.Start

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "歌词设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(12.dp))

                // 预览
                Surface(
                    color = scheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "这是一行歌词预览",
                            color = scheme.primary,
                            fontSize = (size + 3f).sp,
                            fontWeight = weight.bumped(),
                            textAlign = previewAlign,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "上方为高亮行预览，下方为普通行预览",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = scheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "普通行歌词预览",
                        color = scheme.onSurface.copy(alpha = 0.55f),
                        fontSize = size.sp,
                        fontWeight = weight.weight,
                        textAlign = previewAlign,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 字号
                SettingSliderRow(
                    label = "字号",
                    valueText = String.format(Locale.getDefault(), "%.0f sp", size),
                    value = size,
                    valueRange = LyricsSizeRange.MIN_FONT_SIZE..LyricsSizeRange.MAX_FONT_SIZE,
                    onValueChange = { size = it }
                )
                Spacer(Modifier.height(12.dp))

                // 行距
                SettingSliderRow(
                    label = "行距",
                    valueText = String.format(Locale.getDefault(), "%.0f dp", spacing),
                    value = spacing,
                    valueRange = LyricsSizeRange.MIN_LINE_SPACING..LyricsSizeRange.MAX_LINE_SPACING,
                    onValueChange = { spacing = it }
                )
                Spacer(Modifier.height(16.dp))

                // 对齐
                Text("对齐", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ChipOption(
                        label = "居中",
                        selected = alignment == LyricsAlignment.CENTER,
                        onClick = { alignment = LyricsAlignment.CENTER },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(12.dp))
                    ChipOption(
                        label = "靠左",
                        selected = alignment == LyricsAlignment.START,
                        onClick = { alignment = LyricsAlignment.START },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 字重
                Text("字重", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    LyricsFontWeight.values().forEachIndexed { idx, w ->
                        ChipOption(
                            label = w.label,
                            selected = weight == w,
                            onClick = { weight = w },
                            modifier = Modifier.weight(1f)
                        )
                        if (idx < LyricsFontWeight.values().size - 1) {
                            Spacer(Modifier.size(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.size(4.dp))
                    TextButton(onClick = {
                        LyricsSettingsState.update(
                            LyricsSettings(
                                fontSizeSp = size,
                                lineSpacingDp = spacing,
                                alignment = alignment,
                                fontWeight = weight
                            )
                        )
                        onDismiss()
                    }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = scheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun ChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.primary.copy(alpha = 0.15f) else scheme.surfaceVariant
    val fg = if (selected) scheme.primary else scheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)
        )
    }
}
