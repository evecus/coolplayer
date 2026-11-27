package com.coolplayer.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coolplayer.music.App
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.PlaylistEntity
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.data.toSongEntry
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.component.LxIconButton
import com.coolplayer.music.ui.component.TvAccent
import com.coolplayer.music.ui.component.TvTextDark
import com.coolplayer.music.ui.component.TvFocusSurface
import com.coolplayer.music.ui.component.lxFocusRing
import com.coolplayer.music.ui.component.lxFocusable
import kotlinx.coroutines.launch

/** 收藏虚拟项的特殊 id */
private const val FAVORITES_ID = -1L

/**
 * TV 歌单页：左侧歌单列表 + 右侧歌单歌曲详情，全部可遥控器聚焦。
 */
@Composable
fun TvPlaylistScreen(
    allSongs: List<SongEntry> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val repo = remember { MusicRepository(App.database) }
    // path -> 完整歌曲（含封面缩略图 coverBytes 与时长），来自共享曲库。
    // 歌单/收藏表只存元数据，不存封面 BLOB，这里按 path 回填。
    val fullByPath = remember(allSongs) { allSongs.associateBy { it.path } }
    // SongEntry.durationMs 是不可变 val，无法回填；单独存时长供列表显示。
    val durationByPath = remember(allSongs) { allSongs.associate { it.path to it.durationMs } }
    val playlists: List<PlaylistEntity> by repo.observePlaylists().collectAsState(initial = emptyList())
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 收藏（虚拟项，selectedId = FAVORITES_ID）
    val favorites: List<com.coolplayer.music.data.FavoriteEntity> by repo.observeFavorites().collectAsState(initial = emptyList())
    val favEntries: List<SongEntry> = remember(favorites) { favorites.map { it.toSongEntry() } }

    // 当前选中的歌单（默认第一个）
    val effectiveId = selectedId ?: playlists.firstOrNull()?.id
    val songs: List<com.coolplayer.music.data.PlaylistSongEntity> by remember(effectiveId) {
        if (effectiveId != null && effectiveId != FAVORITES_ID) repo.observePlaylistSongs(effectiveId)
        else kotlinx.coroutines.flow.flowOf(emptyList<com.coolplayer.music.data.PlaylistSongEntity>())
    }.collectAsState(initial = emptyList())
    // 歌单/收藏实体本身不含封面 BLOB 与时长，按 path 从共享曲库回填。
    fun SongEntry.enrich(): SongEntry {
        val full = fullByPath[path]
        if (full != null) {
            coverBytes = full.coverBytes
            if (title.isBlank()) title = full.title
            if (artist.isBlank()) artist = full.artist
            if (album.isBlank()) album = full.album
        }
        return this
    }
    val playlistEntries: List<SongEntry> = remember(songs, fullByPath) { songs.map { it.toSongEntry().enrich() } }
    val enrichedFavEntries: List<SongEntry> = remember(favorites, fullByPath) { favEntries.map { it.enrich() } }
    val songEntries: List<SongEntry> = if (selectedId == FAVORITES_ID) enrichedFavEntries else playlistEntries

    Row(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // ── 左：歌单列表 ──
        Column(modifier = Modifier.width(280.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("我的歌单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                LxIconButton(onClick = { showCreate = true }, imageVector = Icons.Default.Add, contentDescription = "新建歌单", compact = true)
            }
            Spacer(Modifier.padding(vertical = 4.dp))
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 收藏（虚拟项）
                item {
                    NavigationItem(
                        icon = Icons.Default.Favorite,
                        label = "我的收藏",
                        selected = selectedId == FAVORITES_ID,
                        onClick = { selectedId = FAVORITES_ID },
                    )
                }
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                    NavigationItem(
                        icon = Icons.Default.MusicNote,
                        label = p.name.ifBlank { "未命名歌单" },
                        selected = effectiveId == p.id && selectedId != FAVORITES_ID,
                        onClick = { selectedId = p.id },
                        onDelete = { scope.launch { repo.deletePlaylist(p.id); if (selectedId == p.id) selectedId = null } },
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // ── 右：歌单歌曲 ──
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            val title = if (selectedId == FAVORITES_ID) "我的收藏" else playlists.find { it.id == effectiveId }?.name?.ifBlank { "未命名歌单" } ?: "歌单"
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${songEntries.size} 首歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(vertical = 6.dp))
            if (songEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("歌单暂无歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(songEntries, key = { _, s -> s.path }) { index, song ->
                        PlaylistSongRow(
                            song = song,
                            index = index,
                            durationMs = durationByPath[song.path] ?: 0L,
                            onClick = { PlayerConnection.player.value?.playPlaylist(songEntries, index) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单", color = TvTextDark) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("歌单名称", color = TvTextDark) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TvTextDark),
                    modifier = Modifier.lxFocusRing(shape = RoundedCornerShape(8.dp)),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch { val id = repo.createPlaylist(newName.trim()); selectedId = id }
                        newName = ""
                        showCreate = false
                    }
                }, modifier = Modifier.lxFocusRing()) { Text("确定", color = TvTextDark) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }, modifier = Modifier.lxFocusRing()) { Text("取消", color = TvTextDark) } },
        )
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        focusedScale = 1.03f,
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) TvAccent else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TvAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDelete != null) {
            LxIconButton(onClick = onDelete, imageVector = Icons.Default.Delete, contentDescription = "删除歌单", tint = MaterialTheme.colorScheme.error, compact = true)
        }
    }
    }
}

@Composable
private fun PlaylistSongRow(song: SongEntry, index: Int, durationMs: Long, onClick: () -> Unit) {
    val player = PlayerConnection.player.collectAsState().value
    val playing = player?.let { p ->
        p.playlist.value.getOrNull(p.currentIdx.value)?.path == song.path && p.isPlaying.value
    } ?: false
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        containerColor = if (playing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surface,
        focusedScale = 1.02f,
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
            formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

/** 歌单歌曲封面缩略图，与音乐库列表一致 */
@Composable
private fun SongThumb(song: SongEntry) {
    val bitmap = remember(song.path, song.coverBytes) {
        song.coverBytes?.let {
            runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
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

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    return String.format("%02d:%02d", total / 60, total % 60)
}
