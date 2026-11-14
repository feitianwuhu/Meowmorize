package com.meowmorize.core.domain.usecase

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.CalendarSyncEvent
import com.meowmorize.core.domain.repository.CalendarSyncRepository
import com.meowmorize.core.domain.repository.CalendarSyncResult
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.PlannedTask
import java.time.Duration

/**
 * 把「即将要背的内容」写进系统日历。
 *
 * 设计取舍：
 * - **默认关闭**，且开启前必须拿到日历权限，不给用户「莫名其妙多了一堆日程」的惊喜。
 * - 同步是**幂等**的：同一条任务复用同一个事件 id，反复触发不会堆叠。
 * - 只同步窗口内（默认 24 小时）的内容，日历里永远是「接下来要做什么」，
 *   而不是整个任务库的倾倒。
 * - 任务完成/被移出计划后，对应事件会被删除，日历不会留垃圾。
 */
class SyncUpcomingToCalendarUseCase(
    private val settingsRepository: SettingsRepository,
    private val planRepository: PlanRepository,
    private val calendarSyncRepository: CalendarSyncRepository,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(): CalendarSyncResult {
        val settings = settingsRepository.current()
        if (!settings.calendarSyncEnabled) {
            return calendarSyncRepository.clear()
        }
        if (!calendarSyncRepository.hasPermission()) {
            return CalendarSyncResult(permissionDenied = true)
        }

        val now = timeProvider.now()
        val until = now.plus(Duration.ofHours(settings.calendarSyncWindowHours.toLong()))
        val floor = now.plus(Duration.ofMinutes(5))

        val planTasks = planRepository.getPlan(timeProvider.today())
            ?.tasks
            ?.filter { !it.completed }
            .orEmpty()
        val upcoming = planRepository.upcomingTasks(until)

        val events = (planTasks + upcoming)
            .distinctBy { it.item.id }
            .mapNotNull { task -> task.toEvent(floor, until) }

        return calendarSyncRepository.sync(events)
    }

    private fun PlannedTask.toEvent(floor: java.time.Instant, until: java.time.Instant): CalendarSyncEvent? {
        val rawStart = when {
            state.phase == LearningPhase.NEW -> floor.plus(Duration.ofMinutes(10))
            state.dueAt.isBefore(floor) -> floor
            else -> state.dueAt
        }
        if (rawStart.isAfter(until)) return null

        val description = buildString {
            subject?.name?.let { append("学科：").append(it).append('\n') }
            item.source?.takeIf { it.isNotBlank() }?.let { append("来源：").append(it).append('\n') }
            append("预计 ").append(item.estimatedMinutes).append(" 分钟\n\n")
            append(item.content.take(300))
            if (item.content.length > 300) append("…")
        }

        return CalendarSyncEvent(
            itemId = item.id,
            title = "背诵 · ${item.topic}",
            description = description,
            startAt = rawStart,
            minutes = item.estimatedMinutes,
        )
    }
}
