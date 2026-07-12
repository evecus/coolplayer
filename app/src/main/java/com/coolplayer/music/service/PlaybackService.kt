package com.coolplayer.music.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.coolplayer.music.App
import com.coolplayer.music.MainActivity
import com.coolplayer.music.data.MusicRepository
import com.coolplayer.music.player.MusicPlayer

/**
 * 后台播放 Service：基于 Media3 的 [MediaSessionService]。
 *
 * - 持有 [MusicPlayer]（封装 ExoPlayer + MediaSession）。
 * - 提供通知栏 / 锁屏 / 蓝牙耳机按键 / 车机控制。
 * - 通过 onBind 暴露 [MusicPlayerBinder] 让 UI 直接拿 [MusicPlayer] 引用订阅 StateFlow。
 * - Service 生命周期内重建后自动 [MusicPlayer.restoreLastSession] 恢复上次播放。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var _player: MusicPlayer
    val player: MusicPlayer get() = _player
    private lateinit var _mediaSession: MediaSession

    private val binder = MusicPlayerBinder(this)

    override fun onCreate() {
        super.onCreate()
        val repo = MusicRepository(App.database)
        _player = MusicPlayer(this, repo)
        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        _mediaSession = MediaSession.Builder(this, _player.player)
            .setSessionActivity(pendingIntent)
            .build()
        _player.init(_mediaSession)
        // 自动恢复上次会话
        _player.restoreLastSession()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (this::_mediaSession.isInitialized) _mediaSession else null

    override fun onBind(intent: Intent?): android.os.IBinder? {
        // 同时支持 MediaSessionService 的 MediaBrowserService 绑定与本应用内部 binder
        super.onBind(intent)?.let { return it }
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = _player
        if (!p.player.playWhenReady || p.player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        _player.persistState()
        _player.release()
        super.onDestroy()
    }
}

/** 让 UI 通过 ServiceConnection 拿到 [MusicPlayer] 引用。 */
class MusicPlayerBinder(val service: PlaybackService) : android.os.Binder() {
    fun getPlayer(): MusicPlayer = service.player
}
