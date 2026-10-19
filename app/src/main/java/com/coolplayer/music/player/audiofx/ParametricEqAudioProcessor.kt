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
 * 计算路径（按优先级）：
 *  1. **native biquad**（[NativeBiquad]，NDK C 实现）：逐样本 DF2T 级联在 C 层
 *     完成，无 JVM 边界检查、编译器可自动向量化。44.1kHz 立体声 10 段下 CPU
 *     占用约为 Kotlin 循环的 1/5~1/10，与 Salt Player 的 BASS C 层 BFX_BQF
 *     同量级。算法与 Kotlin 版逐样本一致（主机端 gcc + JNI 端到端比对验证：
 *     mono/stereo/5.1 × 1/3/10 段 × 增益截断 × 多种 buffer 大小全部 MATCH）。
 *     库不可用（so 未打包、ABI 缺失）时自动回退，行为不变。
 *  2. **Kotlin 循环**：兜底路径。滤波器系数与状态均打平为 primitive 数组，
 *     循环内纯下标访问（早期实现对 BiquadFilter 对象数组逐样本调用，
 *     是"开 EQ 后 CPU 从 20% 涨到 40%"的主因）。
 *
 * Bypass 机制（对应"原声 preset 仍占 CPU"的排查结论）：
 *  已实证 media3 1.4.1：DefaultAudioSink.processBuffers 在
 *  `audioProcessingPipeline.isOperational() == false` 时直接把输入 buffer
 *  写 AudioTrack，完全绕过处理链；而 pipeline 的 active 列表由各 processor
 *  的 `isActive()` 在 configure 时决定。因此 [isActive] 返回 false 时
 *  （无有效频段且 preamp 为 0，即"原声"），本处理器对音频链路零开销——
 *  无 buffer 分配、无拷贝、无逐样本循环。
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

    /**
     * UI 侧 [setPreset] 的即时快照：该 preset 是否包含有效频段/增益
     * （与 [rebuildFilters] 的剔除阈值一致，但**不等 queueInput**，
     * setPreset 当刻即更新）。
     *
     * 两个"是否生效"状态的分工：
     * - [isActive]：处理器对 media3 pipeline 的 bypass 声明（读**已生效**的
     *   activePreset/bandCount，queueInput 边界才更新）；
     * - [eqActiveRequested]：offload/bypass 渲染器决策用（读**用户意图**，
     *   供 EqAwareMediaCodecAudioRenderer.shouldUseBypass 在 decoder init
     *   边界实时判断，避免 offload 旁路处理器链导致 EQ 静默失效）。
     */
    @Volatile var eqActiveRequested: Boolean = false
        private set

    // 打平后的滤波器系数（所有声道共用同一组系数，因为左右声道用相同 EQ 曲线）。
    // 扁平布局：每段 5 个 double，依次 b0,b1,b2,a1,a2 —— native 与 Kotlin 共用。
    private var coeffs = DoubleArray(0)

    // 每声道每 band 的滤波器状态（Direct Form II Transposed 的 z1/z2），
    // 扁平布局 [ch * bandCount + band] —— native 与 Kotlin 共用。
    private var stateZ1 = DoubleArray(0)
    private var stateZ2 = DoubleArray(0)

    // 复用的临时缓冲区，避免每次 queueInput 都重新分配 ShortArray。
    private var scratch = ShortArray(0)

    /** 供 UI 层调用：切换预设（音效风格 / 耳机型号）。 */
    fun setPreset(preset: EqPreset) {
        _currentPreset.value = preset
        pendingPreset = preset
        eqActiveRequested = isPresetEffective(preset)
    }

    /** preset 是否含有效频段/增益（与 rebuildFilters 的剔除阈值一致）。 */
    private fun isPresetEffective(preset: EqPreset): Boolean =
        preset.bands.any { kotlin.math.abs(it.gainDb) >= BAND_DROP_THRESHOLD_DB } ||
            preset.preampDb != 0f

    /**
     * 原声（无有效频段且 preamp 为 0）时返回 false，让 media3 的
     * AudioProcessingPipeline 在 configure 阶段把本处理器移出 active 列表，
     * 此后 processBuffers 直接透传输入 buffer，本处理器零开销。
     *
     * 注意：onConfigure 仍必须返回有效 format（返回 NOT_SET 会抛
     * UnhandledAudioFormatException 导致播放失败），bypass 完全依赖本方法。
     * 播放启动时 preset 已是原声的场景全程 bypass；运行中切到原声的瞬时
     * 仍走透传快路（见 queueInput 注释），待下次 configure（切歌/重播）后 bypass。
     */
    override fun isActive(): Boolean = bandCount > 0 || preampLinearGain != 1.0

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
        // |增益| < 0.05dB 的频段直接剔除：该量级远低于人耳可闻阈值（约 0.5-1dB），
        // 对听感无影响，但能为 AutoEQ 这类"大量微调频段"的曲线省掉可观的
        // 每样本开销；全部频段都被剔除且 preamp 为 0 时整链 bypass（见 isActive）。
        val kept = preset.bands.filter { kotlin.math.abs(it.gainDb) >= BAND_DROP_THRESHOLD_DB }
        bandCount = kept.size

        coeffs = DoubleArray(bandCount * 5)
        for (i in 0 until bandCount) {
            val f = BiquadFilter().apply { configure(kept[i], sampleRateHz) }
            val c = f.exportCoefficients()
            coeffs[i * 5] = c[0]; coeffs[i * 5 + 1] = c[1]; coeffs[i * 5 + 2] = c[2]
            coeffs[i * 5 + 3] = c[3]; coeffs[i * 5 + 4] = c[4]
        }
        stateZ1 = DoubleArray(channelCount * bandCount)
        stateZ2 = DoubleArray(channelCount * bandCount)
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

        if (bandCount == 0 && preampLinearGain == 1.0) {
            // 原声透传：无任何频段且无增益时直接搬移 buffer。
            // （若启动时即原声，isActive()=false 会被 pipeline 整链 bypass，
            //  根本不会走到这里；这条路径覆盖运行中切到原声的瞬时。）
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

        if (NativeBiquad.available) {
            // native 路径：一次 JNI 调用处理整段（含逐样本 biquad 与增益）。
            // so 缺失/ABI 不匹配时 available=false，自动走下方 Kotlin 兜底。
            try {
                NativeBiquad.processInterleaved(
                    scratch, nCh, nBands, coeffs, stateZ1, stateZ2, gain
                )
            } catch (e: UnsatisfiedLinkError) {
                // 极端情况（如 64 位库被剥离）：本段回退 Kotlin，后续仍尝试 native。
                processKotlin(scratch, sampleCount, nCh, nBands, gain)
            }
        } else {
            processKotlin(scratch, sampleCount, nCh, nBands, gain)
        }

        val out = outputBuffer.asShortBuffer()
        out.put(scratch, 0, sampleCount)
        inputBuffer.position(inputBuffer.position() + remaining)
        outputBuffer.position(sampleCount * 2)
        outputBuffer.flip()
    }

    /**
     * Kotlin 兜底滤波循环（native 不可用时）。与 native 算法逐样本一致：
     * Direct Form II Transposed，归一化 /32768.0，回写 *32767.0 + 截断。
     */
    private fun processKotlin(
        buf: ShortArray, sampleCount: Int, nCh: Int, nBands: Int, gain: Double
    ) {
        val framesTotal = sampleCount / nCh
        var frameIdx = 0
        var idx = 0
        while (frameIdx < framesTotal) {
            var ch = 0
            while (ch < nCh) {
                var sample = buf[idx].toDouble() / 32768.0
                val stateBase = ch * nBands
                var band = 0
                while (band < nBands) {
                    val c = band * 5
                    val out = coeffs[c] * sample + stateZ1[stateBase + band]
                    stateZ1[stateBase + band] =
                        coeffs[c + 1] * sample - coeffs[c + 3] * out + stateZ2[stateBase + band]
                    stateZ2[stateBase + band] = coeffs[c + 2] * sample - coeffs[c + 4] * out
                    sample = out
                    band++
                }
                sample *= gain
                val clamped = if (sample > 1.0) 1.0 else if (sample < -1.0) -1.0 else sample
                buf[idx] = (clamped * 32767.0).toInt().toShort()
                idx++
                ch++
            }
            frameIdx++
        }
    }

    override fun onFlush() {
        // seek / 切歌时清空滤波器历史状态，避免上一段音频的残留样本产生咔哒声
        stateZ1.fill(0.0)
        stateZ2.fill(0.0)
    }

    override fun onReset() {
        coeffs = DoubleArray(0)
        stateZ1 = DoubleArray(0)
        stateZ2 = DoubleArray(0)
        bandCount = 0
        scratch = ShortArray(0)
    }

    private companion object {
        /** 低于该增益的频段视为"未调整"，剔除（听感无影响，省每样本开销）。 */
        const val BAND_DROP_THRESHOLD_DB = 0.05f
    }
}
