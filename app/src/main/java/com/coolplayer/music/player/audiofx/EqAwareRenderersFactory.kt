package com.coolplayer.music.player.audiofx

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * 自定义 RenderersFactory：把 [eqProcessor] 接入 ExoPlayer 的音频输出链路。
 *
 * 背景：Media3 的 `DefaultRenderersFactory` **没有**公开的 `setAudioSinkFactory` 方法
 * （这是本项目早期版本的错误假设）。真正的扩展点是 `protected` 方法
 * `buildAudioSink(Context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams): AudioSink`，
 * 通过反编译 media3-exoplayer-1.4.1.aar 核实。因此这里改为继承 + 重写。
 *
 * 参数签名（已核实，media3-exoplayer 1.4.1）：
 *   protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams)
 */
class EqAwareRenderersFactory(
    context: Context,
    private val eqProcessor: ParametricEqAudioProcessor
) : DefaultRenderersFactory(context) {

    private val appContext = context.applicationContext

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(appContext)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(*arrayOf(eqProcessor))
            )
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
