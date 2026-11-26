package com.coolplayer.music.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 媒体权限工具。
 *
 * Android 13+：READ_MEDIA_AUDIO
 * Android 13 以下：READ_EXTERNAL_STORAGE
 */
object PermissionUtil {

    fun hasAudioPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 兼容旧用法。Salt Music 仅需要音频权限即可。 */
    fun hasMediaPermissions(context: Context): Boolean = hasAudioPermission(context)

    fun audioPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /** 兼容旧用法。 */
    fun mediaPermissions(): Array<String> = audioPermissions()

    fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return false
    }

    /**
     * 打开「所有文件访问权限」设置。
     *
     * Android TV 的系统设置往往被裁剪，ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
     * 这类页面可能根本不存在，直接 startActivity 会抛 ActivityNotFoundException，
     * 被原先的 runCatching 吞掉后就表现为「点了没反应」。
     * 这里改为逐级回退，确保至少能跳到一个存在且有意义的设置页。
     */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 1) 本应用的「所有文件访问」开关页
            val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (startIfResolvable(context, appIntent)) return

            // 2) 「所有文件访问」总开关页
            val allIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            if (startIfResolvable(context, allIntent)) return
        }

        // 3) 应用详情页（TV 上基本都存在，用户可在其中查看权限项）
        val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        if (startIfResolvable(context, detailsIntent)) return

        // 4) 兜底：系统设置主页
        startIfResolvable(context, Intent(Settings.ACTION_SETTINGS))
    }

    /** 仅当系统里存在能处理该 Intent 的 Activity 时才启动，避免异常被吞掉变成「没反应」。 */
    private fun startIfResolvable(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openAppSettingsPage(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
