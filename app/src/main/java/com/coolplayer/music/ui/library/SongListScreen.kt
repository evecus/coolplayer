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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    val sortSong by vm.currentSortSong.collectAsState()
    val keyword by vm.searchKeyword.collectAsState()
    val allSongs by vm.allSongs.collectAsState()
    val coverVersion by vm.coverVersion.collectAsState()

    val songs = remember(category, sortSong, keyword, allSongs, coverVersion) {
        vm.sortedSongs()
    }

    var showSort by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val songListState = rememberLazyListState()
    val lastVisibleIndex by remember {
        derivedStateOf { songListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    var coverRequestedCount by remember(category, sortSong) { mutableStateOf(0) }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                    onLetterSelected = { letter ->
                        val idx = letterFirstIndex[letter]
                        if (idx != null) {
                            scope.launch { songListState.scrollToItem(idx) }
                        }
                    }
                )
            }
        }

        if (selectionMode) {
            SelectionBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
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
        val options = listOf(
            "标题 A→Z", "标题 Z→A", "歌手 A→Z", "歌手 Z→A", "时间 旧→新", "时间 新→旧"
        )
        OptionDialog(
            title = "歌曲排序",
            options = options,
            selected = vm.currentSortSong.value,
            onSelected = { vm.setSortSong(it) },
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

        when (category) {
            MusicCategory.ALBUM -> AlbumGrid(groups = groups, navController = navController)
            else -> GroupListWithIndex(
                groups = groups,
                category = category,
                navController = navController
            )
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
private fun AlbumGrid(groups: List<MusicGroupEntry>, navController: NavHostController) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.key }) { g ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        AppNavigator.toGroupDetail(navController, MusicCategory.ALBUM, g.key)
                    }
            ) {
                val cover = g.songs.firstOrNull { it.coverBytes != null }?.coverBytes
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
            modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
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
