package com.coolplayer.music

import android.app.Application
import android.content.Context
import com.coolplayer.music.data.AppDatabase
import com.coolplayer.music.data.StorageService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this
        StorageService.init(this)
        database = AppDatabase.get(this)
    }

    companion object {
        lateinit var context: Context
            private set
        lateinit var database: AppDatabase
            private set
    }
}
