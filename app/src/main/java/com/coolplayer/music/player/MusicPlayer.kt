package com.coolplayer.music.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.ExoPlayer
import com.coolplayer.music.data.AudioMetadataReader
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.data.SongEntry
import com.coolplayer.music.data.StorageService
import com.coolplayer.music.player.audiofx.EqAwareRenderersFactory
import com.coolplayer.music.player.audiofx.ParametricEqAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val exoPlayer: ExoPlayer = run {
        val renderersFactory = EqAwareRenderersFactory(context, eqProcessor)
        ExoPlayer.Builder(context, renderersFactory).build()
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
        startPositionPolling()
    }

    private fun startPositionPolling() {
        posPollJob?.cancel()
        posPollJob = scope.launch {
            while (true) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                _position.value = pos
                val rawDur = exoPlayer.duration
                val dur = if (rawDur == C.TIME_UNSET || rawDur < 0) 0L else rawDur
                if (dur != _duration.value && dur > 0) _duration.value = dur
                kotlinx.coroutines.delay(200L)
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
            val meta = AudioMetadataReader.readFile(song.path)
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
            if (meta.coverBytes != null) {
                song.coverBytes = meta.coverBytes
                _coverBytes.value = meta.coverBytes
                updateMediaSessionArtwork(meta.coverBytes)
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
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun play() {
        exoPlayer.play()
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
        sleepTimer.cancel()
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
                paths.map { p ->
                    val f = File(p)
                    SongEntry(p, f.name, f.parent ?: "/", f.length(), f.lastModified()).also { song ->
                        if (!song.metadataLoaded) {
                            val meta = AudioMetadataReader.readFile(p)
                            song.title = meta.title?.takeIf { it.isNotEmpty() }
                                ?: f.name.substringBeforeLast('.', f.name)
                            song.artist = meta.artist ?: ""
                            song.album = meta.album ?: ""
                            song.lyrics = meta.lyrics ?: ""
                            song.metadataLoaded = true
                        }
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
