package com.coolplayer.music.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coolplayer.music.App
import com.coolplayer.music.data.ArtistSplitter
import com.coolplayer.music.data.AudioMetadataReader
import com.coolplayer.music.data.FolderBlacklist
import com.coolplayer.music.data.LocalScanner
import com.coolplayer.music.data.MusicGroupEntry
import com.coolplayer.music.data.PermissionUtil
import com.coolplayer.music.data.ScanFilters
import com.coolplayer.music.data.ScanFiltersStore
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.data.SongLibraryDao
import com.coolplayer.music.data.StorageService
import com.coolplayer.music.data.parseSongEntryList
import com.coolplayer.music.data.toLibraryEntity
import com.coolplayer.music.data.toSongEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 音乐库分类常量。 */
object MusicCategory {
    const val SONG = 0
    const val ALBUM = 1
    const val ARTIST = 2
    const val FOLDER = 3
}

/** 歌曲排序依据。方向（升序/降序）由 [LibraryViewModel.sortAscending] 单独控制。 */
object SongSortField {
    const val TITLE = 0
    const val MODIFIED_TIME = 1
    const val FILE_NAME = 2
    const val ALBUM = 3
    const val ARTIST = 4
    const val SIZE = 5
    const val DURATION = 6
    const val PLAY_COUNT = 7
}

/** 分组排序常量。 */
object MusicGroupSort {
    const val NAME_ASC = 10
    const val NAME_DESC = 11
    const val TIME_ASC = 12
    const val TIME_DESC = 13
}

/**
 * 音乐库 ViewModel。
 *
 * 负责：权限检查、本地音频扫描、元数据异步加载与缓存、分类分组、排序、搜索、封面分页。
 *
 * 完整音乐库缓存存储在 Room（[SongLibraryDao]，`song_library` 表），不再使用
 * SharedPreferences + JSON。[allSongs] 直接由数据库的 Flow 驱动；排序 / 搜索
 * ([sortedSongs]) 通过协程调用 DAO 提供的 SQL 排序 / LIKE 查询完成，
 * 查询结果写入 [sortedSongsFlow] 供 UI 订阅，避免在主线程上对全量列表做
 * Kotlin sortedWith / filter。
 *
 * 权限申请与扫描不再是自动触发的：App 首次安装/无缓存数据时，只显示空态引导，
 * 用户需要在"媒体来源"页主动点击"开始扫描"才会触发权限请求与真正的扫描逻辑。
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val songLibraryDao: SongLibraryDao = App.database.songLibraryDao()

    /** 音乐库全量数据，直接由 Room 的 Flow 驱动，任何写入（扫描/播放计数）都会自动推送更新。 */
    val allSongs = MutableStateFlow<List<SongEntry>>(emptyList())
    val isScanning = MutableStateFlow(false)
    val hasPermission = MutableStateFlow(false)
    val permissionRequested = MutableStateFlow(false)
    val currentCategory = MutableStateFlow(MusicCategory.SONG)
    val sortField = MutableStateFlow(SongSortField.TITLE)
    val sortAscending = MutableStateFlow(true)
    val currentSortGroup = MutableStateFlow(MusicGroupSort.NAME_ASC)
    val searchKeyword = MutableStateFlow("")
    val metaLoadCount = MutableStateFlow(0)
    val coverVersion = MutableStateFlow(0)

    /** [sortedSongs] 的最新结果：由 SQL 查询异步刷新。UI 通过 [collectAsState] 观察，
     *  而不是每次都重新调用一次同步查询——排序/搜索结果由协程写入这里后自动推送给订阅者。 */
    val sortedSongsFlow = MutableStateFlow<List<SongEntry>>(emptyList())

    /** 扫描过程中逐个发现的文件名，供扫描进度弹窗滚动展示。扫描开始时清空。 */
    val scanProgressLines = MutableStateFlow<List<String>>(emptyList())
    /** 本轮扫描是否已经完成（用于控制弹窗"确定"按钮的可点击时机）。 */
    val scanCompleted = MutableStateFlow(false)
    /** 最近一次扫描得到的歌曲总数（扫描完成后展示）。 */
    val lastScanTotal = MutableStateFlow(0)

    private val coverRequestedPaths = mutableSetOf<String>()
    private var coverLoadGeneration = 0
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        currentCategory.value = StorageService.getInt(StorageService.kMusicCategory, MusicCategory.SONG)
        sortField.value = StorageService.getInt(StorageService.kMusicSortField, SongSortField.TITLE)
        sortAscending.value = StorageService.getBoolean(StorageService.kMusicSortAscending, true)
        currentSortGroup.value = StorageService.getInt(StorageService.kMusicGroupSort, MusicGroupSort.NAME_ASC)
        hasPermission.value = PermissionUtil.hasAudioPermission(getApplication())
        permissionRequested.value = hasPermission.value

        viewModelScope.launch {
            migrateLegacyJsonCacheIfNeeded()

            // 订阅 Room：音乐库表的任何变化（扫描写入、播放次数自增）都会自动同步到 allSongs。
            songLibraryDao.observeAll()
                .onEach { entities ->
                    val songs = entities.map { it.toSongEntry() }
                    allSongs.value = songs
                    if (songs.isNotEmpty() && metaLoadCount.value == 0) {
                        metaLoadCount.value = songs.size
                    }
                    refreshSortedSongs()
                }
                .launchIn(viewModelScope)
        }

        maybeRunPeriodicScan()
    }

    /**
     * 旧版本（SharedPreferences + JSON）的一次性迁移：如果 Room 音乐库表为空，
     * 但旧 JSON 缓存里还有数据，则读出来批量写入 Room，然后清空旧 key。
     * 只会执行一次（迁移后旧 key 被清空，下次判断条件不再成立）。
     */
    private suspend fun migrateLegacyJsonCacheIfNeeded() {
        withContext(Dispatchers.IO) {
            val alreadyMigrated = songLibraryDao.getAll().isNotEmpty()
            if (alreadyMigrated) return@withContext
            val legacyJson = StorageService.getJsonArray(StorageService.kMusicLibraryCache, "[]")
            if (legacyJson == "[]") return@withContext
            val legacySongs = parseSongEntryList(legacyJson)
            if (legacySongs.isEmpty()) {
                StorageService.delete(StorageService.kMusicLibraryCache)
                return@withContext
            }
            // 旧版播放次数存在单独的 JSON map 里，一并回填进新表。
            val legacyCounts = com.coolplayer.music.data.PlayCountStore.getAll()
            legacySongs.forEach { song -> legacyCounts[song.path]?.let { song.playCount = it } }
            songLibraryDao.replaceAll(legacySongs.map { it.toLibraryEntity() })
            StorageService.delete(StorageService.kMusicLibraryCache)
            com.coolplayer.music.data.PlayCountStore.clear()
        }
    }

    /**
     * 按"标题 + 艺术家"完全相同去重，保留第一次出现的条目（原始扫描顺序）。
     * 标题/艺术家均做 trim + lowercase 归一化比较，避免大小写或首尾空格造成误判为不同歌曲。
     */
    private fun dedupeSongs(songs: List<SongEntry>): List<SongEntry> {
        val seen = mutableSetOf<Pair<String, String>>()
        val out = mutableListOf<SongEntry>()
        for (song in songs) {
            val key = song.title.trim().lowercase() to song.artist.trim().lowercase()
            if (seen.add(key)) out.add(song)
        }
        return out
    }

    /**
     * App 启动时检查是否需要静默执行一次定期扫描：
     * 仅当用户开启了"定期扫描"，且距离上一次*完整完成*的扫描已经超过设定的间隔天数。
     * 扫描过程若被中断（例如扫描中被杀进程），[StorageService.kLastScanCompletedAt] 不会被更新，
     * 下次启动会视为仍需要扫描，直到某一次扫描真正跑完为止。
     */
    private fun maybeRunPeriodicScan() {
        val filters = ScanFiltersStore.get()
        if (!filters.periodicScanEnabled) return
        val intervalMs = filters.periodicScanIntervalDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val lastCompleted = StorageService.getLong(StorageService.kLastScanCompletedAt, 0L)
        val now = System.currentTimeMillis()
        if (lastCompleted != 0L && now - lastCompleted < intervalMs) return
        if (!PermissionUtil.hasAudioPermission(getApplication())) return
        // 静默扫描：不驱动扫描进度弹窗（onFound 留空），仅在后台完成后更新数据。
        rescan {}
    }

    fun onPermissionResult(granted: Boolean) {
        hasPermission.value = granted
        permissionRequested.value = true
    }

    /**
     * 重新扫描本地音频并异步加载元数据。[onFound] 每发现一个文件回调一次
     * （同时会被记录进 [scanProgressLines] 供扫描进度弹窗展示）。
     *
     * 扫描本身使用当前持久化的 [ScanFilters]（媒体来源页配置的过滤条件）。
     */
    fun rescan(onFound: (String) -> Unit = {}) {
        if (isScanning.value) return
        isScanning.value = true
        scanCompleted.value = false
        scanProgressLines.value = emptyList()
        metaLoadCount.value = 0
        resetCovers()
        viewModelScope.launch {
            val blacklist = FolderBlacklist.get()
            val filters = ScanFiltersStore.get()
            // 用可变列表在后台线程累积，避免每发现一个文件就整体复制一次
            // StateFlow 里的 List（那样是 O(N^2)），只按一定频率同步快照给 UI。
            val progressBuffer = mutableListOf<String>()
            var foundCount = 0
            val scanned = withContext(Dispatchers.IO) {
                LocalScanner.scanAudios(getApplication(), blacklist, filters) { name ->
                    progressBuffer.add(name)
                    foundCount++
                    if (foundCount % 30 == 0) {
                        val snapshot = progressBuffer.toList()
                        scanProgressLines.value = snapshot
                    }
                    onFound(name)
                }
            }
            // 扫描结束后确保最终列表完整同步一次
            scanProgressLines.value = progressBuffer.toList()
            val songs = scanned.map {
                SongEntry(it.path, it.name, it.folder, it.size, it.modified, it.durationMs)
            }
            // 已有的播放次数（Room 里）需要保留，扫描不应清零用户的播放统计。
            val existingCounts = withContext(Dispatchers.IO) {
                songLibraryDao.getAll().associate { it.path to it.playCount }
            }
            songs.forEach { song -> existingCounts[song.path]?.let { song.playCount = it } }

            // 并发加载元数据：用信号量限制并发数（避免同时打开过多文件句柄），
            // 比原来的完全串行处理明显更快，尤其在多核设备上。
            // 落盘只在全部完成后做一次整表替换，避免"每处理 20 首就重新写一次全部
            // 已扫描歌曲"这种随列表增长而越来越慢的 O(N^2) 开销。
            withContext(Dispatchers.IO) {
                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
                songs.map { song ->
                    async {
                        semaphore.withPermit {
                            val meta = AudioMetadataReader.readFile(song.path)
                            song.title = meta.title?.takeIf { it.isNotEmpty() }
                                ?: song.name.substringBeforeLast('.', song.name)
                            song.artist = meta.artist ?: ""
                            song.album = meta.album ?: ""
                            song.lyrics = meta.lyrics ?: ""
                            song.metadataLoaded = true
                            val done = processedCount.incrementAndGet()
                            if (done % 20 == 0) {
                                withContext(Dispatchers.Main) { metaLoadCount.value = done }
                            }
                        }
                    }
                }.awaitAll()
            }
            val deduped = if (filters.dedupeEnabled) dedupeSongs(songs) else songs
            withContext(Dispatchers.IO) {
                songLibraryDao.replaceAll(deduped.map { it.toLibraryEntity() })
            }
            withContext(Dispatchers.Main) { metaLoadCount.value = deduped.size }
            // allSongs / sortedSongsFlow 会通过 songLibraryDao.observeAll() 的 Flow 自动刷新，
            // 这里不需要再手动赋值。
            lastScanTotal.value = deduped.size
            isScanning.value = false
            scanCompleted.value = true
            StorageService.setLong(StorageService.kLastScanCompletedAt, System.currentTimeMillis())
        }
    }

    fun resetCovers() {
        coverLoadGeneration++
        coverRequestedPaths.clear()
        allSongs.value.forEach { it.coverBytes = null }
        coverVersion.value++
    }

    /**
     * 清理扫描数据：清空音乐库缓存（Room 表）、封面加载状态与内存中的歌曲列表。
     * 不清空收藏 / 歌单 / 播放历史（那些是用户产生的数据，不属于"扫描数据"）。
     *
     * 注：播放次数现在和歌曲同表存储，清空扫描数据会连带清空播放次数——这与旧版本
     * "播放次数独立保留"的语义不同，但更符合"数据表示同一份歌曲记录"的直觉；
     * 如果用户重新扫描到同一首歌，播放次数会归零重新累计。
     */
    fun clearScannedData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songLibraryDao.clearAll()
            }
            StorageService.delete(StorageService.kLastScanCompletedAt)
            metaLoadCount.value = 0
            lastScanTotal.value = 0
            resetCovers()
        }
    }

    /** 请求加载指定歌曲的封面（自动去重，每 20 首刷新一次 UI）。 */
    fun requestCovers(songs: List<SongEntry>) {
        val toLoad = songs.filter { it.path !in coverRequestedPaths }
        if (toLoad.isEmpty()) return
        toLoad.forEach { coverRequestedPaths.add(it.path) }
        val gen = coverLoadGeneration
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var processed = 0
                for (song in toLoad) {
                    if (coverLoadGeneration != gen) return@withContext
                    val meta = AudioMetadataReader.readFile(song.path)
                    song.coverBytes = meta.coverBytes
                    processed++
                    if (processed % 20 == 0) {
                        withContext(Dispatchers.Main) { coverVersion.value++ }
                    }
                }
                withContext(Dispatchers.Main) { coverVersion.value++ }
            }
        }
    }

    /**
     * 记录一次播放：对数据库里对应歌曲的 playCount 做一条 SQL UPDATE 自增。
     * 播放页 / 播放器可在播放开始时调用它来更新"播放次数"排序依据。
     */
    fun recordPlay(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            songLibraryDao.incrementPlayCount(path)
        }
    }

    /**
     * 触发一次 SQL 排序 / 搜索查询，把结果写入 [sortedSongsFlow]。
     * 由 [init] 里的 Room Flow 订阅、[setSort]/[setSearchKeyword] 调用触发。
     */
    private fun refreshSortedSongs() {
        val kw = searchKeyword.value.trim()
        val field = sortField.value
        val ascending = sortAscending.value
        viewModelScope.launch(Dispatchers.IO) {
            val entities = if (kw.isEmpty()) {
                when (field) {
                    SongSortField.TITLE -> if (ascending) songLibraryDao.sortByTitleAsc() else songLibraryDao.sortByTitleDesc()
                    SongSortField.MODIFIED_TIME -> if (ascending) songLibraryDao.sortByModifiedAsc() else songLibraryDao.sortByModifiedDesc()
                    SongSortField.FILE_NAME -> if (ascending) songLibraryDao.sortByNameAsc() else songLibraryDao.sortByNameDesc()
                    SongSortField.ALBUM -> if (ascending) songLibraryDao.sortByAlbumAsc() else songLibraryDao.sortByAlbumDesc()
                    SongSortField.ARTIST -> if (ascending) songLibraryDao.sortByArtistAsc() else songLibraryDao.sortByArtistDesc()
                    SongSortField.SIZE -> if (ascending) songLibraryDao.sortBySizeAsc() else songLibraryDao.sortBySizeDesc()
                    SongSortField.DURATION -> if (ascending) songLibraryDao.sortByDurationAsc() else songLibraryDao.sortByDurationDesc()
                    SongSortField.PLAY_COUNT -> if (ascending) songLibraryDao.sortByPlayCountAsc() else songLibraryDao.sortByPlayCountDesc()
                    else -> if (ascending) songLibraryDao.sortByTitleAsc() else songLibraryDao.sortByTitleDesc()
                }
            } else {
                when (field) {
                    SongSortField.TITLE -> if (ascending) songLibraryDao.searchByTitleAsc(kw) else songLibraryDao.searchByTitleDesc(kw)
                    SongSortField.MODIFIED_TIME -> if (ascending) songLibraryDao.searchByModifiedAsc(kw) else songLibraryDao.searchByModifiedDesc(kw)
                    SongSortField.FILE_NAME -> if (ascending) songLibraryDao.searchByNameAsc(kw) else songLibraryDao.searchByNameDesc(kw)
                    SongSortField.ALBUM -> if (ascending) songLibraryDao.searchByAlbumAsc(kw) else songLibraryDao.searchByAlbumDesc(kw)
                    SongSortField.ARTIST -> if (ascending) songLibraryDao.searchByArtistAsc(kw) else songLibraryDao.searchByArtistDesc(kw)
                    SongSortField.SIZE -> if (ascending) songLibraryDao.searchBySizeAsc(kw) else songLibraryDao.searchBySizeDesc(kw)
                    SongSortField.DURATION -> if (ascending) songLibraryDao.searchByDurationAsc(kw) else songLibraryDao.searchByDurationDesc(kw)
                    SongSortField.PLAY_COUNT -> if (ascending) songLibraryDao.searchByPlayCountAsc(kw) else songLibraryDao.searchByPlayCountDesc(kw)
                    else -> if (ascending) songLibraryDao.searchByTitleAsc(kw) else songLibraryDao.searchByTitleDesc(kw)
                }
            }
            val songs = entities.map { it.toSongEntry() }
            withContext(Dispatchers.Main) { sortedSongsFlow.value = songs }
        }
    }

    /**
     * 独立的关键字搜索（供 [com.coolplayer.music.ui.library.SearchScreen] 使用）。
     * 直接执行 SQL LIKE 查询（标题/艺术家/专辑，标题升序），不复用 [searchKeyword] /
     * [sortedSongsFlow]，避免与主列表的排序状态互相影响。
     */
    suspend fun searchSongs(keyword: String): List<SongEntry> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            songLibraryDao.searchByTitleAsc(kw).map { it.toSongEntry() }
        }
    }

    /**
     * 歌曲分类（[MusicCategory.SONG]）下，按当前排序字段 + 关键字搜索得到的歌曲列表。
     *
     * 实现上是同步读取 [sortedSongsFlow] 当前值（由 [refreshSortedSongs] 异步刷新的
     * SQL 查询结果），而不是每次调用都在主线程对全量列表做 Kotlin sortedWith / filter。
     * 推荐 UI 侧改用 `vm.sortedSongsFlow.collectAsState()` 直接订阅，保证排序/搜索条件
     * 变化后能收到异步查询完成的最新结果；本方法保留给仍以同步方式读取的调用点。
     */
    fun sortedSongs(): List<SongEntry> = sortedSongsFlow.value

    fun buildGroups(): List<MusicGroupEntry> = buildGroupsFor(currentCategory.value)

    /** 按指定分类构建分组（不依赖 currentCategory，供分组详情页按 category 参数查找使用）。 */
    fun buildGroupsFor(category: Int): List<MusicGroupEntry> {
        val songs = allSongs.value
        return when (category) {
            MusicCategory.ALBUM -> songs
                .groupBy { it.album.ifEmpty { "未知专辑" } }
                .map { (k, v) -> MusicGroupEntry(k, k, v) }
            MusicCategory.ARTIST -> {
                val map = linkedMapOf<String, MutableList<SongEntry>>()
                songs.forEach { s ->
                    ArtistSplitter.split(s.artist).forEach { a ->
                        map.getOrPut(a) { mutableListOf() }.add(s)
                    }
                }
                map.map { (k, v) -> MusicGroupEntry(k, k, v) }
            }
            MusicCategory.FOLDER -> songs
                .groupBy { it.folder }
                .map { (k, v) ->
                    val name = k.substringAfterLast('/').ifEmpty { k }
                    MusicGroupEntry(k, name, v)
                }
            else -> emptyList()
        }
    }

    /** 查找指定分类下 key 对应的分组（用于分组详情页）。 */
    fun findGroup(category: Int, key: String): MusicGroupEntry? {
        return buildGroupsFor(category).find { it.key == key }
    }

    fun sortedGroups(): List<MusicGroupEntry> {
        val kw = searchKeyword.value.trim().lowercase()
        val groups = buildGroups()
        val filtered = if (kw.isEmpty()) groups else groups.filter {
            it.displayName.lowercase().contains(kw)
        }
        return when (currentSortGroup.value) {
            MusicGroupSort.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            MusicGroupSort.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            MusicGroupSort.TIME_ASC -> filtered.sortedBy { it.songs.maxOf { s -> s.modified } }
            MusicGroupSort.TIME_DESC -> filtered.sortedByDescending { it.songs.maxOf { s -> s.modified } }
            else -> filtered
        }
    }

    fun setCategory(c: Int) {
        currentCategory.value = c
        StorageService.setInt(StorageService.kMusicCategory, c)
        resetCovers()
    }

    fun setSort(field: Int, ascending: Boolean) {
        sortField.value = field
        sortAscending.value = ascending
        StorageService.setInt(StorageService.kMusicSortField, field)
        StorageService.setBoolean(StorageService.kMusicSortAscending, ascending)
        resetCovers()
        refreshSortedSongs()
    }

    fun setSortGroup(s: Int) {
        currentSortGroup.value = s
        StorageService.setInt(StorageService.kMusicGroupSort, s)
    }

    fun setSearchKeyword(kw: String) {
        searchKeyword.value = kw
        refreshSortedSongs()
    }

    companion object {
        // 给 HomeScreen 用：分类标签
        val categoryLabels = listOf("歌曲", "专辑", "歌手", "文件夹")
    }
}
