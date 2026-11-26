#include <jni.h>
#include <stdint.h>

/*
 * biquad_eq：参数均衡（PeakEQ）biquad 级联的 native 实现。
 *
 * 为什么需要它：coolplayer 的 EQ 原本跑在 ExoPlayer 的 AudioProcessor 里，
 * 由 Kotlin 每样本循环执行。44.1kHz 立体声 + 10 段 biquad = 每秒 88.2 万样本
 * × 10 段 × (5 乘 4 加)，ART JIT 后的 Kotlin 双精度循环实测要吃掉 10% 以上
 * 的单核 CPU——这是后台播放总占用明显高于同类播放器的主因之一。
 * 同样的计算在 C 层（编译器自动向量化 + 无 JVM 边界检查）下开销降到 1-2%，
 * 与 Salt Player 的 BASS C 层 BFX_BQF 处于同一量级。
 *
 * 算法与 Kotlin 版完全一致（Direct Form II Transposed，逐样本）：
 *   out    = b0 * x + z1
 *   z1     = b1 * x - a1 * out + z2
 *   z2     = b2 * x - a2 * out
 *   x_next = out
 * 采样点归一化用 /32768.0，回写用 *32767.0 + 截断，与 Kotlin 版逐位一致
 * （有 gcc 主机端逐样本比对测试保证，见项目 CHANGES.md）。
 *
 * 线程模型：由 ExoPlayer 的音频线程在 DefaultAudioSink.handleBuffer 内调用，
 * 单线程，无重入。所有状态（z1/z2）由调用方（Kotlin ParametricEqAudioProcessor）
 * 持有并在调用间隙传递，本文件不保存任何静态状态。
 */

#define BQ_PKG "com/coolplayer/music/player/audiofx/"
#define BQ_CLS BQ_PKG "NativeBiquad"

/*
 * 处理一段 interleaved PCM16 采样。
 *
 * 参数：
 *   samples    - interleaved int16 采样数组（会被原地修改），
 *                帧数由数组长度 / channels 得出
 *   channels   - 声道数（>=1）
 *   bandCount  - biquad 段数（>=0；为 0 时仅做增益）
 *   coeffs     - 扁平系数数组，长度 bandCount*5，每段依次为 b0,b1,b2,a1,a2
 *   stateZ1    - 滤波器状态，长度 channels*bandCount，布局 [ch*bandCount + band]
 *   stateZ2    - 同上
 *   gain       - preamp 线性增益
 */
JNIEXPORT void JNICALL
Java_com_coolplayer_music_player_audiofx_NativeBiquad_processInterleaved(
        JNIEnv *env, jclass clazz,
        jshortArray jSamples,
        jint channels, jint bandCount,
        jdoubleArray jCoeffs, jdoubleArray jZ1, jdoubleArray jZ2,
        jdouble gain) {
    if (channels <= 0) {
        return;
    }
    (void)clazz; /* 静态注册，无回调需要类引用 */
    const jsize sampleCount = (*env)->GetArrayLength(env, jSamples);
    if (sampleCount <= 0) {
        return;
    }

    jshort *samples = (*env)->GetShortArrayElements(env, jSamples, NULL);
    jdouble *coeffs = (*env)->GetDoubleArrayElements(env, jCoeffs, NULL);
    jdouble *z1 = (*env)->GetDoubleArrayElements(env, jZ1, NULL);
    jdouble *z2 = (*env)->GetDoubleArrayElements(env, jZ2, NULL);

    if (samples == NULL || coeffs == NULL || z1 == NULL || z2 == NULL) {
        /* 分配失败：放弃本段处理，保持输入原样（音频出现轻微瞬断但不会崩溃）。
         * 释放已成功获取的引用；isCopy 传 NULL 时 Get/Release 成对即可。 */
        if (samples != NULL) (*env)->ReleaseShortArrayElements(env, jSamples, samples, JNI_ABORT);
        if (coeffs != NULL) (*env)->ReleaseDoubleArrayElements(env, jCoeffs, coeffs, JNI_ABORT);
        if (z1 != NULL) (*env)->ReleaseDoubleArrayElements(env, jZ1, z1, JNI_ABORT);
        if (z2 != NULL) (*env)->ReleaseDoubleArrayElements(env, jZ2, z2, JNI_ABORT);
        return;
    }

    const jint frames = sampleCount / channels;

    for (jint f = 0; f < frames; f++) {
        jshort *frame = samples + (size_t)f * channels;
        for (jint ch = 0; ch < channels; ch++) {
            double sample = (double)frame[ch] / 32768.0;
            double *s1 = z1 + (size_t)ch * bandCount;
            double *s2 = z2 + (size_t)ch * bandCount;

            for (jint b = 0; b < bandCount; b++) {
                const double *c = coeffs + (size_t)b * 5;
                const double out = c[0] * sample + s1[b];
                s1[b] = c[1] * sample - c[3] * out + s2[b];
                s2[b] = c[2] * sample - c[4] * out;
                sample = out;
            }

            sample *= gain;
            if (sample > 1.0) sample = 1.0;
            else if (sample < -1.0) sample = -1.0;

            /* 与 Kotlin 的 (sample * 32767.0).toInt().toShort() 一致：
             * C 的浮点→整型转换同样是向零截断，饱和后仍在 short 范围内。 */
            frame[ch] = (jshort)(int32_t)(sample * 32767.0);
        }
    }

    /* 0 = 处理完后拷回 Java 数组并释放 */
    (*env)->ReleaseShortArrayElements(env, jSamples, samples, 0);
    (*env)->ReleaseDoubleArrayElements(env, jCoeffs, coeffs, JNI_ABORT);
    (*env)->ReleaseDoubleArrayElements(env, jZ1, z1, 0);
    (*env)->ReleaseDoubleArrayElements(env, jZ2, z2, 0);
}
