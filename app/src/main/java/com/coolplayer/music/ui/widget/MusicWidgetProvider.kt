package com.coolplayer.music.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import com.coolplayer.music.MainActivity
import com.coolplayer.music.R
import com.coolplayer.music.player.PlayerConnection

/**
 * 桌面小部件 4x2：
 *
 * - 显示封面 / 标题 / 歌手
 * - 上一首 / 播放暂停 / 下一首 按钮
 *
 * 由 [PlaybackService] 在播放状态变化时调用 [refresh] 触发更新。
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV -> PlayerConnection.player.value?.prev()
            ACTION_PLAY_PAUSE -> PlayerConnection.player.value?.togglePlay()
            ACTION_NEXT -> PlayerConnection.player.value?.next()
        }
        refresh(context)
    }

    companion object {
        const val ACTION_PREV = "com.coolplayer.music.widget.PREV"
        const val ACTION_PLAY_PAUSE = "com.coolplayer.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.coolplayer.music.widget.NEXT"

        /** 由 PlaybackService 在状态变化时调用，刷新所有已添加的小部件。 */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
            ids.forEach { id -> updateWidget(context, mgr, id) }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            val player = PlayerConnection.player.value

            views.setTextViewText(
                R.id.widget_title,
                player?.title?.value?.takeIf { it.isNotEmpty() } ?: "Cool Player"
            )
            views.setTextViewText(R.id.widget_artist, player?.artist?.value ?: "")

            val coverBytes = player?.coverBytes?.value
            if (coverBytes != null) {
                val bmp = runCatching {
                    BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                }.getOrNull()
                if (bmp != null) {
                    views.setImageViewBitmap(R.id.widget_cover, bmp)
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            }

            val isPlaying = player?.isPlaying?.value ?: false
            views.setImageViewResource(
                R.id.widget_play,
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

            val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PREV
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val playIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val openIntent = Intent(context, MainActivity::class.java)

            views.setOnClickPendingIntent(
                R.id.widget_prev,
                PendingIntent.getBroadcast(context, 1, prevIntent, flag)
            )
            views.setOnClickPendingIntent(
                R.id.widget_play,
                PendingIntent.getBroadcast(context, 2, playIntent, flag)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                PendingIntent.getBroadcast(context, 3, nextIntent, flag)
            )
            views.setOnClickPendingIntent(
                R.id.widget_cover,
                PendingIntent.getActivity(context, 0, openIntent, flag)
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    /** manifest 中注册的 ActionReceiver 占位类（用于接收按钮点击广播）。 */
    class ActionReceiver : AppWidgetProvider()
}
