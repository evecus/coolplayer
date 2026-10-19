package com.coolplayer.music.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.coolplayer.music.service.MusicPlayerBinder
import com.coolplayer.music.service.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI 侧与 [PlaybackService] 的绑定管理。
 *
 * 用法：
 * ```kotlin
 * val context = LocalContext.current
 * val playerState by PlayerConnection.state.collectAsState()
 * LaunchedEffect(Unit) { PlayerConnection.bind(context) }
 * ```
 *
 * Service 启动后会自动连接并填充 [player]，UI 通过 `PlayerConnection.player?.xxx.collectAsState()` 订阅状态。
 */
object PlayerConnection {

    private val _player = MutableStateFlow<MusicPlayer?>(null)
    val player: StateFlow<MusicPlayer?> = _player.asStateFlow()

    val state: StateFlow<MusicPlayer?> = _player

    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val p = (binder as? MusicPlayerBinder)?.getPlayer()
            _player.value = p
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _player.value = null
        }

        override fun onBindingDied(name: ComponentName?) {
            _player.value = null
        }
    }

    /** 绑定并启动 Service（前台 Service 需 startForegroundService）。 */
    fun bind(context: Context) {
        if (bound) return
        bound = true
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbind(context: Context) {
        if (!bound) return
        bound = false
        runCatching { context.unbindService(conn) }
        _player.value = null
    }
}
