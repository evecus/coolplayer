package com.coolplayer.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.navigation.AppNavigator

/**
 * 分组详情页：从专辑/艺术家/文件夹列表点进来，展示该分组下全部歌曲。
 *
 * [category] 对应 [MusicCategory]，[groupKey] 对应 [com.coolplayer.music.data.MusicGroupEntry.key]。
 */
@Composable
fun GroupDetailScreen(
    category: Int,
    groupKey: String,
    navController: NavHostController,
    onBack: () -> Unit
) {
    val vm: LibraryViewModel = viewModel()
    val allSongs by vm.allSongs.collectAsState()
    val coverVersion by vm.coverVersion.collectAsState()

    val group = remember(category, groupKey, allSongs) {
        vm.findGroup(category, groupKey)
    }
    val songs = group?.songs.orEmpty()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = group?.displayName ?: "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                                AppNavigator.toPlayer(navController)
                            }
                        }
                        .padding(8.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs, key = { _, s -> s.path }) { i, song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlayerConnection.player.value?.playPlaylist(songs, i)
                                AppNavigator.toPlayer(navController)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        CoverOrNote(coverBytes = song.coverBytes, size = 40.dp, iconSize = 22.dp)
                        Spacer(Modifier.size(12.dp))
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

    // 分组详情页封面加载
    androidx.compose.runtime.LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            vm.requestCovers(songs)
        }
    }
}
