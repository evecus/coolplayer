package com.coolplayer.music.player.audiofx

import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * EQ 感知的音频渲染器：在 [shouldUseBypass] 决策点把"EQ 生效中"与
 * "Audio Offload（bypass）"互斥。
 *
 * 背景（media3 1.4.1 字节码实证）：
 * - `MediaCodecRenderer.shouldUseBypass(format)` 的判定只看
 *   `RendererConfiguration.offloadModePreferred != 0` 与格式的 offload
 *   支持情况（`getAudioOffloadSupport(format) & RENDERER_SUPPORT_FLAG`），
 *   **不检查音频处理器链**；
 * - 而 `DefaultAudioSink.configure` 对非 `audio/raw`（即压缩格式直接
 *   offload）输入使用**空 AudioProcessingPipeline**——也就是说 bypass
 *   一旦启用，挂链上的 [ParametricEqAudioProcessor] 被完全旁路，EQ 静默
 *   失效（media3 已知约束：offload 与 AudioProcessor 互斥）。
 *
 * 因此 EQ 生效期间必须返回 false 强制走"解码 → PCM → 处理器链"的常规
 * 路径；原声（processor 不 active）时交给父类正常决策，走 offload
 * 低功耗路径（解码+输出都在 audio DSP/HAL，app 侧 CPU 趋近 0——
 * 这正是本地原声播放后台 CPU 的主要优化点）。
 *
 * 决策时机说明：shouldUseBypass 在 `MediaCodecRenderer.maybeInitCodecOrBypass`
 * 时评估（prepare / 切歌 / 格式变化 / seek 后 decoder 重新 init）。运行中
 * 开关 EQ 造成的路径翻转由 MusicPlayer 通过重载当前曲目触发重新评估。
 *
 * 诊断埋点（tag = "CoolPlayerOffload"）：真机复测若 CPU 未如预期下降，
 * 这条日志给出完整决策输入，可逐项对账定位断点：
 * - `offloadModePreferred == 0` → TrackSelector 未下发 offload（偏好没生效
 *   或设备不支持该格式的 offload，`maybeConfigureRendererForOffload` 不放行）；
 * - `offloadModePreferred == 2 && formatSupported == false` → 设备侧
 *   `AudioManager.isOffloadedPlaybackSupported` 返回 false（ROM/HAL 不支持）；
 * - `offloadModePreferred == 2 && formatSupported == true && bypass == true`
 *   → offload 已真正启用，剩余 CPU 大头在音频链路之外。
 */
class EqAwareMediaCodecAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
    private val eqActiveProvider: () -> Boolean
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
) {

    /**
     * 父类的 audioSink 字段是 private，子类要自查 offload 支持情况
     * （`getFormatOffloadSupport` 内部即 `AudioManager
     * .isOffloadedPlaybackSupported`）必须自带一份引用。
     */
    private val sinkRef: AudioSink = audioSink

    override fun shouldUseBypass(format: Format): Boolean {
        val eqActive = eqActiveProvider()
        // getConfiguration() 为 BaseRenderer 的 protected final，子类可读。
        val offloadModePreferred = try {
            getConfiguration()?.offloadModePreferred ?: -1
        } catch (t: Throwable) {
            -2
        }
        val support = try {
            sinkRef.getFormatOffloadSupport(format)
        } catch (t: Throwable) {
            null
        }
        val bypass = if (eqActive) false else super.shouldUseBypass(format)
        Log.i(
            TAG,
            "shouldUseBypass mime=${format.sampleMimeType} rate=${format.sampleRate} " +
                "ch=${format.channelCount} codecs=${format.codecs} eqActive=$eqActive " +
                "offloadModePreferred=$offloadModePreferred " +
                "formatSupported=${support?.isFormatSupported} " +
                "gapless=${support?.isGaplessSupported} " +
                "speed=${support?.isSpeedChangeSupported} " +
                "sdk=${Build.VERSION.SDK_INT} -> bypass=$bypass"
        )
        return bypass
    }

    companion object {
        const val TAG: String = "CoolPlayerOffload"
    }
}
