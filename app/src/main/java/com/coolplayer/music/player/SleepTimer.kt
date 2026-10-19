package com.coolplayer.music.player

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 睡眠定时器。
 *
 * 支持两种模式：
 * - 倒计时停止：到点 [onTrigger]
 * - 播完当前再停：注册后等待下一首 onCompleted 时触发
 *
 * 使用方式：
 * ```
 * val timer = SleepTimer { player.pause() }
 * timer.setCountdown(30 * 60 * 1000L)   // 30 分钟后停
 * timer.setFinishCurrent()              // 当前这首播完再停
 * timer.cancel()
 * ```
 */
class SleepTimer(private val onTrigger: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private val finishCurrentRunnable = Runnable { /* 占位，触发由外部调用 */ }

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _mode = MutableStateFlow(SleepMode.OFF)
    val mode: StateFlow<SleepMode> = _mode.asStateFlow()

    private var endTimeMs: Long = 0L
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!_active.value) return
            val now = System.currentTimeMillis()
            val remain = (endTimeMs - now).coerceAtLeast(0L)
            _remainingMs.value = remain
            if (remain <= 0L) {
                fire()
            } else {
                handler.postDelayed(this, 500L)
            }
        }
    }

    /** 设置倒计时停止（毫秒）。 */
    fun setCountdown(durationMs: Long) {
        cancel()
        _mode.value = SleepMode.COUNTDOWN
        _active.value = true
        endTimeMs = System.currentTimeMillis() + durationMs
        _remainingMs.value = durationMs
        handler.post(tickRunnable)
    }

    /** 设置「播完当前再停」。 */
    fun setFinishCurrent() {
        cancel()
        _mode.value = SleepMode.FINISH_CURRENT
        _active.value = true
        _remainingMs.value = 0L
    }

    /** 由 MusicPlayer 在歌曲播放完成时调用，若处于 FINISH_CURRENT 模式则触发停止。 */
    fun onSongCompleted() {
        if (_active.value && _mode.value == SleepMode.FINISH_CURRENT) {
            fire()
        }
    }

    fun cancel() {
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(finishCurrentRunnable)
        _active.value = false
        _mode.value = SleepMode.OFF
        _remainingMs.value = 0L
    }

    private fun fire() {
        _active.value = false
        _mode.value = SleepMode.OFF
        _remainingMs.value = 0L
        handler.removeCallbacks(tickRunnable)
        onTrigger()
    }
}

enum class SleepMode { OFF, COUNTDOWN, FINISH_CURRENT }
