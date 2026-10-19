package com.coolplayer.music.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.coolplayer.music.data.AudioMetadata
import com.coolplayer.music.data.AudioMetadataReader
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.data.StorageService
import com.coolplayer.music.data.toSongEntry
import com.coolplayer.music.player.audiofx.EqAwareRenderersFactory
import com.coolplayer.music.player.audiofx.ParametricEqAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 播放模式。 */
enum class PlayMode { LIST, SHUFFLE, REPEAT_ONE }

/**
 * 音乐播放器：包装 ExoPlayer + MediaSession，对外暴露 [StateFlow] 状态。
 *
 * - 由 [com.coolplayer.music.service.PlaybackService] 在 onCreate 中创建，onDestroy 中释放。
 * - UI 侧通过 [PlayerConnection] 绑定 Service 拿到本实例，直接订阅 StateFlow。
 * - MediaSession 提供给系统通知栏 / 锁屏 / 蓝牙耳机 / 车机控制。
 */
class MusicPlayer(
    private val context: Context,
    private val repository: MusicRepository
) {

    /** 参数均衡处理器：暴露给 UI（AudioFxScreen）读取/切换当前预设。 */
    val eqProcessor = ParametricEqAudioProcessor()

    // 已通过反编译 media3-exoplayer-1.4.1.aar 核实：DefaultRenderersFactory 并没有
    // setAudioSinkFactory 这个公开方法；真正的扩展点是 protected 方法
    // buildAudioSink(Context, boolean, boolean): AudioSink，需要继承后重写。
    // 见下方 EqAwareRenderersFactory。
    //
    // Audio Offload（1.4.1 字节码实证的完整链路）：
    // - TrackSelectionParameters.audioOffloadPreferences 默认
    //   AUDIO_OFFLOAD_MODE_DISABLED(0) → MediaCodecAudioRenderer.shouldUseBypass
    //   恒 false → 永远走"app 侧解码 + PCM 搬运"路径。这是本地原声播放后台
    //   CPU 偏高的根因之一（320k MP3 实测 20% vs Salt 10%）。
    // - 显式开启 AUDIO_OFFLOAD_MODE_ENABLED 后，支持 offload 的设备/格式
    //   （API>=29 + AudioManager.isOffloadedPlaybackSupported）下解码+输出
    //   全部在 audio DSP/HAL 完成，app 侧 CPU 趋近 0。
    // - offload 与 AudioProcessor 互斥（DefaultAudioSink.configure 对压缩格式
    //   输入使用空 pipeline）：EQ 生效时由 EqAwareMediaCodecAudioRenderer
    //   在 shouldUseBypass 里强制关闭 bypass，原声时自动回到 offload。
    private val exoPlayer: ExoPlayer = run {
        val renderersFactory = EqAwareRenderersFactory(context, eqProcessor)
        val trackSelector = DefaultTrackSelector(context).apply {
            // 注意：不能用 Kotlin 属性赋值 `parameters = ...`——getter 的协变
            // 返回类型是 DefaultTrackSelector$Parameters，setter 接受
            // TrackSelectionParameters，属性语法会报 assignment type mismatch。
            // 显式调用 setParameters(TrackSelectionParameters) 即可。
            setParameters(
                buildUponParameters()
                    .setAudioOffloadPreferences(
                        TrackSelectionParameters.AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(
                                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                            )
                            .build()
                    )
                    .build()
            )
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setTrackSelector(trackSelector)
            .build()
    }
    val player: ExoPlayer get() = exoPlayer

    lateinit var mediaSession: MediaSession
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var posPollJob: Job? = null

    // ── 状态流 ──────────────────────────────────────────────
    private val _playlist = MutableStateFlow<List<SongEntry>>(emptyList())
    val playlist: StateFlow<List<SongEntry>> = _playlist.asStateFlow()

    private val _currentIdx = MutableStateFlow(0)
    val currentIdx: StateFlow<Int> = _currentIdx.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _coverBytes = MutableStateFlow<ByteArray?>(null)
    val coverBytes: StateFlow<ByteArray?> = _coverBytes.asStateFlow()

    private val _lyrics = MutableStateFlow("")
    val lyrics: StateFlow<String> = _lyrics.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist.asStateFlow()

    private val _album = MutableStateFlow("")
    val album: StateFlow<String> = _album.asStateFlow()

    private val _hasContent = MutableStateFlow(false)
    val hasContent: StateFlow<Boolean> = _hasContent.asStateFlow()

    val sleepTimer = SleepTimer { pause() }

    // ── 初始化 ──────────────────────────────────────────────
    fun init(session: MediaSession) {
        this.mediaSession = session
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                // 淡入淡出的自愈：MediaSession 是直接包在 ExoPlayer 上的，
                // 通知栏 / 锁屏 / 蓝牙耳机的播放命令会绕过本类的 play()，
                // 直接从淡出后的 0 音量开始播，出现"在放但没声音"。
                // 这里检测到"正在播放、音量未满、且没有正在进行的淡入淡出"
                // 时补一次快速淡入。
                if (isPlaying && fadeJob?.isActive != true && exoPlayer.volume < 0.999f) {
                    fadeInFromCurrent()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> _isBuffering.value = true
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        val d = exoPlayer.duration
                        val dur = if (d == C.TIME_UNSET || d < 0) 0L else d
                        _duration.value = dur
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        sleepTimer.onSongCompleted()
                        onCompleted()
                    }
                    Player.STATE_IDLE -> _isBuffering.value = false
                }
            }
        })
        observeEqPathSwitch()
        startPositionPolling()
    }

    /**
     * EQ 开关状态翻转时刷新播放路径（offload ↔ PCM+EQ）。
     *
     * 背景（media3 1.4.1 实证）：offload/bypass 决策发生在
     * `MediaCodecRenderer.maybeInitCodecOrBypass`（prepare / 切歌 / 格式变化 /
     * decoder 重新 init 边界），运行中改 EQ 状态**不会**自动重估；而 offload
     * 与 AudioProcessor 互斥——开着 EQ 却仍走 offload 会导致 EQ 静默失效。
     *
     * 这里在"原声 ↔ 有 EQ"翻转且正在播放时，用当前 MediaItem 重载一次播放
     * （setMediaItem + prepare + seekTo 原位），强制 renderer 重新 init、
     * 重新评估 shouldUseBypass。本地文件重载开销约百毫秒，且只发生在用户
     * 手动开关 EQ 的瞬间，可接受；暂停中翻转则什么都不做，下次播放自然生效。
     */
    private fun observeEqPathSwitch() {
        scope.launch {
            var lastActive = eqProcessor.eqActiveRequested
            eqProcessor.currentPreset.collect { _ ->
                val nowActive = eqProcessor.eqActiveRequested
                if (nowActive == lastActive) return@collect
                lastActive = nowActive
                if (!exoPlayer.isPlaying) return@collect
                val item = exoPlayer.currentMediaItem ?: return@collect
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                exoPlayer.setMediaItem(item)
                exoPlayer.prepare()
                exoPlayer.seekTo(pos)
                exoPlayer.play()
            }
        }
    }

    private fun startPositionPolling() {
        posPollJob?.cancel()
        posPollJob = scope.launch {
            while (true) {
                // 只在真正播放时才轮询进度；暂停/无内容时完全挂起，不做任何工作。
                // 之前是无条件 while(true) + delay(200)，哪怕暂停或没有加载歌曲也
                // 一直在跑，是待机时 CPU 占用偏高的来源之一。
                if (_isPlaying.value) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    _position.value = pos
                    val rawDur = exoPlayer.duration
                    val dur = if (rawDur == C.TIME_UNSET || rawDur < 0) 0L else rawDur
                    if (dur != _duration.value && dur > 0) _duration.value = dur
                    // 200ms → 500ms：进度条与歌词高亮都不需要 5Hz 的刷新率，
                    // 人眼对进度条 0.5s 的分辨率完全无感，但 Compose 侧的
                    // 重组/重绘频率直接下降 60%（播放页整页随 position 重组，
                    // 是播放时 UI 线程 CPU 的主要来源）。
                    kotlinx.coroutines.delay(500L)
                } else {
                    // 暂停中：挂起等待恢复播放，不轮询、不占 CPU。
                    _isPlaying.first { playing -> playing }
                }
            }
        }
    }

    // ── 播放控制 ─────────────────────────────────────────────

    /** 载入播放列表并从 [index] 开始播放。 */
    fun playPlaylist(songs: List<SongEntry>, index: Int) {
        if (songs.isEmpty()) return
        _playlist.value = songs
        _hasContent.value = true
        playAt(index.coerceIn(0, songs.lastIndex))
        // 持久化
        scope.launch {
            StorageService.setStringList(
                StorageService.kLastPlaylist,
                songs.map { it.path }
            )
            StorageService.setInt(StorageService.kLastIndex, index.coerceIn(0, songs.lastIndex))
        }
    }

    fun playAt(i: Int) {
        val list = _playlist.value
        if (i !in list.indices) return
        _currentIdx.value = i
        val song = list[i]
        _title.value = song.title
        _artist.value = song.artist.ifEmpty { "未知歌手" }
        _album.value = song.album
        _lyrics.value = song.lyrics
        _coverBytes.value = null
        scope.launch { exoPlayer.setMediaItem(buildMediaItem(song)); exoPlayer.prepare(); exoPlayer.play() }
        loadMetadata(song)
        scope.launch {
            repository.recordPlay(song)
            withContext(Dispatchers.IO) {
                repository.songLibraryDao.incrementPlayCount(song.path)
            }
            song.playCount += 1
        }
    }

    private fun loadMetadata(song: SongEntry) {
        ioScope.launch {
            // ── 元数据来源策略（参考 Salt Player 的"读一次、到处用"思路）──
            //
            // song.metadataLoaded == true 表示该曲目的标题/歌手/专辑/歌词已经在
            // 扫描阶段解析过并持久化进了 Room（见 LibraryViewModel.rescan），
            // 此时播放页直接复用数据库里的字段，不再碰磁盘。
            // JAudioTagger 是纯 Java 实现，全量解析单个文件（音频头 + 标签 +
            // 提取内嵌封面）在旗舰机上也常需数十毫秒 CPU；"每次切歌都重新解析
            // 一遍"是播放场景下不必要的持续性开销。
            //
            // 封面是唯一例外：列表用的是数据库里压缩过的小图缩略图，播放页要
            // 展示原图画质，因此单独走 CoverCache + 按需只读封面。
            val meta: AudioMetadata? = if (song.metadataLoaded) null else AudioMetadataReader.readFile(song.path)

            if (meta != null) {
                if (_lyrics.value.isEmpty() && !meta.lyrics.isNullOrEmpty()) {
                    _lyrics.value = meta.lyrics
                    song.lyrics = meta.lyrics
                }
                if (_title.value.isEmpty() && !meta.title.isNullOrEmpty()) {
                    _title.value = meta.title
                    song.title = meta.title
                }
                if (_artist.value.isEmpty() && !meta.artist.isNullOrEmpty()) {
                    _artist.value = meta.artist
                    song.artist = meta.artist
                }
                if (song.album.isEmpty() && !meta.album.isNullOrEmpty()) {
                    song.album = meta.album
                    _album.value = meta.album
                }
                song.metadataLoaded = true
            } else if (song.metadataLoaded) {
                // 复用数据库元数据：仅在 StateFlow 还是空的时候补一次，
                // 避免把同一份字符串反复写进 MutableStateFlow 触发无谓重组。
                if (_lyrics.value.isEmpty() && song.lyrics.isNotEmpty()) _lyrics.value = song.lyrics
                if (_title.value.isEmpty() && song.title.isNotEmpty()) _title.value = song.title
                if (_artist.value.isEmpty() && song.artist.isNotEmpty()) _artist.value = song.artist
                if (_album.value.isEmpty() && song.album.isNotEmpty()) _album.value = song.album
            }

            // 封面：优先命中内存缓存（单曲循环/恢复播放零读盘），未命中才读盘，
            // 读到的结果回写缓存供下次使用。
            val cover = CoverCache.get(song.path)
                ?: (meta?.coverBytes ?: AudioMetadataReader.readCoverOnly(song.path))?.also {
                    CoverCache.put(song.path, it)
                }
            if (cover != null) {
                // 注意：这里不再写 song.coverBytes——该字段现在语义上专指
                // SongCoverEntity 数据库表里的小图缩略图（见 SongEntry 上的
                // 注释），播放页展示走独立的 _coverBytes（下方），继续把这里
                // 读到的原始大图写进 song.coverBytes 只会造成语义混淆，
                // 且对展示没有任何实际作用（下一次数据库 Flow emit 就会用
                // 一个全新对象替换掉它）。
                _coverBytes.value = cover
                updateMediaSessionArtwork(cover)
            }
            updateMediaMetadataToSession()
        }
    }

    private fun buildMediaItem(song: SongEntry): MediaItem {
        val trimmed = song.path.trim()
        val uri = try {
            val u = Uri.parse(trimmed)
            when (u.scheme?.lowercase()) {
                "http", "https", "file", "content" -> u
                else -> Uri.fromFile(File(trimmed))
            }
        } catch (e: Exception) {
            Uri.fromFile(File(trimmed))
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build()
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) fadeOutAndPause() else fadeInAndPlay()
    }

    fun pause() {
        fadeJob?.cancel()
        exoPlayer.pause()
        exoPlayer.volume = 1f
    }

    fun play() {
        fadeJob?.cancel()
        exoPlayer.play()
        exoPlayer.volume = 1f
    }

    // ── 淡入淡出（移植自 Salt Player 的 fade_in_or_out_during_play_or_pause）──
    //
    // Salt 的实现是在 BASS 输出通道上做音量斜坡（BASS_ChannelSetAttribute
    // BASS_ATTRIB_VOL 逐级逼近）；ExoPlayer 侧等价的扩展点就是 AudioSink 的
    // volume（DefaultAudioSink 在音频处理链之后、写 AudioTrack 之前应用音量，
    // 因此淡入淡出不会与 EQ 处理相互干扰）。
    //
    // 默认 300ms / 15 步（约 20ms 一步，与一次音频缓冲时长相当，听起来是平滑
    // 直线而不会有可闻的台阶感）。

    // 默认 300ms / 15 步（约 20ms 一步，与一次音频缓冲时长相当，听起来是平滑
    // 直线而不会有可闻的台阶感）。
    private val FADE_STEPS = 15
    private val FADE_DURATION_MS = 300L

    /** 淡入淡出总开关，对应 Salt 的 fade_in_or_out_during_play_or_pause（默认开）。 */
    private val fadeEnabled: Boolean
        get() = StorageService.getBoolean(StorageService.kFadeInOut, true)

    @Volatile private var fadeJob: Job? = null

    /** 淡出到静音再暂停；开关关闭时立即暂停。 */
    private fun fadeOutAndPause() {
        if (!fadeEnabled) {
            exoPlayer.pause()
            return
        }
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = exoPlayer.volume
            for (i in 1..FADE_STEPS) {
                exoPlayer.volume = from * (1f - i / FADE_STEPS.toFloat())
                delay(FADE_DURATION_MS / FADE_STEPS)
            }
            exoPlayer.volume = 0f
            exoPlayer.pause()
        }
    }

    /** 从静音开始播放并淡入到全量；开关关闭时直接满量播放。 */
    private fun fadeInAndPlay() {
        if (!fadeEnabled) {
            exoPlayer.play()
            exoPlayer.volume = 1f
            return
        }
        fadeJob?.cancel()
        exoPlayer.volume = 0f
        exoPlayer.play()
        fadeInFromCurrent()
    }

    /** 从当前音量快速补一个淡入到全量。 */
    private fun fadeInFromCurrent() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = exoPlayer.volume
            for (i in 1..FADE_STEPS) {
                exoPlayer.volume = from + (1f - from) * (i / FADE_STEPS.toFloat())
                delay(FADE_DURATION_MS / FADE_STEPS)
            }
            exoPlayer.volume = 1f
        }
    }

    fun next() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val idx = when (_playMode.value) {
            PlayMode.SHUFFLE -> {
                if (list.size <= 1) 0
                else (0 until list.size).filter { it != _currentIdx.value }.random()
            }
            else -> (_currentIdx.value + 1) % list.size
        }
        playAt(idx)
    }

    fun prev() {
        val list = _playlist.value
        if (list.isEmpty()) return
        // 播放超过 3 秒则回到本曲开头
        if (_position.value > 3000L) {
            seekTo(0L)
            return
        }
        val idx = if (_currentIdx.value - 1 < 0) list.lastIndex else _currentIdx.value - 1
        playAt(idx)
    }

    fun seekTo(ms: Long) {
        exoPlayer.seekTo(ms.coerceAtLeast(0L))
        _position.value = ms.coerceAtLeast(0L)
        // ExoPlayer 内部会在 seek 时自动 flush AudioSink（含 eqProcessor.onFlush），无需手动调用。
    }

    fun cyclePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.LIST -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.LIST
        }
    }

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
    }

    private fun onCompleted() {
        when (_playMode.value) {
            PlayMode.REPEAT_ONE -> playAt(_currentIdx.value)
            else -> next()
        }
    }

    /** 在播放列表中追加歌曲（不切换当前播放）。 */
    fun appendToPlaylist(songs: List<SongEntry>) {
        _playlist.value = _playlist.value + songs
    }

    /** 替换播放列表（保持当前歌曲不中断）。 */
    fun setPlaylist(songs: List<SongEntry>) {
        if (songs.isEmpty()) return
        _playlist.value = songs
        _hasContent.value = true
    }

    /** 移除播放列表中指定索引的歌曲。 */
    fun removeAt(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _playlist.value = list
        val cur = _currentIdx.value
        when {
            index < cur -> _currentIdx.value = cur - 1
            index == cur -> {
                if (list.isEmpty()) {
                    exoPlayer.clearMediaItems()
                    _hasContent.value = false
                } else {
                    playAt(cur.coerceIn(0, list.lastIndex))
                }
            }
        }
    }

    // ── MediaSession 同步 ─────────────────────────────────────

    private fun updateMediaMetadataToSession() {
        // MediaItem 已在 buildMediaItem 中携带 metadata，通知栏 / 锁屏由 MediaSession 自动同步。
        // 元数据在 loadMetadata 后会通过 setMediaItem 重新设置封面信息。
        val song = _playlist.value.getOrNull(_currentIdx.value) ?: return
        if (_coverBytes.value != null) {
            // 重新设置 MediaItem 让通知栏更新封面元数据
            val updatedItem = buildMediaItem(song)
            runCatching {
                exoPlayer.replaceMediaItem(_currentIdx.value, updatedItem)
            }
        }
    }

    private fun updateMediaSessionArtwork(bytes: ByteArray) {
        updateMediaMetadataToSession()
    }

    // ── 生命周期 ──────────────────────────────────────────────

    fun release() {
        posPollJob?.cancel()
        fadeJob?.cancel()
        sleepTimer.cancel()
        CoverCache.clear()
        runCatching {
            if (::mediaSession.isInitialized) mediaSession.release()
        }
        exoPlayer.release()
    }

    /** 把当前播放列表与位置恢复到 Service 重建后。 */
    fun restoreLastSession() {
        val paths = StorageService.getStringList(StorageService.kLastPlaylist, emptyList())
        if (paths.isEmpty()) return
        val lastIdx = StorageService.getInt(StorageService.kLastIndex, 0)
        val lastPos = StorageService.getLong(StorageService.kLastPosition, 0L)
        scope.launch {
            val songs = withContext(ioScope.coroutineContext) {
                // 元数据全部来自 Room：一次 IN 查询拿回整个列表的
                // 标题/歌手/专辑/歌词/播放次数。原实现是对列表里每个文件
                // 逐个调用 JAudioTagger 全量解析，Service 每重建一次
                // （切歌后被杀、蓝牙断连重连等）就要串行读 N 个文件，
                // 是启动期 CPU/IO 峰值的主要来源。
                //
                // 数据库里查不到的路径（扫描数据被清理、文件是新增的）退化为
                // 仅含 File 基本信息的条目，metadataLoaded=false，
                // 后续 loadMetadata 会按需补一次读盘，行为自愈。
                val entities = repository.songLibraryDao.getByPaths(paths).associateBy { it.path }
                paths.map { p ->
                    entities[p]?.toSongEntry() ?: run {
                        val f = File(p)
                        SongEntry(p, f.name, f.parent ?: "/", f.length(), f.lastModified())
                    }
                }
            }
            _playlist.value = songs
            _hasContent.value = true
            _currentIdx.value = lastIdx.coerceIn(0, songs.lastIndex)
            val song = songs[_currentIdx.value]
            _title.value = song.title
            _artist.value = song.artist.ifEmpty { "未知歌手" }
            _album.value = song.album
            _lyrics.value = song.lyrics
            withContext(Dispatchers.Main) {
                exoPlayer.setMediaItem(buildMediaItem(song))
                exoPlayer.prepare()
                if (lastPos > 0L) exoPlayer.seekTo(lastPos)
                // 不自动 play，等待用户操作
                updateMediaMetadataToSession()
            }
            loadMetadata(song)
        }
    }

    /** App 进入后台时持久化当前位置（用于下次恢复）。 */
    fun persistState() {
        StorageService.setInt(StorageService.kLastIndex, _currentIdx.value)
        StorageService.setLong(StorageService.kLastPosition, exoPlayer.currentPosition)
    }
}
