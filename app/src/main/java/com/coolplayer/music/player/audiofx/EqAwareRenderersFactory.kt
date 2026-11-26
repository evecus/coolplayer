package com.coolplayer.music.player.audiofx

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * 自定义 RenderersFactory：把 [eqProcessor] 接入 ExoPlayer 的音频输出链路，
 * 并把默认的 MediaCodecAudioRenderer 替换为 EQ 感知的
 * [EqAwareMediaCodecAudioRenderer]（offload 与 EQ 互斥，见其注释）。
 *
 * 背景：Media3 的 `DefaultRenderersFactory` **没有**公开的 `setAudioSinkFactory` 方法
 * （这是本项目早期版本的错误假设）。真正的扩展点是 `protected` 方法
 * `buildAudioSink(Context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams): AudioSink`，
 * 通过反编译 media3-exoplayer-1.4.1.aar 核实。因此这里改为继承 + 重写。
 *
 * `buildAudioRenderers` 同为 protected 扩展点（签名已对 1.4.1 aar 核实）：
 * 官方实现仅做 `new MediaCodecAudioRenderer(...)` + 反射加载 decoder 扩展
 * （MidiRenderer / FFmpeg 等）。本项目未引入任何 media3 decoder 扩展依赖，
 * 因此重写时无需复制扩展加载逻辑——与官方默认 `EXTENSION_RENDERER_MODE_OFF`
 * 行为完全一致。
 *
 * 参数签名（已核实，media3-exoplayer 1.4.1）：
 *   protected void buildAudioRenderers(Context, int extensionRendererMode,
 *       MediaCodecSelector, boolean enableDecoderFallback, AudioSink,
 *       Handler, AudioRendererEventListener, ArrayList<Renderer>)
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

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        out.add(
            EqAwareMediaCodecAudioRenderer(
                context = context,
                codecAdapterFactory = codecAdapterFactory,
                mediaCodecSelector = mediaCodecSelector,
                enableDecoderFallback = enableDecoderFallback,
                eventHandler = eventHandler,
                eventListener = eventListener,
                audioSink = audioSink
            ) { eqProcessor.eqActiveRequested }
        )
        // extensionRendererMode != OFF 时官方会反射加载 MidiRenderer / FFmpeg
        // 音频扩展；本项目无相关依赖，与官方默认（OFF）行为一致，不做处理。
    }
}
