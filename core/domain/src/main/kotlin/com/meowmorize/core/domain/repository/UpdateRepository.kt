package com.meowmorize.core.domain.repository

import com.meowmorize.core.model.UpdateInfo

/**
 * 版本信息源。
 *
 * 只做「读最新版本」这一件事：不下载、不安装、不写盘。
 * 网络或解析失败直接抛异常，怎么提示由调用方决定。
 */
interface UpdateRepository {
    suspend fun latest(): UpdateInfo
}
