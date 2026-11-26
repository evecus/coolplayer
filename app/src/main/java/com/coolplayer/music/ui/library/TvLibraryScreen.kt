package com.coolplayer.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coolplayer.music.App
import com.coolplayer.music.data.MusicGroupEntry
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.PlaylistEntity
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.component.LxIconButton
import com.coolplayer.music.ui.component.SongMenuDialog
import com.coolplayer.music.ui.component.TvAccent
import com.coolplayer.music.ui.component.lxFocusable
import kotlinx.coroutines.launch

/**
 * TV 音乐库浏览页。
 *
 * - SONG 分类：歌曲列表（带封面/歌手/时长）
 * - ALBUM/ARTIST/FOLDER：分组网格（专辑封面墙/歌手/文件夹），点击进入分组歌曲列表
 * 全部条目带焦点框，方向键可达，OK 键播放。
 */
@Composable
fun TvLibraryScreen(
    vm: LibraryViewModel,
    onOpenGroup: (category: Int, key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category by vm.currentCategory.collectAsState()
    val songs by vm.sortedSongsFlow.collectAsState()
    val allSongs by vm.allSongs.collectAsState()

    // ── 遥控器菜单键弹出的歌曲操作 ──
    val repo = remember { MusicRepository(App.database) }
    val scope = rememberCoroutineScope()
    val playlists: List<PlaylistEntity> by repo.observePlaylists().collectAsState(initial = emptyList())
    var menuSong by remember { mutableStateOf<SongEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (category) {
                    MusicCategory.SONG -> "全部歌曲"
                    MusicCategory.ALBUM -> "专辑"
                    MusicCategory.ARTIST -> "艺术家"
                    MusicCategory.FOLDER -> "文件夹"
                    else -> "音乐库"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val count = if (category == MusicCategory.SONG) songs.size else vm.buildGroups().size
            Text(
                "$count 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            LxIconButton(
                onClick = { if (songs.isNotEmpty()) PlayerConnection.player.value?.playPlaylist(songs.shuffled(), 0) },
                imageVector = Icons.Default.Shuffle,
                contentDescription = "随机播放全部",
            )
        }

        Spacer(Modifier.padding(vertical = 6.dp))

        when (category) {
            MusicCategory.SONG -> {
                if (songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无歌曲，请先在「扫描音乐」中扫描本地音频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    SongColumn(songs = songs, onMenuClick = { menuSong = it })
                }
            }
            else -> {
                val groups = remember(allSongs, category) { vm.buildGroupsFor(category) }
                GroupGrid(groups = groups, category = category, onOpenGroup = onOpenGroup)
            }
        }
    }

    // ── 歌曲菜单弹窗 ──
    menuSong?.let { song ->
        SongMenuDialog(
            song = song,
            playlists = playlists,
            onPlayNext = { PlayerConnection.player.value?.playNext(song) },
            onAddToPlaylist = { id -> scope.launch { repo.addToPlaylist(id, song) } },
            onHide = { vm.hideSong(song.path) },
            onDismiss = { menuSong = null },
        )
    }
}

@Composable
private fun SongColumn(songs: List<SongEntry>, onMenuClick: (SongEntry) -> Unit) {
    val player = PlayerConnection.player.collectAsState().value
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.path }) { index, song ->
            val playing = player?.let { p ->
                p.playlist.value.getOrNull(p.currentIdx.value)?.path == song.path && p.isPlaying.value
            } ?: false
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .lxFocusable(shape = RoundedCornerShape(10.dp))
                    // 遥控器菜单键（MENU=82 / CONTEXT_MENU=183）弹出歌曲操作
                    .onPreviewKeyEvent { e ->
                        val code = e.nativeKeyEvent.keyCode
                        if (e.type == KeyEventType.KeyDown &&
                            (code == android.view.KeyEvent.KEYCODE_MENU || code == 183)
                        ) {
                            onMenuClick(song)
                            true
                        } else false
                    }
                    .clickable { player?.playPlaylist(songs, index) }
                    .background(
                        if (playing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongThumb(song = song)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title.ifBlank { song.name },
                        fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                        color = if (playing) TvAccent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${song.artist.ifBlank { "未知歌手" }} · ${song.album.ifBlank { "未知专辑" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formatDuration(song.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SongThumb(song: SongEntry) {
    val bitmap = remember(song.path, song.coverBytes) {
        song.coverBytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupGrid(
    groups: List<MusicGroupEntry>,
    category: Int,
    onOpenGroup: (Int, String) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无内容，请先扫描音乐", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(groups, key = { _, g -> g.key }) { _, group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .lxFocusable(shape = RoundedCornerShape(12.dp))
                    .clickable { onOpenGroup(category, group.key) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GroupCover(group = group, category = category)
                Spacer(Modifier.padding(vertical = 4.dp))
                Text(
                    group.displayName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${group.songs.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupCover(group: MusicGroupEntry, category: Int) {
    val coverSong = group.songs.firstOrNull()
    val bitmap = remember(group.key, coverSong?.coverBytes) {
        coverSong?.coverBytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val icon: ImageVector = when (category) {
                MusicCategory.ARTIST -> Icons.Default.Mic
                MusicCategory.FOLDER -> Icons.Default.Folder
                else -> Icons.Default.Album
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
