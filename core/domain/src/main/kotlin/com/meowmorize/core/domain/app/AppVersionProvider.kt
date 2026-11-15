package com.meowmorize.core.domain.app

/** 当前安装包的版本号，供「检查更新」比对。 */
interface AppVersionProvider {
    /** 形如 `1.0.4`；读取失败时返回 `0`，保证比价永远能跑完。 */
    fun versionName(): String
}
