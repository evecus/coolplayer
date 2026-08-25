package com.coolplayer.music.player.audiofx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 单个双二阶（Biquad）IIR 滤波器，系数公式来自 RBJ Audio EQ Cookbook。
 * 支持三种类型：Peaking（钟形增益/衰减）、Low Shelf、High Shelf。
 *
 * 每个滤波器维护自己独立的历史样本（x1,x2,y1,y2），因此每个声道需要一个独立实例。
 */
class BiquadFilter {

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // 直接型 II 转置结构的状态变量
    private var z1 = 0.0
    private var z2 = 0.0

    /**
     * 根据频段参数重新计算滤波器系数。
     * @param sampleRateHz 当前音频采样率
     */
    fun configure(band: EqBand, sampleRateHz: Int) {
        val fs = sampleRateHz.toDouble()
        val fc = band.freqHz.toDouble().coerceIn(10.0, fs / 2.0 - 1.0)
        val q = band.q.toDouble().coerceAtLeast(0.05)
        val gainDb = band.gainDb.toDouble()

        val a = 10.0.pow(gainDb / 40.0) // sqrt(10^(gainDb/20))
        val w0 = 2.0 * PI * fc / fs
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val alpha = sinw0 / (2.0 * q)

        var a0: Double
        when (band.type) {
            FilterType.PK -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosw0
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosw0
                a2 = 1.0 - alpha / a
            }
            FilterType.LSC -> {
                val sqrtA = sqrt(a)
                val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                b0 = a * ((a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha)
                b1 = 2.0 * a * ((a - 1) - (a + 1) * cosw0)
                b2 = a * ((a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha)
                a0 = (a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha
                a1 = -2.0 * ((a - 1) + (a + 1) * cosw0)
                a2 = (a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha
            }
            FilterType.HSC -> {
                val sqrtA = sqrt(a)
                val twoSqrtAAlpha = 2.0 * sqrtA * alpha
                b0 = a * ((a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha)
                b1 = -2.0 * a * ((a - 1) + (a + 1) * cosw0)
                b2 = a * ((a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha)
                a0 = (a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha
                a1 = 2.0 * ((a - 1) - (a + 1) * cosw0)
                a2 = (a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha
            }
        }

        // 归一化，使 a0 = 1
        b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
    }

    /** 重置滤波器内部状态（切歌 / seek 时可选调用，避免残留样本产生咔哒声）。 */
    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** 处理单个采样点（Direct Form II Transposed），输入输出均为 [-1, 1] 归一化浮点样本。 */
    fun process(input: Double): Double {
        val out = b0 * input + z1
        z1 = b1 * input - a1 * out + z2
        z2 = b2 * input - a2 * out
        return out
    }
}
