package com.coolplayer.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.coolplayer.music.App
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.toSongEntry
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.navigation.AppNavigator
import com.coolplayer.music.ui.theme.boundTabletWidth
import kotlinx.coroutines.launch

/**
 * 歌单列表页：展示用户自建歌单，右上角"..."弹出新建歌单对话框。
 */
@Composable
fun PlaylistScreen(
    navController: NavHostController,
    onBack: () -> Unit
) {
    val repo = remember { MusicRepository(AppDatabase.get(App.context)) }
    val playlists by repo.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxHeight().boundTabletWidth().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                )
                Text(
                    text = "歌单",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { showMenu = true }
                            .padding(8.dp)
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("新建歌单") },
                            onClick = {
                                showMenu = false
                                showCreate = true
                            }
                        )
                    }
                }
            }

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists) { p ->
                        val songCount by repo.observePlaylistSongs(p.id)
                            .collectAsState(initial = emptyList())
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { AppNavigator.toPlaylistDetail(navController, p.id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${songCount.size} 首",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("在此输入歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            scope.launch { repo.createPlaylist(name.trim()) }
                            showCreate = false
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            }
        )
    }
}

// ── 歌单详情页 ──────────────────────────────────────────────────────────

/**
 * 歌单详情：
 *
 * - [playlistId] = -1L 表示收藏
 * - [playlistId] = -2L 表示最近播放
 * - 其他：自建歌单
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit
) {
    val repo = remember { MusicRepository(AppDatabase.get(App.context)) }
    val scope = rememberCoroutineScope()

    val title: String
    val songs: List<com.coolplayer.music.data.SongEntry>
    when (playlistId) {
        -1L -> {
            val favs by repo.observeFavorites().collectAsState(initial = emptyList())
            title = "我的收藏"
            songs = favs.map { it.toSongEntry() }
        }
        -2L -> {
            val recents by repo.observeRecent(200).collectAsState(initial = emptyList())
            title = "最近播放"
            songs = recents.map { it.toSongEntry() }
        }
        else -> {
            val list by repo.observePlaylistSongs(playlistId).collectAsState(initial = emptyList())
            val allPlaylists by repo.observePlaylists().collectAsState(initial = emptyList())
            title = allPlaylists.firstOrNull { it.id == playlistId }?.name ?: "歌单"
            songs = list.map { it.toSongEntry() }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxHeight().boundTabletWidth().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "共 ${songs.size} 首",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "播放全部",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            if (songs.isNotEmpty()) {
                                PlayerConnection.player.value?.playPlaylist(songs, 0)
                            }
                        }
                        .padding(8.dp)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs) { i, song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlayerConnection.player.value?.playPlaylist(songs, i)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${i + 1}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(width = 28.dp, height = 20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist.ifEmpty { "未知歌手" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
