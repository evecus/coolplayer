package com.coolplayer.music.ui.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.coolplayer.music.data.PermissionUtil
import com.coolplayer.music.player.MusicPlayer
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.library.GroupListScreen
import com.coolplayer.music.ui.library.LibraryViewModel
import com.coolplayer.music.ui.library.MusicCategory
import com.coolplayer.music.ui.library.SongListScreen
import com.coolplayer.music.ui.navigation.AppNavigator
import com.coolplayer.music.ui.widget.LoadingView
import kotlinx.coroutines.launch

/**
 * Cool Player 首页：
 *
 * - 左侧抽屉导航（点击左上角面包屑图标滑出）：歌曲/专辑/艺术家/文件夹分类 + 扫描音乐/设置入口
 * - 顶部：面包屑菜单 + 当前分类标题 + 搜索
 * - 中间：当前分类内容（歌曲列表 / 分组网格 or 列表）
 * - 底部：迷你播放栏（点击进入播放页）
 *
 * 启动时绑定 PlaybackService 获取 [MusicPlayer]。
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel()

    LaunchedEffect(Unit) {
        PlayerConnection.bind(context)
        vm.init()
    }

    // 首次进入请求媒体权限
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it } || PermissionUtil.hasAudioPermission(context)
        vm.onPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        if (!PermissionUtil.hasAudioPermission(context)) {
            permLauncher.launch(PermissionUtil.audioPermissions())
        }
    }

    val playerState by PlayerConnection.state.collectAsState()
    val player = playerState
    val hasPermission by vm.hasPermission.collectAsState()
    val permissionRequested by vm.permissionRequested.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                HomeDrawerContent(
                    vm = vm,
                    onCategorySelected = { cat ->
                        vm.setCategory(cat)
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        AppNavigator.toSettings(navController)
                    },
                    player = player
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeTopBar(
                    vm = vm,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        !hasPermission && permissionRequested -> {
                            PermissionDeniedView {
                                permLauncher.launch(PermissionUtil.audioPermissions())
                            }
                        }
                        !hasPermission -> LoadingView("准备中…")
                        else -> HomeContent(vm = vm, navController = navController)
                    }
                }
                // 底部迷你播放栏
                MiniPlaybackBar(player = player, onClick = { AppNavigator.toPlayer(navController) })
            }
        }
    }
}

/**
 * 左侧抽屉导航内容：分类入口 + 扫描音乐/设置。
 */
@Composable
private fun HomeDrawerContent(
    vm: LibraryViewModel,
    onCategorySelected: (Int) -> Unit,
    onSettings: () -> Unit,
    player: MusicPlayer?
) {
    val scheme = MaterialTheme.colorScheme
    val category by vm.currentCategory.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.size(12.dp))
        Text(
            text = "Cool Player",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        DrawerItem(
            icon = Icons.Default.MusicNote,
            label = "歌曲",
            selected = category == MusicCategory.SONG,
            iconTint = androidx.compose.ui.graphics.Color(0xFF2ECC71),
            onClick = { onCategorySelected(MusicCategory.SONG) }
        )
        DrawerItem(
            icon = Icons.Default.Album,
            label = "专辑",
            selected = category == MusicCategory.ALBUM,
            iconTint = androidx.compose.ui.graphics.Color(0xFFE74C3C),
            onClick = { onCategorySelected(MusicCategory.ALBUM) }
        )
        DrawerItem(
            icon = Icons.Default.Mic,
            label = "艺术家",
            selected = category == MusicCategory.ARTIST,
            iconTint = androidx.compose.ui.graphics.Color(0xFFF1C40F),
            onClick = { onCategorySelected(MusicCategory.ARTIST) }
        )
        DrawerItem(
            icon = Icons.Default.Folder,
            label = "文件夹",
            selected = category == MusicCategory.FOLDER,
            iconTint = androidx.compose.ui.graphics.Color(0xFF7C4DFF),
            onClick = { onCategorySelected(MusicCategory.FOLDER) }
        )

        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
        )

        DrawerItem(
            icon = Icons.Default.LibraryMusic,
            label = "扫描音乐",
            selected = false,
            iconTint = androidx.compose.ui.graphics.Color(0xFF3498DB),
            onClick = { /* 暂不实现 */ }
        )
        DrawerItem(
            icon = Icons.Default.Settings,
            label = "设置",
            selected = false,
            iconTint = androidx.compose.ui.graphics.Color(0xFF2ECC71),
            onClick = onSettings
        )

        Spacer(Modifier.weight(1f))

        // 底部当前播放条（可选展示）
        if (player != null) {
            val title = player.title.collectAsState().value
            val artist = player.artist.collectAsState().value
            val hasContent = player.hasContent.collectAsState().value
            if (hasContent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    PlaceholderCover()
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifEmpty { "未在播放" },
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = scheme.onSurface
                        )
                        Text(
                            text = artist,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurface
        )
    }
}

@Composable
private fun HomeTopBar(
    vm: LibraryViewModel,
    onMenuClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val category by vm.currentCategory.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    val title = when (category) {
        MusicCategory.SONG -> "歌曲"
        MusicCategory.ALBUM -> "专辑"
        MusicCategory.ARTIST -> "艺术家"
        MusicCategory.FOLDER -> "文件夹"
        else -> "歌曲"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "菜单",
            tint = scheme.onSurface,
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onMenuClick)
                .padding(12.dp)
        )
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "搜索",
            tint = scheme.onSurface,
            modifier = Modifier
                .size(48.dp)
                .clickable { showSearch = true }
                .padding(12.dp)
        )
    }

    if (showSearch) {
        com.coolplayer.music.ui.widget.SearchDialog(
            title = "搜索",
            hintText = "搜索歌曲 / 歌手 / 专辑",
            initialText = vm.searchKeyword.value,
            onChanged = { vm.setSearchKeyword(it) },
            onDismiss = { showSearch = false }
        )
    }
}

@Composable
private fun HomeContent(vm: LibraryViewModel, navController: NavHostController) {
    val category by vm.currentCategory.collectAsState()
    when (category) {
        MusicCategory.SONG -> {
            SongListScreen(vm = vm, navController = navController)
        }
        else -> {
            GroupListScreen(vm = vm, navController = navController)
        }
    }
}

@Composable
private fun PermissionDeniedView(onGrant: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "需要音频权限才能扫描本地音乐",
                color = scheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onGrant) { Text("授予权限") }
        }
    }
}

// ── 迷你播放栏 ───────────────────────────────────────────────────────────

@Composable
private fun MiniPlaybackBar(player: MusicPlayer?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val title = player?.title?.collectAsState()?.value ?: ""
    val artist = player?.artist?.collectAsState()?.value ?: ""
    val isPlaying = player?.isPlaying?.collectAsState()?.value ?: false
    val coverBytes = player?.coverBytes?.collectAsState()?.value
    val hasContent = player?.hasContent?.collectAsState()?.value ?: false

    AnimatedVisibility(visible = hasContent) {
        Surface(
            color = scheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // 封面
                val cb = coverBytes
                if (cb != null) {
                    val bmp = remember(cb) {
                        runCatching {
                            BitmapFactory.decodeByteArray(cb, 0, cb.size)
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        PlaceholderCover()
                    }
                } else {
                    PlaceholderCover()
                }
                Spacer(Modifier.size(10.dp))
                // 标题/歌手
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifEmpty { "未在播放" },
                        color = scheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.size(8.dp))
                // 播放/暂停按钮
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = scheme.onSurface,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { player?.togglePlay() }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaceholderCover() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.QueueMusic,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}
