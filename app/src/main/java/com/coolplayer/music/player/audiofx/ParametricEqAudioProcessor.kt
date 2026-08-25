package com.coolplayer.music.player.audiofx

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * 参数均衡（PeakEQ）AudioProcessor：接入 ExoPlayer 的 AudioSink 处理链，
 * 对 16-bit PCM 音频做实时 biquad 滤波 + Preamp 增益。
 *
 * 用法：在构建 ExoPlayer 时通过自定义 RenderersFactory / AudioSink 把本处理器
 * 加入处理链（见 [com.coolplayer.music.player.MusicPlayer]）。
 *
 * 线程模型：`queueInput`/`getOutput` 运行在 ExoPlayer 内部音频处理线程；
 * [currentPreset] 的写入来自 UI（主线程）。为避免跨线程读写滤波器系数产生的
 * 竞态问题，系数更新通过 [pendingPreset] 做一次性快照切换，在下一次 `queueInput`
 * 时统一生效，不会产生崩溃或撕裂，最坏情况只是漏一帧参数更新。
 */
class ParametricEqAudioProcessor : BaseAudioProcessor() {

    private val _currentPreset = MutableStateFlow(EqPreset.ORIGINAL)
    val currentPreset: StateFlow<EqPreset> = _currentPreset.asStateFlow()

    @Volatile private var pendingPreset: EqPreset? = null

    private var channelCount = 2
    private var sampleRateHz = 44100

    // 每个声道一组 biquad 滤波器（数量随当前预设的 band 数量变化）
    private var filtersPerChannel: Array<Array<BiquadFilter>> = emptyArray()
    private var activePreset: EqPreset = EqPreset.ORIGINAL
    private var preampLinearGain = 1.0

    /** 供 UI 层调用：切换预设（音效风格 / 耳机型号）。 */
    fun setPreset(preset: EqPreset) {
        _currentPreset.value = preset
        pendingPreset = preset
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // 只处理 16-bit PCM；其他编码直接透传（不做 EQ），避免崩溃。
            return AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRateHz = inputAudioFormat.sampleRate
        rebuildFilters(activePreset)
        return inputAudioFormat
    }

    private fun rebuildFilters(preset: EqPreset) {
        activePreset = preset
        preampLinearGain = dbToLinear(preset.preampDb)
        filtersPerChannel = Array(channelCount) { channel ->
            Array(preset.bands.size) { bandIdx ->
                BiquadFilter().apply { configure(preset.bands[bandIdx], sampleRateHz) }
            }
        }
    }

    private fun dbToLinear(db: Float): Double = Math.pow(10.0, db / 20.0)

    override fun queueInput(inputBuffer: ByteBuffer) {
        // 每次处理前检查是否有新预设待生效（在处理边界切换，避免系数中途改变导致的爆音）
        pendingPreset?.let { newPreset ->
            pendingPreset = null
            if (newPreset.id != activePreset.id) {
                rebuildFilters(newPreset)
            }
        }

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val sampleCount = input.remaining()

        if (activePreset.bands.isEmpty() && activePreset.preampDb == 0f) {
            // 原声：直接透传，零额外开销
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val out = outputBuffer.asShortBuffer()
        var frameIdx = 0
        val framesTotal = sampleCount / channelCount
        while (frameIdx < framesTotal) {
            for (ch in 0 until channelCount) {
                val idx = frameIdx * channelCount + ch
                var sample = input.get(idx) / 32768.0
                val chainFilters = filtersPerChannel.getOrNull(ch)
                if (chainFilters != null) {
                    for (f in chainFilters) {
                        sample = f.process(sample)
                    }
                }
                sample *= preampLinearGain
                // 硬限幅，防止叠加增益导致削波爆音
                val clamped = max(-1.0, min(1.0, sample))
                out.put(idx, (clamped * 32767.0).toInt().toShort())
            }
            frameIdx++
        }
        inputBuffer.position(inputBuffer.position() + remaining)
        outputBuffer.position(sampleCount * 2)
        outputBuffer.flip()
    }

    override fun onFlush() {
        // seek / 切歌时清空滤波器历史状态，避免上一段音频的残留样本产生咔哒声
        filtersPerChannel.forEach { chain -> chain.forEach { it.reset() } }
    }

    override fun onReset() {
        filtersPerChannel = emptyArray()
    }
}
