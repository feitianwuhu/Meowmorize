package com.meowmorize.core.domain.repository

import java.io.File

/** 一天的壁纸元数据。 */
data class DailyWallpaper(
    /** yyyy-MM-dd。 */
    val date: String,
    val title: String,
    val copyright: String,
    /** 原图地址，用于「下载今日壁纸」。 */
    val sourceUrl: String,
)

/**
 * 每日壁纸来源（微软 Bing 的每日一图）。
 *
 * 只做「拿到今天的图」这一件事：元数据、下载、缓存、导出到系统下载目录。
 * 具体渲染（铺法、模糊、遮罩）属于 UI 层，不在这里。
 */
interface WallpaperRepository {

    /** 今日壁纸元数据；有当日缓存则直接用，不联网。 */
    suspend fun today(): DailyWallpaper

    /** 本地已缓存的今日壁纸（元数据 + 文件）；没有则 null。 */
    fun cached(): Pair<DailyWallpaper, File>?

    /** 确保今日图已落盘，返回元数据与文件；失败返回 null（不抛异常）。 */
    suspend fun ensureDownloaded(): Pair<DailyWallpaper, File>?

    /** 把今日图导出到系统「下载」目录，返回文件名；失败返回 null。 */
    suspend fun exportToDownloads(
        metadata: DailyWallpaper,
        file: File,
    ): String?
}
