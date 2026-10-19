package com.coolplayer.music.player.audiofx

import android.content.Context
import android.os.Handler
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

    override fun shouldUseBypass(format: Format): Boolean {
        if (eqActiveProvider()) {
            // EQ 生效中：禁用 bypass/offload，保证音频流经处理器链。
            return false
        }
        return super.shouldUseBypass(format)
    }
}
