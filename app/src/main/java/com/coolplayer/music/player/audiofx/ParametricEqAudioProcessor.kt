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
 *
 * 性能说明（对应"开 EQ 后 CPU 明显升高"的排查结论）：
 * 早期实现里 `queueInput` 每个采样点都要：
 *   1. 调 `filtersPerChannel.getOrNull(ch)`（每次一次数组越界检查 + 可空包装）；
 *   2. 用 `ShortBuffer.get(idx)/put(idx, ...)` 随机访问（每次都有偏移计算和边界检查，
 *      比顺序访问慢）；
 *   3. 对 `BiquadFilter` 数组做 `for (f in chain)`，每次都是一次跨对象字段访问
 *      （系数、状态都在被调用对象内部，无法被内联/寄存器化）。
 * 44.1kHz 立体声下，这些开销会被放大到每秒 88,200 次 × band 数，是"开 EQ 后 CPU 从
 * 20% 涨到 40%"的主因。
 *
 * 优化后的思路：
 *   - 滤波器系数在 [rebuildFilters] 时打平成局部 DoubleArray（[bandB0]/[bandB1]/...），
 *     滤波循环内直接按下标读写 primitive 数组，不再跨对象访问字段；
 *   - 滤波器状态（z1/z2）同样打平成 `[声道][band]` 的二维 DoubleArray，避免持有一堆
 *     独立的 BiquadFilter 对象、每次 process() 都是方法调用；
 *   - 用 `ShortArray` 一次性把整个 buffer 顺序读出来处理，再一次性写回，避免对
 *     `ShortBuffer` 做逐样本随机访问。
 */
class ParametricEqAudioProcessor : BaseAudioProcessor() {

    private val _currentPreset = MutableStateFlow(EqPreset.ORIGINAL)
    val currentPreset: StateFlow<EqPreset> = _currentPreset.asStateFlow()

    @Volatile private var pendingPreset: EqPreset? = null

    private var channelCount = 2
    private var sampleRateHz = 44100

    private var activePreset: EqPreset = EqPreset.ORIGINAL
    private var preampLinearGain = 1.0
    private var bandCount = 0

    // 打平后的滤波器系数（所有声道共用同一组系数，因为左右声道用相同 EQ 曲线）。
    private var coefB0 = DoubleArray(0)
    private var coefB1 = DoubleArray(0)
    private var coefB2 = DoubleArray(0)
    private var coefA1 = DoubleArray(0)
    private var coefA2 = DoubleArray(0)

    // 每声道每 band 的滤波器状态（Direct Form II Transposed 的 z1/z2），[channel][band]。
    private var stateZ1: Array<DoubleArray> = emptyArray()
    private var stateZ2: Array<DoubleArray> = emptyArray()

    // 复用的临时缓冲区，避免每次 queueInput 都重新分配 ShortArray。
    private var scratch = ShortArray(0)

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
        bandCount = preset.bands.size

        coefB0 = DoubleArray(bandCount)
        coefB1 = DoubleArray(bandCount)
        coefB2 = DoubleArray(bandCount)
        coefA1 = DoubleArray(bandCount)
        coefA2 = DoubleArray(bandCount)
        for (i in 0 until bandCount) {
            val f = BiquadFilter().apply { configure(preset.bands[i], sampleRateHz) }
            val c = f.exportCoefficients()
            coefB0[i] = c[0]; coefB1[i] = c[1]; coefB2[i] = c[2]; coefA1[i] = c[3]; coefA2[i] = c[4]
        }
        stateZ1 = Array(channelCount) { DoubleArray(bandCount) }
        stateZ2 = Array(channelCount) { DoubleArray(bandCount) }
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

        if (bandCount == 0 && activePreset.preampDb == 0f) {
            // 原声：直接透传，零额外开销（不经过下面的 ShortArray 转换）。
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val sampleCount = input.remaining()

        if (scratch.size < sampleCount) scratch = ShortArray(sampleCount)
        input.get(scratch, 0, sampleCount)

        val gain = preampLinearGain
        val nBands = bandCount
        val nCh = channelCount
        val b0 = coefB0; val b1 = coefB1; val b2 = coefB2; val a1 = coefA1; val a2 = coefA2
        val framesTotal = sampleCount / nCh

        var frameIdx = 0
        var idx = 0
        while (frameIdx < framesTotal) {
            var ch = 0
            while (ch < nCh) {
                var sample = scratch[idx].toDouble() / 32768.0
                val z1 = stateZ1[ch]
                val z2 = stateZ2[ch]
                var band = 0
                while (band < nBands) {
                    val out = b0[band] * sample + z1[band]
                    z1[band] = b1[band] * sample - a1[band] * out + z2[band]
                    z2[band] = b2[band] * sample - a2[band] * out
                    sample = out
                    band++
                }
                sample *= gain
                val clamped = if (sample > 1.0) 1.0 else if (sample < -1.0) -1.0 else sample
                scratch[idx] = (clamped * 32767.0).toInt().toShort()
                idx++
                ch++
            }
            frameIdx++
        }

        val out = outputBuffer.asShortBuffer()
        out.put(scratch, 0, sampleCount)
        inputBuffer.position(inputBuffer.position() + remaining)
        outputBuffer.position(sampleCount * 2)
        outputBuffer.flip()
    }

    override fun onFlush() {
        // seek / 切歌时清空滤波器历史状态，避免上一段音频的残留样本产生咔哒声
        for (ch in stateZ1.indices) {
            stateZ1[ch].fill(0.0)
            stateZ2[ch].fill(0.0)
        }
    }

    override fun onReset() {
        coefB0 = DoubleArray(0); coefB1 = DoubleArray(0); coefB2 = DoubleArray(0)
        coefA1 = DoubleArray(0); coefA2 = DoubleArray(0)
        stateZ1 = emptyArray(); stateZ2 = emptyArray()
        bandCount = 0
        scratch = ShortArray(0)
    }
}
