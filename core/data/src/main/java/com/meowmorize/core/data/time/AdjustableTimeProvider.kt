package com.meowmorize.core.data.time

import android.content.Context
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.TimeTravelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 可调时钟：真实时间 + 持久化偏移。
 *
 * 为什么用 SharedPreferences 而不是 DataStore：偏移必须在**第一次读取时钟之前**
 * 同步可用（Hilt 提供 TimeProvider 时就会有人调用 `today()`），
 * DataStore 是异步 API，会引入「启动瞬间算错日期」的窗口期。
 * 这个开关只服务调试与体验，没有事务需求，用同步存储是合理取舍。
 */
@Singleton
class AdjustableTimeProvider @Inject constructor(
    @ApplicationContext context: Context,
) : TimeProvider, TimeTravelRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val offsetMillis = AtomicLong(prefs.getLong(KEY_OFFSET, 0L))

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override val clockChanges: Flow<Unit> = changes.asSharedFlow()

    override fun now(): Instant = Instant.ofEpochMilli(System.currentTimeMillis() + offsetMillis.get())

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun offsetMillis(): Long = offsetMillis.get()

    override fun virtualNow(): Instant = now()

    override suspend fun advanceBy(duration: Duration) {
        applyOffset(offsetMillis.get() + duration.toMillis())
    }

    override suspend fun jumpTo(target: Instant) {
        applyOffset(target.toEpochMilli() - System.currentTimeMillis())
    }

    override suspend fun reset() {
        applyOffset(0L)
    }

    private fun applyOffset(value: Long) {
        offsetMillis.set(value)
        prefs.edit().putLong(KEY_OFFSET, value).apply()
        changes.tryEmit(Unit)
    }

    private companion object {
        const val PREFS_NAME = "meow_clock"
        const val KEY_OFFSET = "offset_millis"
    }
}
