package com.meowmorize.core.domain.usecase

import com.meowmorize.core.domain.app.AppVersionProvider
import com.meowmorize.core.domain.repository.UpdateRepository
import com.meowmorize.core.model.UpdateInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CheckUpdateUseCaseTest {

    private class FakeUpdateRepository(private val result: Result<UpdateInfo>) : UpdateRepository {
        override suspend fun latest(): UpdateInfo = result.getOrThrow()
    }

    private class FakeVersion(private val value: String) : AppVersionProvider {
        override fun versionName(): String = value
    }

    private fun info(version: String) = UpdateInfo(
        versionName = version,
        notes = "notes",
        releaseUrl = "https://example.com/releases",
        downloadUrl = "https://example.com/app.apk",
    )

    // ---------- 版本比较 ----------

    @Test
    fun `remote patch version is newer`() {
        assertTrue(CheckUpdateUseCase.isNewer("1.0.5", "1.0.4"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(CheckUpdateUseCase.isNewer("1.0.4", "1.0.4"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertFalse(CheckUpdateUseCase.isNewer("v1.0.4", "1.0.4"))
        assertTrue(CheckUpdateUseCase.isNewer("V1.0.5", "1.0.4"))
    }

    @Test
    fun `numeric comparison not string comparison`() {
        // 「1.0.10 > 1.0.9」按字符串比会错，必须按数字比
        assertTrue(CheckUpdateUseCase.isNewer("1.0.10", "1.0.9"))
        assertFalse(CheckUpdateUseCase.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `different segment count is handled`() {
        assertTrue(CheckUpdateUseCase.isNewer("2", "1.9.9"))
        assertTrue(CheckUpdateUseCase.isNewer("1.1", "1.0.9"))
        assertTrue(CheckUpdateUseCase.isNewer("1.0.4.1", "1.0.4"))
        assertFalse(CheckUpdateUseCase.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `prerelease suffix is ignored`() {
        assertFalse(CheckUpdateUseCase.isNewer("1.0.4-beta", "1.0.4"))
        assertTrue(CheckUpdateUseCase.isNewer("1.0.5-beta", "1.0.4"))
    }

    // ---------- 用例结果 ----------

    @Test
    fun `newer remote yields Available`() = runTest {
        val useCase = CheckUpdateUseCase(
            updateRepository = FakeUpdateRepository(Result.success(info("1.0.5"))),
            appVersionProvider = FakeVersion("1.0.4"),
        )
        val result = useCase()
        assertTrue(result is UpdateCheckResult.Available)
        result as UpdateCheckResult.Available
        assertEquals("1.0.5", result.update.versionName)
        assertEquals("1.0.4", result.currentVersion)
    }

    @Test
    fun `same remote yields UpToDate`() = runTest {
        val useCase = CheckUpdateUseCase(
            updateRepository = FakeUpdateRepository(Result.success(info("1.0.4"))),
            appVersionProvider = FakeVersion("1.0.4"),
        )
        assertTrue(useCase() is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `network failure yields Failed instead of throwing`() = runTest {
        val useCase = CheckUpdateUseCase(
            updateRepository = FakeUpdateRepository(Result.failure(IOException("网络不可用"))),
            appVersionProvider = FakeVersion("1.0.4"),
        )
        val result = useCase()
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals("网络不可用", (result as UpdateCheckResult.Failed).message)
        assertEquals("1.0.4", result.currentVersion)
    }
}
