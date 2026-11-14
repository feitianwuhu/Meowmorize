package com.meowmorize.core.domain.repository

import com.meowmorize.core.model.CalendarDay
import com.meowmorize.core.model.MonthCalendar
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/** 月历数据源。未来日期使用记忆曲线推算，历史日期使用真实计划与日志。 */
interface CalendarRepository {
    fun observeMonth(month: YearMonth): Flow<MonthCalendar>

    /** 任意区间的按日数据，用于「近 7 天负载」等跨月统计。 */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<CalendarDay>>
}
