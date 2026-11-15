package com.meowmorize.core.data.repository

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.common.time.toLocalDate
import com.meowmorize.core.data.mapper.reason
import com.meowmorize.core.data.mapper.toModel
import com.meowmorize.core.database.dao.DailyPlanDao
import com.meowmorize.core.database.dao.ItemDao
import com.meowmorize.core.database.dao.ReviewLogDao
import com.meowmorize.core.database.dao.ReviewStateDao
import com.meowmorize.core.database.dao.SubjectDao
import com.meowmorize.core.database.entity.DailyPlanItemEntity
import com.meowmorize.core.database.entity.ItemEntity
import com.meowmorize.core.database.entity.ReviewLogEntity
import com.meowmorize.core.database.entity.ReviewStateEntity
import com.meowmorize.core.database.entity.SubjectEntity
import com.meowmorize.core.domain.repository.CalendarRepository
import com.meowmorize.core.model.CalendarDay
import com.meowmorize.core.model.CalendarTask
import com.meowmorize.core.model.MonthCalendar
import com.meowmorize.core.model.RecallGrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 月历实现。
 *
 * 两种数据混用，并在 UI 上区分：
 * - 已生成计划的日期 → 展示真实任务与完成状态；
 * - 尚未生成计划的未来日期 → 用记忆曲线的 dueAt 推算「预计会到期多少条」。
 *
 * 这样用户既能回看历史，也能预判「下周三会比较忙」，而不是只有今天一屏。
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val planDao: DailyPlanDao,
    private val itemDao: ItemDao,
    private val subjectDao: SubjectDao,
    private val stateDao: ReviewStateDao,
    private val logDao: ReviewLogDao,
    private val timeProvider: TimeProvider,
) : CalendarRepository {

    override fun observeMonth(month: YearMonth): Flow<MonthCalendar> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        return observeDays(start, end).map { days ->
            MonthCalendar(month = month, days = days.associateBy { it.date })
        }
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<CalendarDay>> {
        return combine(
            planDao.observePlanItemsBetween(from.toString(), to.toString()),
            itemDao.observeActive(),
            subjectDao.observeAll(),
            stateDao.observeAll(),
            logDao.observeBetween(
                timeProvider.startOfDay(from).toEpochMilli(),
                timeProvider.endOfDay(to).toEpochMilli(),
            ),
        ) { planItems, items, subjects, states, logs ->
            buildDays(from, to, planItems, items, subjects, states, logs)
        }
    }

    private fun buildDays(
        start: LocalDate,
        end: LocalDate,
        planItems: List<DailyPlanItemEntity>,
        items: List<ItemEntity>,
        subjects: List<SubjectEntity>,
        states: List<ReviewStateEntity>,
        logs: List<ReviewLogEntity>,
    ): List<CalendarDay> {
        val itemMap = items.associateBy { it.id }
        val subjectMap = subjects.associateBy { it.id }
        val stateMap = states.associateBy { it.itemId }

        val logsByDayAndItem: Map<Pair<LocalDate, Long>, ReviewLogEntity> = logs
            .groupBy {
                Instant.ofEpochMilli(it.reviewedAt).toLocalDate(timeProvider.zone()) to it.itemId
            }
            .mapValues { (_, entries) -> entries.maxBy { it.reviewedAt } }

        val planByDate = planItems.groupBy { it.date }

        val stateTasksByDate: Map<LocalDate, List<ReviewStateEntity>> = states
            .filter { it.phase != com.meowmorize.core.model.LearningPhase.NEW.name }
            .groupBy { Instant.ofEpochMilli(it.dueAt).toLocalDate(timeProvider.zone()) }

        val days = mutableMapOf<LocalDate, CalendarDay>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            val planned = planByDate[cursor.toString()].orEmpty()
            val tasks = planned.mapNotNull { planItem ->
                val item = itemMap[planItem.itemId] ?: return@mapNotNull null
                val latest = logsByDayAndItem[cursor to planItem.itemId]
                CalendarTask(
                    itemId = item.id,
                    topic = item.topic,
                    subjectName = item.subjectId?.let(subjectMap::get)?.name,
                    subjectColorArgb = item.subjectId?.let(subjectMap::get)?.colorArgb,
                    minutes = planItem.minutes,
                    completed = latest != null && latest.grade != RecallGrade.AGAIN.name,
                    reason = planItem.reason(),
                    dueAt = stateMap[item.id]?.dueAt?.let(Instant::ofEpochMilli),
                )
            }

            val plannedIds = tasks.map { it.itemId }.toSet()
            val forecastTasks = if (planned.isEmpty()) {
                stateTasksByDate[cursor].orEmpty()
                    .filter { it.itemId !in plannedIds }
                    .mapNotNull { state ->
                        val item = itemMap[state.itemId] ?: return@mapNotNull null
                        CalendarTask(
                            itemId = item.id,
                            topic = item.topic,
                            subjectName = item.subjectId?.let(subjectMap::get)?.name,
                            subjectColorArgb = item.subjectId?.let(subjectMap::get)?.colorArgb,
                            minutes = item.estimatedMinutes,
                            completed = false,
                            reason = null,
                            dueAt = Instant.ofEpochMilli(state.dueAt),
                            forecast = true,
                        )
                    }
            } else {
                emptyList()
            }

            if (tasks.isNotEmpty() || forecastTasks.isNotEmpty()) {
                days[cursor] = CalendarDay(
                    date = cursor,
                    tasks = tasks,
                    forecastCount = forecastTasks.size,
                    plannedCount = tasks.size,
                    completedCount = tasks.count { it.completed },
                    minutes = tasks.sumOf { it.minutes },
                    forecastMinutes = forecastTasks.sumOf { it.minutes },
                    subjectColors = (tasks + forecastTasks).mapNotNull { it.subjectColorArgb }.distinct(),
                )
            }
            cursor = cursor.plusDays(1)
        }

        return (0..java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()).map { offset ->
            val date = start.plusDays(offset.toLong())
            days[date] ?: CalendarDay(date)
        }
    }
}
