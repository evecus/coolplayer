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
     * 降采样并重新编码为一份体积小得多的 JPEG，用于长期持有在
     * [com.coolplayer.music.data.SongEntry.coverBytes] 里。
     *
     * 背景：coverBytes 是纯内存态字段（不持久化到数据库），扫描完 2000+
     * 首歌后会一直常驻内存；如果直接存原始文件字节，按平均单张 200KB～
     * 1MB 估算，全量加载后可能占用几百 MB 到 GB 级内存。压缩到长边
     * [maxDimenPx]（800px 覆盖了从列表小图标到播放页大图——播放页封面
     * 通常在 800~1200px 物理像素之间——的主要展示需求，肉眼观感与原图
     * 基本无差异）、JPEG 质量 [quality] 后，单张通常能降到几十 KB，
     * 整体内存占用降低一个数量级以上。
     *
     * 解码失败（非图片数据、损坏等）时返回 null，调用方应保留不显示封面。
     */
    fun compressForStorage(
        originalBytes: ByteArray,
        maxDimenPx: Int = 800,
        quality: Int = 82
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
