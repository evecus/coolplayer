package com.coolplayer.music.player.audiofx

/**
 * native biquad 的 JNI 封装。
 *
 * 对应 `app/src/main/cpp/biquad_eq.c`，算法与 Kotlin 版逐样本一致
 * （有主机端 gcc 逐样本比对验证）。库缺失（如 so 未打进包、ABI 不匹配）
 * 时 [available] 为 false，调用方自动回退 Kotlin 实现，行为不变。
 */
internal object NativeBiquad {

    /**
     * native 库是否可用。在类加载时探测一次，[UnsatisfiedLinkError] 不抛给调用方。
     */
    val available: Boolean = try {
        System.loadLibrary("biquad_eq")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    } catch (e: SecurityException) {
        false
    }

    /**
     * 原地处理一段 interleaved PCM16 采样。
     *
     * @param samples    interleaved 采样数组（原地修改）
     * @param channels   声道数
     * @param bandCount  biquad 段数，0 表示只做增益
     * @param coeffs     扁平系数，长度 bandCount*5，每段依次 b0,b1,b2,a1,a2
     * @param stateZ1    滤波器状态，长度 channels*bandCount，布局 [ch*bandCount + band]
     * @param stateZ2    同 [stateZ1]
     * @param gain       preamp 线性增益
     */
    external fun processInterleaved(
        samples: ShortArray,
        channels: Int,
        bandCount: Int,
        coeffs: DoubleArray,
        stateZ1: DoubleArray,
        stateZ2: DoubleArray,
        gain: Double
    )
}
