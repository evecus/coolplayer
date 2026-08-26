package com.coolplayer.music.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * 封面解码工具：按目标尺寸做降采样，避免把音频文件内嵌的原始高分辨率封面
 * （常见 1000x1000 甚至更大）直接以 ARGB_8888 完整解码进内存。
 *
 * 例如一张 1200x1200 的封面用 [BitmapFactory.decodeByteArray] 直接解码，
 * 单张就要占约 1200*1200*4 ≈ 5.5MB 内存；歌曲列表一次性为 200 首歌加载
 * 封面时，如果不做采样限制，瞬时内存占用可以轻松突破几百 MB。
 *
 * 仅用于 Compose 树之外、无法使用 Coil 的场景（取色、AppWidget RemoteViews）。
 * Compose 内的封面展示统一改用 Coil 的 AsyncImage，由 Coil 自动完成按需
 * 采样与内存/磁盘缓存，不应再调用本工具类。
 */
object BitmapDecodeUtil {

    /**
     * 解码字节数组为 [Bitmap]，并降采样到长边不超过 [maxDimenPx]。
     * 采用两阶段解码：先只读边界（inJustDecodeBounds）算出合适的 inSampleSize，
     * 再真正解码，避免任何一步产生超出目标尺寸太多的中间大图。
     */
    fun decodeSampled(bytes: ByteArray, maxDimenPx: Int): Bitmap? = runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        val (width, height) = boundsOptions.outWidth to boundsOptions.outHeight
        if (width <= 0 || height <= 0) return@runCatching null

        val sampleSize = calculateInSampleSize(width, height, maxDimenPx, maxDimenPx)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            // 封面用于展示/取色，不需要 alpha 精度，RGB_565 比 ARGB_8888 省一半内存。
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }.getOrNull()

    /**
     * 把音频文件内嵌的原始封面（可能几百 KB 到几 MB 的高分辨率 JPEG/PNG）
     * 降采样并重新编码为一份体积小得多的缩略图 JPEG，用于扫描时写入
     * [com.coolplayer.music.data.SongCoverEntity]（数据库里长期持久化的
     * 封面缓存表）。
     *
     * 这份缩略图**只供歌曲列表展示使用**（列表行封面 40~52dp，3 倍密度下
     * 物理像素约 120~160px）——默认参数故意不追求接近原图的画质，因为用户
     * 在滚动列表时不会放大细看。播放页需要的高画质大图**不使用这份
     * 缩略图**，而是由 [com.coolplayer.music.player.MusicPlayer] 播放时
     * 现读音频文件的原始封面（未压缩），保证最佳画质，反正同一时刻只有
     * 一首歌在播放，现读一次的开销可以接受。
     *
     * 压缩到长边 [maxDimenPx]、JPEG 质量 [quality] 后，单张通常只有 2~5 KB，
     * 2000+ 首歌全部缓存也只占用数据库文件几 MB～十几 MB 磁盘空间，
     * 且不占用常驻内存（内存里只有 Coil 对当前可见列表项的短暂 LRU 缓存）。
     *
     * 解码失败（非图片数据、损坏等）时返回 null，调用方应保留不写入该曲目的封面。
     */
    fun compressForStorage(
        originalBytes: ByteArray,
        maxDimenPx: Int = 160,
        quality: Int = 60
    ): ByteArray? = runCatching {
        val bmp = decodeSampled(originalBytes, maxDimenPx) ?: return@runCatching null
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bmp.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
