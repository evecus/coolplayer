package com.coolplayer.music

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.StorageService

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        context = this
        StorageService.init(this)
        database = AppDatabase.get(this)
    }

    /**
     * 自定义 Coil 的 [ImageLoader]：显式限制内存缓存占用可用内存的 20%
     * （Coil 默认是 25%，专辑封面数量多但单张不需要太大缓存空间时适当
     * 调低，避免在低内存设备上因为图片缓存挤占过多内存导致其他部分被
     * 系统回收）。全项目所有封面展示（歌曲列表、专辑网格、播放页、
     * 迷你播放条）统一走这一个 ImageLoader 实例，共享同一份内存缓存，
     * 同一张封面在不同页面复用时不会重复解码。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .crossfade(false)
            .build()
    }

    companion object {
        lateinit var context: Context
            private set
        lateinit var database: AppDatabase
            private set
    }
}
