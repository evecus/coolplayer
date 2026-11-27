package com.coolplayer.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.PinyinUtil
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.data.toSongEntry
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.component.TvTextDark
import com.coolplayer.music.ui.component.TvFocusSurface
import com.coolplayer.music.ui.component.requestFocusWhenReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SearchStage { DISCOVER, TYPING, RESULT }

/**
 * TV 搜索页 —— FMusic 屏上键盘布局 + 本地音乐库搜索。
 *
 * - 左栏：搜索框 + 屏上键盘（26 字母 + 数字 + 删除/清空 + 搜索/取消）
 * - 中栏：未搜索显示「热门歌手 + 搜索历史」胶囊；输入中显示「搜索建议 + 搜索历史」
 * - 右栏：搜索结果列表（歌名/歌手/专辑），搜索完成焦点自动落到第一条
 * 所有数据源均来自 coolplayer 本地 Room 库；关键词支持标题/歌手/专辑 + 拼音匹配。
 */
@Composable
fun TvSearchScreen(
    allSongs: List<SongEntry>,
    modifier: Modifier = Modifier,
) {
    val store = remember { TvSearchStore() }
    val state by store.uiState.collectAsState()
    val player = PlayerConnection.player.collectAsState().value
    val scope = rememberCoroutineScope()

    // 热门歌手：从本地库统计出现次数最多的艺术家
    val hotSingers = remember(allSongs) {
        allSongs.mapNotNull { it.artist.takeIf { a -> a.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(12).map { it.key }
    }

    val stage = when {
        state.keyword.isBlank() -> SearchStage.DISCOVER
        state.submitted && state.keyword == state.searchText -> SearchStage.RESULT
        else -> SearchStage.TYPING
    }

    // 输入变化时生成拼音建议
    LaunchedEffect(state.keyword) {
        store.updateTips(allSongs)
    }
    // 提交搜索时执行本地查询
    LaunchedEffect(state.searchText, state.searchSeq) {
        if (state.searchText.isNotBlank()) store.doSearch(allSongs)
    }

    val firstResultFocus = remember { FocusRequester() }
    LaunchedEffect(state.results.isEmpty(), stage) {
        if (stage == SearchStage.RESULT && state.results.isNotEmpty()) {
            runCatching { firstResultFocus.requestFocusWhenReady() }
        }
    }

    // 加载历史
    LaunchedEffect(Unit) { store.loadHistory() }

    Row(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // ── 左栏：搜索框 + 屏上键盘 ──
        Column(
            modifier = Modifier.width(440.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchInputBox(keyword = state.keyword, onClear = { store.clearInput() })
            TvKeyboard(
                onAppendChar = { store.appendChar(it) },
                onBackspace = { store.backspace() },
                onClear = { store.clearInput() },
                onSearch = { store.submit() },
            )
        }

        // ── 中栏：热搜/建议 + 历史 ──
        if (stage != SearchStage.RESULT) {
            Row(modifier = Modifier.weight(0.85f).fillMaxHeight()) {
                TagColumn(
                    title = if (stage == SearchStage.TYPING) "搜索建议" else "热门搜索",
                    emptyHint = if (stage == SearchStage.TYPING) "输入字母获取建议" else "暂无热门搜索",
                    words = if (stage == SearchStage.TYPING) state.tips else hotSingers,
                    onSelect = { store.searchWord(it) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                TagColumn(
                    title = "搜索历史",
                    emptyHint = "暂无搜索历史",
                    words = state.history,
                    onSelect = { store.searchWord(it) },
                    onClear = { store.clearHistory() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // ── 右栏：结果 ──
        Box(modifier = if (stage == SearchStage.RESULT) Modifier.weight(1.15f).fillMaxHeight() else Modifier.width(0.dp)) {
            if (stage == SearchStage.RESULT) {
                if (state.searching) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (state.results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到相关歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                        itemsIndexed(state.results) { index, song ->
                            SearchResultRow(
                                song = song,
                                index = index,
                                isActive = player?.let { p ->
                                    p.playlist.value.getOrNull(p.currentIdx.value)?.path == song.path && p.isPlaying.value
                                } ?: false,
                                focusRequester = if (index == 0) firstResultFocus else null,
                                onClick = {
                                    scope.launch {
                                        val list = state.results
                                        withContext(Dispatchers.Main) { player?.playPlaylist(list, index) }
                                    }
                                    store.addHistory(state.searchText)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInputBox(keyword: String, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (keyword.isEmpty()) {
            Text("使用屏上键盘输入关键词", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        } else {
            Text(keyword, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagColumn(
    title: String,
    emptyHint: String,
    words: List<String>,
    onSelect: (String) -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onClear != null && words.isNotEmpty()) {
                Text(
                    "清空",
                    fontSize = 13.sp,
                    color = TvTextDark,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(6.dp),
                )
            }
        }
        if (words.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(emptyHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                words.forEach { word ->
                    TvFocusSurface(
                        onClick = { onSelect(word) },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        focusedScale = 1.08f,
                    ) {
                        Text(
                            word, fontSize = 15.sp, color = TvTextDark, maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    song: SongEntry,
    index: Int,
    isActive: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title.ifBlank { song.name },
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            Text(
                "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (isActive) Icon(Icons.Default.PlayArrow, contentDescription = "正在播放", tint = MaterialTheme.colorScheme.primary)
    }
    }
}

/** TV 屏上键盘：6 列圆角按键，全部可聚焦 */
@Composable
private fun TvKeyboard(
    onAppendChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val letters = ('a'..'z').map { it.toString() } + (0..9).map { it.toString() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        letters.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { ch -> KeyboardKey(ch.uppercase(), { onAppendChar(ch) }, Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyboardKey("删除", onBackspace, Modifier.weight(1f), outlined = true)
            KeyboardKey("清空", onClear, Modifier.weight(1f), outlined = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyboardKey("搜索", onSearch, Modifier.weight(1f), accent = true)
            KeyboardKey("取消", onClear, Modifier.weight(1f), outlined = true)
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    accent: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        // 搜索键不再加蓝色边框，保留浅紫底色 + 加粗文字来突出
        accent -> Color.Transparent
        outlined -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = shape,
        containerColor = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        focusedScale = 1.1f,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(if (borderColor == Color.Transparent) 0.dp else 1.dp, borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (accent) TvTextDark else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── 搜索状态与逻辑 ──────────────────────────────────────────────

private data class TvSearchUiState(
    val keyword: String = "",
    val searchText: String = "",
    val submitted: Boolean = false,
    val searchSeq: Int = 0,
    val tips: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val results: List<SongEntry> = emptyList(),
    val searching: Boolean = false,
)

private class TvSearchStore {
    private val _uiState = MutableStateFlow(TvSearchUiState())
    val uiState: StateFlow<TvSearchUiState> = _uiState.asStateFlow()

    fun appendChar(ch: String) {
        _uiState.value = _uiState.value.copy(keyword = _uiState.value.keyword + ch, submitted = false)
    }

    fun backspace() {
        _uiState.value = _uiState.value.copy(keyword = _uiState.value.keyword.dropLast(1), submitted = false)
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(keyword = "", submitted = false, tips = emptyList())
    }

    fun submit() {
        val kw = _uiState.value.keyword.trim()
        if (kw.isBlank()) return
        _uiState.value = _uiState.value.copy(
            searchText = kw,
            submitted = true,
            searchSeq = _uiState.value.searchSeq + 1,
            searching = true,
        )
    }

    fun searchWord(word: String) {
        _uiState.value = _uiState.value.copy(
            keyword = word,
            searchText = word,
            submitted = true,
            searchSeq = _uiState.value.searchSeq + 1,
            searching = true,
        )
    }

    /** 拼音/文字匹配生成建议词（歌手 + 标题） */
    fun updateTips(allSongs: List<SongEntry>) {
        val kw = _uiState.value.keyword.trim()
        if (kw.isBlank()) {
            _uiState.value = _uiState.value.copy(tips = emptyList())
            return
        }
        val key = PinyinUtil.toSortKey(kw)
        val candidates = (allSongs.mapNotNull { it.artist.takeIf { a -> a.isNotBlank() } } +
            allSongs.mapNotNull { it.title.takeIf { t -> t.isNotBlank() } }).distinct()
        val tips = candidates.filter { PinyinUtil.toSortKey(it).contains(key, ignoreCase = true) }.take(10)
        _uiState.value = _uiState.value.copy(tips = tips)
    }

    fun doSearch(allSongs: List<SongEntry>) {
        CoroutineScope(Dispatchers.Default).launch {
            val kw = _uiState.value.searchText.trim()
            val key = PinyinUtil.toSortKey(kw)
            val results = allSongs.filter { song ->
                val text = listOf(song.title, song.name, song.artist, song.album).joinToString(" ")
                text.contains(kw, ignoreCase = true) || PinyinUtil.toSortKey(text).contains(key)
            }
            _uiState.value = _uiState.value.copy(results = results, searching = false)
        }
    }

    fun loadHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = com.coolplayer.music.data.StorageService
            val json = prefs.getJsonArray("tv_search_history", "[]")
            val list = runCatching {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(history = list)
        }
    }

    fun addHistory(word: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = com.coolplayer.music.data.StorageService
            val current = _uiState.value.history
            val updated = (listOf(word) + current.filter { it != word }).take(20)
            val arr = org.json.JSONArray()
            updated.forEach { arr.put(it) }
            prefs.setJsonArray("tv_search_history", arr.toString())
            _uiState.value = _uiState.value.copy(history = updated)
        }
    }

    fun clearHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            com.coolplayer.music.data.StorageService.setJsonArray("tv_search_history", "[]")
            _uiState.value = _uiState.value.copy(history = emptyList())
        }
    }
}
