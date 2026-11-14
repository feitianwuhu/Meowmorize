package com.meowmorize.core.common.time

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 时间抽象。注入它而不是直接调用 [Instant.now]，
 * 排程算法与 ViewModel 才能在单元测试里「拨动时钟」验证跨天、逾期等行为。
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId

    /**
     * 时钟变化通知。
     * 平时是空流；调试用的「时间快进」会发出信号，让各页面重新按新的「今天」计算。
     */
    val clockChanges: Flow<Unit> get() = emptyFlow()
    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())

    /** 当天结束的时刻（23:59:59.999），用于判断「今天到期」。 */
    fun endOfToday(): Instant = today().atTime(LocalTime.MAX).atZone(zone()).toInstant()

    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(zone()).toInstant()

    /** 某一天结束时刻（下一天 0 点前 1 纳秒），用于「当天」「今日完成」等区间判断。 */
    fun endOfDay(date: LocalDate): Instant = date.plusDays(1).atStartOfDay(zone()).toInstant().minusNanos(1)

    fun isWeekend(date: LocalDate = today()): Boolean {
        return when (date.dayOfWeek.value) {
            6, 7 -> true
            else -> false
        }
    }
}

/** 生产实现：系统时钟。 */
class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
