package com.meowmorize.core.domain.usecase

import com.meowmorize.core.domain.app.AppVersionProvider
import com.meowmorize.core.domain.repository.UpdateRepository
import com.meowmorize.core.model.UpdateInfo

/** 「检查更新」的三种结果。三种就够，不引入更多状态。 */
sealed interface UpdateCheckResult {

    val currentVersion: String

    /** 已经是最新版本。 */
    data class UpToDate(override val currentVersion: String) : UpdateCheckResult

    /** 远端有更新的版本。 */
    data class Available(
        override val currentVersion: String,
        val update: UpdateInfo,
    ) : UpdateCheckResult

    /** 查不了（断网、限流、解析失败）。 */
    data class Failed(
        override val currentVersion: String,
        val message: String,
    ) : UpdateCheckResult
}

/**
 * 比对远端最新版本与当前安装版本。
 *
 * 三条设计约束：
 * - **不自动安装**。这里只回答「有没有新版」，装不装是用户的事。
 * - **不向上抛异常**。检查更新失败不该让设置页崩掉，一律收敛成 [UpdateCheckResult.Failed]。
 * - **版本比较按数字段**，不假设一定三段（`1.0.5` 与 `1.0.5.1` 都能比），
 *   并忽略 `v` 前缀与 `-beta` 之类的后缀。
 */
class CheckUpdateUseCase(
    private val updateRepository: UpdateRepository,
    private val appVersionProvider: AppVersionProvider,
) {

    suspend operator fun invoke(): UpdateCheckResult {
        val current = appVersionProvider.versionName()
        val latest = runCatching { updateRepository.latest() }
            .getOrElse { error ->
                return UpdateCheckResult.Failed(
                    currentVersion = current,
                    message = error.message?.takeIf { it.isNotBlank() } ?: "网络不可用",
                )
            }
        return if (isNewer(latest.versionName, current)) {
            UpdateCheckResult.Available(current, latest)
        } else {
            UpdateCheckResult.UpToDate(current)
        }
    }

    companion object {

        /** remote 是否比 current 新。 */
        fun isNewer(remote: String, current: String): Boolean {
            val a = numbers(remote)
            val b = numbers(current)
            val size = maxOf(a.size, b.size)
            for (index in 0 until size) {
                val left = a.getOrElse(index) { 0 }
                val right = b.getOrElse(index) { 0 }
                if (left != right) return left > right
            }
            return false
        }

        /** "v1.2.3-beta" → [1, 2, 3] */
        private fun numbers(version: String): List<Int> =
            version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .takeWhile { it.isDigit() || it == '.' }
                .split('.')
                .mapNotNull(String::toIntOrNull)
    }
}
