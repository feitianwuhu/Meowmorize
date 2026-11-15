package com.meowmorize.core.model

/**
 * 一次「检查更新」拿到的版本信息。
 *
 * 刻意只保留「展示 + 跳转」需要的最小字段：不静默下载、不后台安装，
 * 更不新不新、要不要装，始终由用户自己决定。
 */
data class UpdateInfo(
    /** 远端最新版本号，如 1.0.5（已去掉 tag 的 v 前缀）。 */
    val versionName: String,
    /** 发布说明原文，直接展示给用户看。 */
    val notes: String,
    /** 发布页地址，可用浏览器打开。 */
    val releaseUrl: String,
    /** APK 直链；该版本没有上传安装包时为 null。 */
    val downloadUrl: String? = null,
) {
    /** 优先给安装包直链，没有就退回发布页。 */
    val bestUrl: String get() = downloadUrl ?: releaseUrl
}
