package com.meowmorize.core.data.wallpaper

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.meowmorize.core.domain.repository.DailyWallpaper
import com.meowmorize.core.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 微软 Bing 每日壁纸。
 *
 * 选它的原因：每天一张、有官方 JSON 接口、横向风景为主，做背景不挑主题；
 * 而且 `cn.bing.com` 在国内通常可达，不需要翻墙。
 *
 * 分辨率按屏幕挑：宽屏机型取 UHD，普通机型取 1920×1080——
 * 反正最终会模糊 + 加遮罩，4K 原图只是白占内存。
 */
@Singleton
class BingWallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WallpaperRepository {

    private val dir: File
        get() = File(context.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }

    private fun imageFile(date: String) = File(dir, "wallpaper-$date.jpg")

    private fun metaFile(date: String) = File(dir, "meta-$date.json")

    override suspend fun today(): DailyWallpaper = withContext(Dispatchers.IO) {
        cachedMeta(todayKey()) ?: fetchMeta()
    }

    override fun cached(): Pair<DailyWallpaper, File>? {
        val key = todayKey()
        val meta = cachedMeta(key) ?: return null
        val file = imageFile(key)
        return if (file.exists() && file.length() > 0) meta to file else null
    }

    override suspend fun ensureDownloaded(): Pair<DailyWallpaper, File>? = withContext(Dispatchers.IO) {
        cached()?.let { return@withContext it }
        runCatching {
            val meta = fetchMeta()
            val key = todayKey()
            val target = imageFile(key)
            download(meta.sourceUrl, target)
            saveMeta(key, meta)
            // 只留当天一张，免得占满存储
            dir.listFiles()?.forEach { file ->
                if (file.name != target.name && file.name != metaFile(key).name) file.delete()
            }
            meta to target
        }.getOrNull()
    }

    override suspend fun exportToDownloads(
        metadata: DailyWallpaper,
        file: File,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "Meowmorize-壁纸-${metadata.date}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/Meowmorize",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: return@runCatching null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            name
        }.getOrNull()
    }

    // ---------- 元数据 ----------

    private fun todayKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun cachedMeta(key: String): DailyWallpaper? {
        val file = metaFile(key)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            DailyWallpaper(
                date = key,
                title = json.optString("title"),
                copyright = json.optString("copyright"),
                sourceUrl = json.optString("url"),
            )
        }.getOrNull()?.takeIf { it.sourceUrl.isNotBlank() }
    }

    private fun saveMeta(key: String, meta: DailyWallpaper) {
        runCatching {
            metaFile(key).writeText(
                JSONObject()
                    .put("title", meta.title)
                    .put("copyright", meta.copyright)
                    .put("url", meta.sourceUrl)
                    .toString(),
            )
        }
    }

    private fun fetchMeta(): DailyWallpaper {
        val raw = httpGet(ENDPOINT)
        val json = runCatching { JSONObject(raw) }
            .getOrElse { throw IOException("壁纸信息格式不对") }
        val image = json.optJSONArray("images")?.optJSONObject(0)
            ?: throw IOException("今天没有壁纸")
        val urlbase = image.optString("urlbase")
        if (urlbase.isBlank()) throw IOException("壁纸地址缺失")

        return DailyWallpaper(
            date = todayKey(),
            title = image.optString("title").ifBlank { "每日壁纸" },
            copyright = image.optString("copyright"),
            sourceUrl = HOST + urlbase + resolutionSuffix(),
        )
    }

    /** 按屏幕宽度挑分辨率：宽屏取 UHD，其余 1080p 足够（反正要模糊）。 */
    private fun resolutionSuffix(): String = runCatching {
        val metrics = context.resources.displayMetrics
        if (maxOf(metrics.widthPixels, metrics.heightPixels) >= 1400) "_UHD.jpg" else "_1920x1080.jpg"
    }.getOrDefault("_1920x1080.jpg")

    // ---------- 网络 ----------

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("服务器返回 ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("下载失败 ${connection.responseCode}")
            }
            val temp = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input ->
                temp.outputStream().use { out -> input.copyTo(out) }
            }
            // 顺手校验一下确实是图片，避免把错误页当壁纸缓存下去
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0) {
                temp.delete()
                throw IOException("下载到的不是图片")
            }
            if (target.exists()) target.delete()
            temp.renameTo(target)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HOST = "https://cn.bing.com"
        const val ENDPOINT = "$HOST/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN"
        const val USER_AGENT = "Meowmorize-Android"
    }
}
