package com.meowmorize.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** 日历里某一天的一条任务。 */
data class CalendarTask(
    val itemId: Long,
    val topic: String,
    val subjectName: String?,
    val subjectColorArgb: Int?,
    val minutes: Int,
    val completed: Boolean,
    val reason: PlanReason?,
    /** 计划外的「预计到期」任务没有具体时间点。 */
    val dueAt: Instant? = null,
    /** 尚未生成当日计划，只是按记忆曲线推算会到期。 */
    val forecast: Boolean = false,
)

/**
 * 日历中的一天。
 *
 * [forecast] 的语义：这一天还没有生成计划（例如未来几天），
 * 数字来自记忆曲线的到期推算，用来让用户提前看到「哪天会比较忙」。
 */
data class CalendarDay(
    val date: LocalDate,
    val tasks: List<CalendarTask> = emptyList(),
    val forecastCount: Int = 0,
    val plannedCount: Int = 0,
    val completedCount: Int = 0,
    val minutes: Int = 0,
    val forecastMinutes: Int = 0,
    val subjectColors: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && forecastCount == 0

    /** 日历格子上的负荷强度 0~1，用于让「红点密集」的日子一眼看出来。 */
    val intensity: Float
        get() {
            val total = minutes + forecastMinutes
            return (total / 90f).coerceIn(0f, 1f)
        }
}

/** 一个月的日历数据。 */
data class MonthCalendar(
    val month: YearMonth,
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
) {
    fun day(date: LocalDate): CalendarDay = days[date] ?: CalendarDay(date)
}
