package com.meowmorize.core.domain.repository

import java.time.Instant

/** 写进系统日历的一条事件（与任务一一对应）。 */
data class CalendarSyncEvent(
    val itemId: Long,
    val title: String,
    val description: String,
    val startAt: Instant,
    val minutes: Int,
)

data class CalendarSyncResult(
    val synced: Int = 0,
    val removed: Int = 0,
    val permissionDenied: Boolean = false,
    val failed: Boolean = false,
)

/**
 * 系统日历适配器。
 *
 * 抽出接口是为了让「同步逻辑」留在领域层可测，而 ContentResolver 这类平台细节
 * 只出现在 `:core:data` 的实现里。
 */
interface CalendarSyncRepository {
    fun hasPermission(): Boolean

    /** 幂等同步：事件按 itemId 复用，已不在窗口内的事件会被删除。 */
    suspend fun sync(events: List<CalendarSyncEvent>): CalendarSyncResult

    /** 清空本应用写入的日历事件（关闭同步或重置时使用）。 */
    suspend fun clear(): CalendarSyncResult
}
