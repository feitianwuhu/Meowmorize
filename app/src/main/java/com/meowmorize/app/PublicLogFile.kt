package com.meowmorize.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * 把一份文本写到系统「下载」目录。
 *
 * 存在的理由很具体：用户的手机不接电脑、也不能开无线调试，日志只能靠
 * 「应用自己写到公共目录 → 用户在文件管理器里打开」这条路带出来。
 *
 * 同名文件存在时原地覆盖，避免每次启动都在「下载」里堆一串副本。
 * 全部失败也只会静默返回：这是排障辅助，不该反过来把应用弄崩。
 */
internal object PublicLogFile {

    fun publish(context: Context, displayName: String, content: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            val existingId = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(displayName),
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            if (existingId != null) {
                val uri = Uri.withAppendedPath(collection, existingId.toString())
                resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
                return@runCatching
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(collection, values) ?: return@runCatching
            resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
        }
    }
}
