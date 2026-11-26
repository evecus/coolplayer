package com.coolplayer.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coolplayer.music.App
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.PlaylistEntity
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.component.LxIconButton
import com.coolplayer.music.ui.component.SongMenuDialog
import com.coolplayer.music.ui.component.TvAccent
import com.coolplayer.music.ui.component.lxFocusable
import kotlinx.coroutines.launch

/** TV 分组详情页：专辑/歌手/文件夹下的歌曲列表 */
@Composable
fun TvGroupDetailScreen(
    vm: LibraryViewModel,
    category: Int,
    groupKey: String,
    onBack: () -> Unit,
    modifier: androidx.compose.ui.Modifier = Modifier,
) {
    val allSongs by vm.allSongs.collectAsState()
    val group = remember(category, groupKey, allSongs) { vm.findGroup(category, groupKey) }
    val songs: List<SongEntry> = group?.songs.orEmpty()
    val player = PlayerConnection.player.collectAsState().value

    // ── 遥控器菜单键弹出的歌曲操作 ──
    val repo = remember { MusicRepository(App.database) }
    val scope = rememberCoroutineScope()
    val playlists: List<PlaylistEntity> by repo.observePlaylists().collectAsState(initial = emptyList())
    var menuSong by remember { mutableStateOf<SongEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group?.displayName ?: "分组",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${songs.size} 首歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LxIconButton(
                onClick = { if (songs.isNotEmpty()) player?.playPlaylist(songs.shuffled(), 0) },
                imageVector = Icons.Default.Shuffle,
                contentDescription = "随机播放",
            )
            Spacer(Modifier.width(12.dp))
            LxIconButton(
                onClick = { if (songs.isNotEmpty()) player?.playPlaylist(songs, 0) },
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放全部",
            )
        }

        Spacer(Modifier.padding(vertical = 8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(songs, key = { _, s -> s.path }) { index, song ->
                val playing = player?.let { p ->
                    p.playlist.value.getOrNull(p.currentIdx.value)?.path == song.path && p.isPlaying.value
                } ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lxFocusable(shape = RoundedCornerShape(10.dp))
                        .onPreviewKeyEvent { e ->
                            val code = e.nativeKeyEvent.keyCode
                            if (e.type == KeyEventType.KeyDown &&
                                (code == android.view.KeyEvent.KEYCODE_MENU || code == 183)
                            ) {
                                menuSong = song
                                true
                            } else false
                        }
                        .background(
                            if (playing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { player?.playPlaylist(songs, index) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title.ifBlank { song.name }, fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal, color = if (playing) TvAccent else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
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
