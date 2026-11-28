package com.meowmorize.feature.settings.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：下载 APK → 校验 → 交给系统安装器。
 *
 * 三条设计约束：
 * 1. **不静默安装**。Android 上本就不可能静默安装，这里也不尝试绕过：
 *    下载完唤起系统安装器，用户在系统界面确认，这是唯一的正道。
 * 2. **进度可见**。页内有进度条；同时发一条带进度的通知（未授予通知权限时只保留页内进度）。
 * 3. **失败可退**。任何一步失败都返回明确原因，界面同时保留「在浏览器打开」的兜底入口。
 */
class ApkUpdateHelper(private val context: Context) {

    /** 是否允许安装未知来源应用（Android 8+ 需要用户单独授权）。 */
    fun canInstall(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 下载到 cacheDir；进度回调 0f–1f。 */
    suspend fun download(
        url: String,
        versionName: String,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            // 只保留一个包，避免反复下载把缓存撑大
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "Meowmorize-$versionName.apk")

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Meowmorize-Android")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("服务器返回 ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                var read = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            // 校验：至少几 MB 且以 ZIP 头（PK）开头，避免把错误页当安装包
            if (target.length() < 1_000_000) {
                target.delete()
                throw IOException("下载内容不完整")
            }
            val head = ByteArray(2)
            target.inputStream().use { it.read(head) }
            if (head[0] != 0x50.toByte() || head[1] != 0x4B.toByte()) {
                target.delete()
                throw IOException("下载到的不是安装包")
            }
            onProgress(1f)
            target
        }
    }

    /** 唤起系统安装器。 */
    fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 跳到「安装未知应用」授权页。 */
    fun openUnknownSourcesSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 浏览器兜底：直接打开发布页。 */
    fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // ---------- 通知 ----------

    fun notifyProgress(progress: Float, versionName: String) {
        if (!canNotify()) return
        ensureChannel()
        val percent = (progress * 100).toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 Meowmorize $versionName")
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun notifyReady(versionName: String) {
        if (!canNotify()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Meowmorize $versionName 已下载")
            .setContentText("点此安装")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, Class.forName("com.meowmorize.app.MainActivity")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun clearNotification() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun canNotify(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "应用更新",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "下载更新时的进度提示" },
        )
    }

    private companion object {
        const val CHANNEL_ID = "app_update"
        const val NOTIFICATION_ID = 1001
    }
}
