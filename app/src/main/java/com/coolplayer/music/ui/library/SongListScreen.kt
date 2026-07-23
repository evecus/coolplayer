package com.coolplayer.music.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.coolplayer.music.data.MusicGroupEntry
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.navigation.AppNavigator
import com.coolplayer.music.ui.theme.boundTabletWidth
import com.coolplayer.music.ui.widget.OptionDialog
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * 歌曲列表页：分类=SONG 时显示。
 *
 * 顶部：随机播放图标 + 歌曲总数、排序按钮、多选按钮。
 * 右侧：字母索引条，可拖动快速定位。
 * 点击歌曲播放并进入播放页；多选模式下可批量删除/加入歌单。
 */
@Composable
fun SongListScreen(vm: LibraryViewModel, navController: NavHostController) {
    val category by vm.currentCategory.collectAsState()
    val sortFieldValue by vm.sortField.collectAsState()
    val sortAscendingValue by vm.sortAscending.collectAsState()
    val keyword by vm.searchKeyword.collectAsState()
    val coverVersion by vm.coverVersion.collectAsState()

    // 直接订阅 SQL 排序/搜索查询的异步结果（LibraryViewModel.sortedSongsFlow），
    // 排序字段、方向、关键字变化时 ViewModel 会重新发起查询并推送新结果到这里。
    val songs by vm.sortedSongsFlow.collectAsState()

    var showSort by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val songListState = rememberLazyListState()
    val lastVisibleIndex by remember {
        derivedStateOf { songListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    var coverRequestedCount by remember(category, sortFieldValue, sortAscendingValue, keyword) { mutableStateOf(0) }
    LaunchedEffect(category, songs.isNotEmpty(), lastVisibleIndex, coverRequestedCount) {
        if (songs.isEmpty()) return@LaunchedEffect
        if (coverRequestedCount >= songs.size) return@LaunchedEffect
        if (coverRequestedCount > 0 && lastVisibleIndex >= 0 &&
            lastVisibleIndex < coverRequestedCount - 50
        ) return@LaunchedEffect
        val end = min(coverRequestedCount + 200, songs.size)
        vm.requestCovers(songs.subList(coverRequestedCount, end))
        coverRequestedCount = end
    }

    // 索引：每个字母第一次出现对应的歌曲下标
    val letterFirstIndex = remember(songs) {
        val map = linkedMapOf<String, Int>()
        songs.forEachIndexed { idx, song ->
            val key = firstIndexKeyOf(song.title)
            if (key !in map) map[key] = idx
        }
        map
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxHeight().boundTabletWidth()) {
            if (selectionMode) {
                SelectionTopRow(
                    selectedCount = selectedPaths.size,
                    totalCount = songs.size,
                    onSelectAll = {
                        if (selectedPaths.size == songs.size) {
                            selectedPaths.clear()
                        } else {
                            selectedPaths.clear()
                            selectedPaths.addAll(songs.map { it.path })
                        }
                    },
                    onClose = {
                        selectionMode = false
                        selectedPaths.clear()
                    }
                )
            } else {
                SongListHeaderRow(
                    total = songs.size,
                    onShuffle = {
                        if (songs.isNotEmpty()) {
                            PlayerConnection.player.value?.playPlaylist(songs.shuffled(), 0)
                            AppNavigator.toPlayer(navController)
                        }
                    },
                    onSort = { showSort = true },
                    onMultiSelect = { selectionMode = true }
                )
            }

            if (songs.isEmpty()) {
                EmptyLibraryPrompt(
                    modifier = Modifier.weight(1f),
                    onScanClick = { AppNavigator.toScanSettings(navController) }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = songListState,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(songs, key = { _, s -> s.path }) { index, song ->
                            SongRow(
                                song = song,
                                selectionMode = selectionMode,
                                selected = song.path in selectedPaths,
                                onToggleSelect = {
                                    if (song.path in selectedPaths) {
                                        selectedPaths.remove(song.path)
                                    } else {
                                        selectedPaths.add(song.path)
                                    }
                                },
                                onClick = {
                                    if (selectionMode) {
                                        if (song.path in selectedPaths) {
                                            selectedPaths.remove(song.path)
                                        } else {
                                            selectedPaths.add(song.path)
                                        }
                                    } else {
                                        val p = PlayerConnection.player.value
                                        p?.playPlaylist(songs, index)
                                        AppNavigator.toPlayer(navController)
                                    }
                                }
                            )
                        }
                    }

                    AlphabetIndexBar(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                        onLetterSelected = { letter ->
                            val idx = letterFirstIndex[letter]
                            if (idx != null) {
                                scope.launch { songListState.scrollToItem(idx) }
                            }
                        }
                    )
                }
            }
        }

        if (selectionMode) {
            SelectionBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter).boundTabletWidth(),
                onDelete = {
                    // TODO: 接入真实删除逻辑
                    selectedPaths.clear()
                    selectionMode = false
                },
                onAddToPlaylist = {
                    // TODO: 接入加入歌单逻辑
                },
                onPlaySelected = {
                    val toPlay = songs.filter { it.path in selectedPaths }
                    if (toPlay.isNotEmpty()) {
                        PlayerConnection.player.value?.playPlaylist(toPlay, 0)
                        AppNavigator.toPlayer(navController)
                    }
                }
            )
        }
    }

    if (showSort) {
        SongSortDialog(
            currentField = vm.sortField.value,
            currentAscending = vm.sortAscending.value,
            onConfirm = { field, ascending ->
                vm.setSort(field, ascending)
                showSort = false
            },
            onDismiss = { showSort = false }
        )
    }
}

/** 歌曲列表顶部行：随机播放 + 总数、排序、多选。 */
@Composable
private fun SongListHeaderRow(
    total: Int,
    onShuffle: () -> Unit,
    onSort: () -> Unit,
    onMultiSelect: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onShuffle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "随机播放",
                tint = scheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "$total",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Default.Sort,
            contentDescription = "排序",
            tint = scheme.onSurface,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onSort)
                .padding(9.dp)
        )
        Icon(
            imageVector = Icons.Default.Checklist,
            contentDescription = "多选",
            tint = scheme.onSurface,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onMultiSelect)
                .padding(9.dp)
        )
    }
}

/** 多选模式顶部行：全选 + 已选中数量 + 关闭。 */
@Composable
private fun SelectionTopRow(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "全选",
            color = scheme.primary,
            fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onSelectAll)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "已选中 $selectedCount 项",
            fontSize = 15.sp,
            color = scheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "关闭多选",
            tint = scheme.onSurface,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant)
                .clickable(onClick = onClose)
                .padding(7.dp)
        )
    }
}

/** 多选模式底部操作条：永久删除 / 添加到歌单 / 播放选中队列。 */
@Composable
private fun SelectionBottomBar(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlaySelected: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomBarAction(icon = Icons.Default.Delete, label = "永久删除", onClick = onDelete)
            BottomBarAction(icon = Icons.Default.PlaylistAdd, label = "添加到歌单", onClick = onAddToPlaylist)
            BottomBarAction(icon = Icons.Default.MusicNote, label = "播放选中队列", onClick = onPlaySelected)
        }
    }
}

@Composable
private fun BottomBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(4.dp))
        Text(text = label, fontSize = 11.sp, color = scheme.onSurface)
    }
}

/** 单曲行：普通模式点击播放，多选模式点击勾选。 */
@Composable
private fun SongRow(
    song: SongEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CoverOrNote(coverBytes = song.coverBytes, size = 40.dp, iconSize = 24.dp)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = song.artist.ifEmpty { "未知歌手" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        }
    }
}

/**
 * 分组列表页：分类=ALBUM/ARTIST/FOLDER 时显示。
 *
 * - 专辑：宫格封面卡片（图 6 风格）
 * - 艺术家/文件夹：带右侧字母索引条的列表（图 7、8 风格）
 */
@Composable
fun GroupListScreen(vm: LibraryViewModel, navController: NavHostController) {
    val category by vm.currentCategory.collectAsState()
    val sortGroup by vm.currentSortGroup.collectAsState()
    val keyword by vm.searchKeyword.collectAsState()
    val allSongs by vm.allSongs.collectAsState()

    val groups = remember(category, sortGroup, keyword, allSongs) {
        vm.sortedGroups()
    }

    var showSort by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共 ${groups.size} 个",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "排序",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { showSort = true }
                    .padding(9.dp)
            )
        }

        if (allSongs.isEmpty()) {
            EmptyLibraryPrompt(
                modifier = Modifier.weight(1f),
                onScanClick = { AppNavigator.toScanSettings(navController) }
            )
        } else {
            when (category) {
                MusicCategory.ALBUM -> AlbumGrid(vm = vm, groups = groups, navController = navController)
                else -> GroupListWithIndex(
                    groups = groups,
                    category = category,
                    navController = navController
                )
            }
        }
    }

    if (showSort) {
        val options = listOf("名称 A→Z", "名称 Z→A", "时间 旧→新", "时间 新→旧")
        OptionDialog(
            title = "分组排序",
            options = options,
            selected = vm.currentSortGroup.value - MusicGroupSort.NAME_ASC,
            onSelected = { vm.setSortGroup(it + MusicGroupSort.NAME_ASC) },
            onDismiss = { showSort = false }
        )
    }
}

/** 专辑宫格：2 列封面卡片，点击进入专辑详情。 */
@Composable
private fun AlbumGrid(vm: LibraryViewModel, groups: List<MusicGroupEntry>, navController: NavHostController) {
    val coverVersion by vm.coverVersion.collectAsState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // 每个专辑取"第一首有封面数据的歌曲"作为代表封面；若都还没加载过，则取第一首触发加载。
    val representativeSongs = remember(groups) {
        groups.map { g -> g.songs.firstOrNull() }
    }

    val lastVisibleIndex by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    var coverRequestedCount by remember(groups) { mutableStateOf(0) }
    LaunchedEffect(groups, representativeSongs.isNotEmpty(), lastVisibleIndex, coverRequestedCount) {
        if (representativeSongs.isEmpty()) return@LaunchedEffect
        if (coverRequestedCount >= representativeSongs.size) return@LaunchedEffect
        if (coverRequestedCount > 0 && lastVisibleIndex >= 0 &&
            lastVisibleIndex < coverRequestedCount - 40
        ) return@LaunchedEffect
        val end = min(coverRequestedCount + 120, representativeSongs.size)
        val batch = representativeSongs.subList(coverRequestedCount, end).filterNotNull()
        if (batch.isNotEmpty()) vm.requestCovers(batch)
        coverRequestedCount = end
    }

    LazyVerticalGrid(
        state = gridState,
        // 自适应列宽：手机约 2 列，平板按宽度自动排更多列
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(groups, key = { it.key }) { g ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        AppNavigator.toGroupDetail(navController, MusicCategory.ALBUM, g.key)
                    }
            ) {
                val cover = remember(g.key, coverVersion) {
                    g.songs.firstOrNull { it.coverBytes != null }?.coverBytes
                }
                AlbumCover(coverBytes = cover, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                Text(
                    text = g.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${g.songs.size} 首",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumCover(coverBytes: ByteArray?, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    if (coverBytes != null) {
        val bmp = remember(coverBytes) {
            runCatching { BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size) }.getOrNull()
        }
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            )
            return
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Album,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(40.dp)
        )
    }
}

/** 艺术家 / 文件夹列表：带右侧字母索引条。 */
@Composable
private fun GroupListWithIndex(
    groups: List<MusicGroupEntry>,
    category: Int,
    navController: NavHostController
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val letterFirstIndex = remember(groups) {
        val map = linkedMapOf<String, Int>()
        groups.forEachIndexed { idx, g ->
            val key = firstIndexKeyOf(g.displayName)
            if (key !in map) map[key] = idx
        }
        map
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(groups, key = { it.key }) { g ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppNavigator.toGroupDetail(navController, category, g.key)
                        }
                        .padding(vertical = 10.dp)
                ) {
                    GroupIcon(category = category)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = g.displayName,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${g.songs.size} 首",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AlphabetIndexBar(
            modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
            onLetterSelected = { letter ->
                val idx = letterFirstIndex[letter]
                if (idx != null) {
                    scope.launch { listState.scrollToItem(idx) }
                }
            }
        )
    }
}

@Composable
private fun GroupIcon(category: Int) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) = when (category) {
        MusicCategory.ARTIST -> Icons.Default.Mic to androidx.compose.ui.graphics.Color(0xFFF1C40F)
        MusicCategory.FOLDER -> Icons.Default.Folder to androidx.compose.ui.graphics.Color(0xFF7C4DFF)
        else -> Icons.Default.MusicNote to scheme.primary
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * 封面/音符占位组件。
 */
@Composable
fun CoverOrNote(
    coverBytes: ByteArray?,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.primary
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
                    .clip(RoundedCornerShape(6.dp))
            )
            return
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 音乐库为空时的引导卡片：提示用户点击"扫描音乐"跳转到媒体来源设置页。
 * 不再自动请求权限或自动扫描，扫描行为完全由用户在"媒体来源"页触发。
 */
@Composable
fun EmptyLibraryPrompt(modifier: Modifier = Modifier, onScanClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            color = scheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 220.dp)
                .clickable(onClick = onScanClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = scheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "扫描音乐",
                    fontSize = 17.sp,
                    color = scheme.onSurface
                )
            }
        }
    }
}

/**
 * 全屏搜索页。
 *
 * 独立路由，不复用 [LibraryViewModel.searchKeyword]（避免退出搜索后主列表
 * 仍带着搜索词过滤，或需要手动清空才能恢复的问题）。搜索关键字只是本页面
 * 内的局部状态，通过 [LibraryViewModel.searchSongs] 对 Room 音乐库表发起 SQL LIKE
 * 查询（标题/艺术家/专辑），不再对内存中的全量列表做 Kotlin filter；
 * 且封面异步加载逻辑独立维护 requestedCount（其 remember key 包含关键字本身），
 * 从根本上避免"关键字变化导致已过滤出的列表被误判为已加载过封面"的问题。
 *
 * 左上角返回箭头 -> 关闭搜索页，回到歌曲列表（不影响主列表状态）。
 */
@Composable
fun SearchScreen(vm: LibraryViewModel, navController: NavHostController, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    var keyword by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 直接对 Room 表做 SQL LIKE 查询（标题/艺术家/专辑），不再对内存里的全量列表做 Kotlin filter。
    var results by remember { mutableStateOf<List<SongEntry>>(emptyList()) }
    LaunchedEffect(keyword) {
        results = vm.searchSongs(keyword)
    }

    val listState = rememberLazyListState()
    val lastVisibleIndex by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    // key 包含 keyword：每次关键字变化都是全新的一批结果，从 0 重新计数请求封面，
    // 不会因为上次全量列表遗留的计数值而误判"已全部加载过封面"。
    var coverRequestedCount by remember(keyword) { mutableStateOf(0) }
    LaunchedEffect(keyword, results.isNotEmpty(), lastVisibleIndex, coverRequestedCount) {
        if (results.isEmpty()) return@LaunchedEffect
        if (coverRequestedCount >= results.size) return@LaunchedEffect
        if (coverRequestedCount > 0 && lastVisibleIndex >= 0 &&
            lastVisibleIndex < coverRequestedCount - 50
        ) return@LaunchedEffect
        val end = min(coverRequestedCount + 200, results.size)
        vm.requestCovers(results.subList(coverRequestedCount, end))
        coverRequestedCount = end
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(scheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxHeight().boundTabletWidth().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = scheme.onSurface,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onBack)
                        .padding(12.dp)
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("搜索歌曲 / 歌手 / 专辑") },
                    singleLine = true,
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                modifier = Modifier.clickable { keyword = "" }
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .focusRequester(focusRequester)
                )
            }

            when {
                keyword.isBlank() -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text = "输入关键字搜索本地音乐",
                            color = scheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 60.dp)
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text = "未找到相关歌曲",
                            color = scheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 60.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    ) {
                        itemsIndexed(results, key = { _, s -> s.path }) { index, song ->
                            SongRow(
                                song = song,
                                selectionMode = false,
                                selected = false,
                                onToggleSelect = {},
                                onClick = {
                                    val p = PlayerConnection.player.value
                                    p?.playPlaylist(results, index)
                                    AppNavigator.toPlayer(navController)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 歌曲排序弹窗：上方"升序 / 降序"单选，下方"排序依据"单选，底部确定 / 取消。
 *
 * 用户在弹窗内切换选项不会立即生效，只有点击"确定"后才调用 [onConfirm]；
 * 点击"取消"或弹窗外关闭则丢弃改动，恢复到打开弹窗前的排序。
 */
@Composable
private fun SongSortDialog(
    currentField: Int,
    currentAscending: Boolean,
    onConfirm: (field: Int, ascending: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var ascending by remember { mutableStateOf(currentAscending) }
    var field by remember { mutableStateOf(currentField) }

    val fieldLabels = listOf(
        SongSortField.TITLE to "标题",
        SongSortField.MODIFIED_TIME to "修改时间",
        SongSortField.FILE_NAME to "文件",
        SongSortField.ALBUM to "专辑",
        SongSortField.ARTIST to "艺术家",
        SongSortField.SIZE to "大小",
        SongSortField.DURATION to "时长",
        SongSortField.PLAY_COUNT to "播放次数"
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "歌曲排序",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                // 模块一：升序 / 降序
                Row(modifier = Modifier.fillMaxWidth()) {
                    SortDirectionOption(
                        label = "升序",
                        selected = ascending,
                        onClick = { ascending = true },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(12.dp))
                    SortDirectionOption(
                        label = "降序",
                        selected = !ascending,
                        onClick = { ascending = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 模块二：排序依据
                Column {
                    fieldLabels.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { field = value }
                                .padding(vertical = 10.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = field == value,
                                onClick = { field = value }
                            )
                            Text(text = label, color = scheme.onSurface, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.size(4.dp))
                    androidx.compose.material3.TextButton(onClick = { onConfirm(field, ascending) }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun SortDirectionOption(
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
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
    }
}
