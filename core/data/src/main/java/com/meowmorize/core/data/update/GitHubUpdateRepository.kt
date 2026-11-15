package com.meowmorize.core.data.update

import com.meowmorize.core.domain.repository.UpdateRepository
import com.meowmorize.core.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 GitHub Releases 读最新版本。
 *
 * 为什么选它：项目本来就发在 GitHub，不需要额外维护服务端；`releases/latest`
 * 只返回正式版，草稿与预发布不会出现在里面，正好符合「只提示正式更新」的语义。
 *
 * 两点实现注意：
 * - 未认证调用有 60 次/小时/IP 的限制，所以调用方（启动检查）按 6 小时节流；
 * - 这里用 HttpURLConnection + org.json，不引新依赖——为了一个下拉版本号引入
 *   Retrofit/Moshi 不值得，也会让构建多出两条供应链。
 */
@Singleton
class GitHubUpdateRepository @Inject constructor() : UpdateRepository {

    override suspend fun latest(): UpdateInfo = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // 403 多半是限流；说人话比抛数字有用
                throw IOException(
                    when (code) {
                        403, 429 -> "请求太频繁，稍后再试"
                        404 -> "还没有发布任何版本"
                        else -> "服务器返回 $code"
                    },
                )
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            parse(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(raw: String): UpdateInfo {
        val json = runCatching { JSONObject(raw) }
            .getOrElse { throw IOException("发布信息格式不对") }

        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) throw IOException("发布信息缺少版本号")

        val assets = json.optJSONArray("assets")
        val apkUrl = assets?.let { array ->
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotBlank() && it.startsWith("http") }
        }

        return UpdateInfo(
            versionName = tag.removePrefix("v").removePrefix("V"),
            notes = json.optString("body").trim().ifBlank { "（这个版本没有写说明）" },
            releaseUrl = json.optString("html_url").ifBlank { RELEASES_PAGE },
            downloadUrl = apkUrl,
        )
    }

    private companion object {
        const val ENDPOINT = "https://api.github.com/repos/feitianwuhu/Meowmorize/releases/latest"
        const val RELEASES_PAGE = "https://github.com/feitianwuhu/Meowmorize/releases"
        const val USER_AGENT = "Meowmorize-Android"
        const val TIMEOUT_MS = 10_000
    }
}
