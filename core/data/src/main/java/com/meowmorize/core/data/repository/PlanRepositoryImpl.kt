package com.meowmorize.core.data.repository

import androidx.room.withTransaction
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.data.mapper.reason
import com.meowmorize.core.data.mapper.toEntity
import com.meowmorize.core.data.mapper.toModel
import com.meowmorize.core.data.mapper.toPlan
import com.meowmorize.core.database.MeowmorizeDatabase
import com.meowmorize.core.database.dao.DailyPlanDao
import com.meowmorize.core.database.dao.ItemDao
import com.meowmorize.core.database.dao.ReviewLogDao
import com.meowmorize.core.database.dao.ReviewStateDao
import com.meowmorize.core.database.dao.SubjectDao
import com.meowmorize.core.database.entity.DailyPlanEntity
import com.meowmorize.core.database.entity.DailyPlanItemEntity
import com.meowmorize.core.database.entity.ReviewLogEntity
import com.meowmorize.core.database.entity.ReviewStateEntity
import com.meowmorize.core.database.entity.SubjectEntity
import com.meowmorize.core.database.entity.ItemEntity
import com.meowmorize.core.domain.plan.DueCandidate
import com.meowmorize.core.domain.plan.NewCandidate
import com.meowmorize.core.domain.plan.PlanDraft
import com.meowmorize.core.domain.repository.PlanInputs
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.srs.FsrsScheduler
import com.meowmorize.core.domain.srs.SrsConfig
import com.meowmorize.core.model.DailyPlan
import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.PlanReason
import com.meowmorize.core.model.PlannedTask
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewLog
import com.meowmorize.core.model.ReviewOutcome
import com.meowmorize.core.model.ReviewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 计划与复习进度的数据实现。
 *
 * 「今日完成度」不落库，而是由当天的复习日志推导：
 * 计划表只记录「今天打算做什么」，日志记录「实际做了什么」，
 * 两者分离后，编辑任务、重新生成计划都不会弄丢已完成状态。
 */
@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val database: MeowmorizeDatabase,
    private val planDao: DailyPlanDao,
    private val itemDao: ItemDao,
    private val subjectDao: SubjectDao,
    private val stateDao: ReviewStateDao,
    private val logDao: ReviewLogDao,
    private val scheduler: FsrsScheduler,
    private val timeProvider: TimeProvider,
) : PlanRepository {

    override fun observePlan(date: LocalDate): Flow<DailyPlan?> {
        val dateKey = date.toString()
        val startMillis = timeProvider.startOfDay(date).toEpochMilli()
        val endMillis = timeProvider.endOfDay(date).toEpochMilli()

        val headAndItems = combine(
            planDao.observePlan(dateKey),
            planDao.observePlanItems(dateKey),
        ) { head, items -> head to items }

        return combine(
            headAndItems,
            itemDao.observeAll(),
            subjectDao.observeAll(),
            stateDao.observeAll(),
            logDao.observeBetween(startMillis, endMillis),
        ) { (head, planItems), items, subjects, states, logs ->
            head?.toPlan(
                tasks = buildTasks(planItems, items, subjects, states, logs),
                date = date,
            )
        }
    }

    override suspend fun upcomingTasks(until: Instant): List<PlannedTask> {
        val items = itemDao.getActive().associateBy { it.id }
        val subjects = subjectDao.getAll().associateBy { it.id }
        val today = timeProvider.today()
        val startOfToday = timeProvider.startOfDay(today)
        return stateDao.getDueBefore(until.toEpochMilli()).mapNotNull { stateEntity ->
            val item = items[stateEntity.itemId] ?: return@mapNotNull null
            if (stateEntity.phase == LearningPhase.NEW.name) return@mapNotNull null
            val state = stateEntity.toModel()
            PlannedTask(
                item = item.toModel(),
                state = state,
                subject = item.subjectId?.let(subjects::get)?.toModel(),
                reason = if (state.dueAt.isBefore(startOfToday)) PlanReason.OVERDUE else PlanReason.DUE_TODAY,
                orderIndex = 0,
                minutes = item.estimatedMinutes,
            )
        }
    }

    override suspend fun todayReviewCount(itemId: Long, now: Instant): Int {
        val start = timeProvider.startOfDay(timeProvider.today()).toEpochMilli()
        return logDao.countBetween(itemId, start, now.toEpochMilli())
    }

    override fun observeReviewState(itemId: Long): Flow<ReviewState?> =
        stateDao.observeByItem(itemId).map { it?.toModel() }

    override fun observeReviewStates(): Flow<Map<Long, ReviewState>> =
        stateDao.observeAll().map { list -> list.associate { it.itemId to it.toModel() } }

    override suspend fun getPlan(date: LocalDate): DailyPlan? {
        val head = planDao.getPlan(date.toString()) ?: return null
        val planItems = planDao.getPlanItems(date.toString())
        val startMillis = timeProvider.startOfDay(date).toEpochMilli()
        val endMillis = timeProvider.endOfDay(date).toEpochMilli()
        return head.toPlan(
            tasks = buildTasks(
                planItems = planItems,
                items = itemDao.getActive(),
                subjects = subjectDao.getAll(),
                states = stateDao.getAll(),
                logs = logDao.getBetween(startMillis, endMillis),
            ),
            date = date,
        )
    }

    override suspend fun loadPlanInputs(now: Instant, endOfToday: Instant): PlanInputs {
        val items = itemDao.getActive().map(ItemEntity::toModel)
        val states = stateDao.getAll().associate { it.itemId to it.toModel() }
        val today = LocalDate.ofInstant(now, timeProvider.zone())

        val due = mutableListOf<DueCandidate>()
        val newItems = mutableListOf<NewCandidate>()

        items.forEach { item ->
            val state = states[item.id]
            if (state == null || state.isNew) {
                newItems += NewCandidate(item)
            } else if (!state.dueAt.isAfter(endOfToday)) {
                val dueDate = LocalDate.ofInstant(state.dueAt, timeProvider.zone())
                val overdueDays = if (state.dueAt.isBefore(timeProvider.startOfDay(today))) {
                    ChronoUnit.DAYS.between(dueDate, today).coerceAtLeast(1)
                } else {
                    0
                }
                due += DueCandidate(
                    item = item,
                    state = state,
                    overdueDays = overdueDays,
                    retrievability = scheduler.retrievability(state, now),
                )
            }
        }

        return PlanInputs(
            due = due.sortedByDescending { it.overdueDays },
            newItems = newItems,
        )
    }

    override suspend fun savePlan(
        date: LocalDate,
        draft: PlanDraft,
        now: Instant,
        userAdjusted: Boolean,
    ): DailyPlan {
        // 历史计划只读：否则「完成率」的分母会被今天的重排悄悄改写，趋势统计失去意义
        require(date >= timeProvider.today()) { "不允许改写历史计划：$date" }
        val dateKey = date.toString()
        database.withTransaction {
            planDao.deletePlan(dateKey) // 级联删除旧的任务行
            planDao.upsertPlan(
                DailyPlanEntity(
                    date = dateKey,
                    generatedAt = now.toEpochMilli(),
                    capacityMinutes = draft.capacityMinutes,
                    plannedMinutes = draft.plannedMinutes,
                    loadLevel = draft.loadLevel.name,
                    targetRetention = draft.targetRetention,
                    elasticityFactor = draft.elasticityFactor,
                    backlogCount = draft.backlogCount,
                    newCount = draft.newCount,
                    notes = draft.notes.joinToString("\n"),
                    userAdjusted = userAdjusted,
                ),
            )
            planDao.insertPlanItems(
                draft.tasks.mapIndexed { index, task ->
                    DailyPlanItemEntity(
                        date = dateKey,
                        itemId = task.itemId,
                        reason = task.reason.name,
                        orderIndex = index,
                        minutes = task.minutes,
                    )
                },
            )
        }
        return checkNotNull(getPlan(date)) { "计划保存失败" }
    }

    override suspend fun deletePlan(date: LocalDate) {
        planDao.deletePlan(date.toString())
    }

    override suspend fun recordReview(
        itemId: Long,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        config: SrsConfig,
    ): ReviewOutcome {
        return database.withTransaction {
            val current = stateDao.getByItem(itemId)?.toModel()
            val item = itemDao.getById(itemId)
            val todayStart = timeProvider.startOfDay(timeProvider.today()).toEpochMilli()
            // 语义是「今天已经评过几次」，0 表示今天第一次
            val todayReviewCount = logDao.countBetween(itemId, todayStart, now.toEpochMilli())
            val outcome = scheduler.review(
                state = current,
                grade = grade,
                now = now,
                targetRetention = targetRetention,
                itemId = itemId,
                todayReviewCount = todayReviewCount,
                config = config,
            )
            stateDao.upsert(outcome.state.toEntity())
            logDao.insert(
                ReviewLog(
                    itemId = itemId,
                    reviewedAt = now,
                    grade = grade,
                    // 真实间隔（天），不是「上次排程天数」；同日重练记 0
                    intervalBefore = current?.lastReviewedAt?.let {
                        Duration.between(it, now).toMinutes().coerceAtLeast(0) / 1440
                    }?.toInt() ?: 0,
                    intervalAfter = outcome.nextIntervalDays,
                    stabilityAfter = outcome.state.stability,
                    phaseAfter = outcome.state.phase,
                    itemRevision = item?.revision ?: 1,
                ).toEntity(),
            )
            outcome
        }
    }

    override suspend fun recentCompletionRatios(date: LocalDate, days: Int): List<Float> {
        val dates = (1..days).map { date.minusDays(it.toLong()) }
        if (dates.isEmpty()) return emptyList()
        val from = dates.min()
        val to = dates.max()
        val planItems = planDao.getPlanItemsBetween(from.toString(), to.toString())
        val logs = logDao.getBetween(
            timeProvider.startOfDay(from).toEpochMilli(),
            timeProvider.endOfDay(to).toEpochMilli(),
        )

        val plannedPerDay = planItems.groupingBy { it.date }.eachCount()
        val completedPerDay = logs
            .filter { it.grade != RecallGrade.AGAIN.name }
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.reviewedAt), timeProvider.zone()).toString() }
            .mapValues { entry -> entry.value.map { it.itemId }.distinct().size }

        return dates.mapNotNull { day ->
            val key = day.toString()
            val planned = plannedPerDay[key] ?: return@mapNotNull null
            if (planned == 0) return@mapNotNull null
            ((completedPerDay[key] ?: 0).toFloat() / planned).coerceIn(0f, 1f)
        }
    }

    private fun buildTasks(
        planItems: List<DailyPlanItemEntity>,
        items: List<ItemEntity>,
        subjects: List<SubjectEntity>,
        states: List<ReviewStateEntity>,
        logs: List<ReviewLogEntity>,
    ): List<PlannedTask> {
        val itemMap = items.associateBy { it.id }
        val subjectMap = subjects.associateBy { it.id }
        val stateMap = states.associateBy { it.itemId }
        val latestLogs = logs.groupBy { it.itemId }.mapValues { (_, entries) ->
            entries.maxByOrNull { it.reviewedAt }
        }

        return planItems.sortedBy { it.orderIndex }.mapNotNull { planItem ->
            val item = itemMap[planItem.itemId] ?: return@mapNotNull null
            val latest = latestLogs[planItem.itemId]
            val isAgain = latest?.grade == RecallGrade.AGAIN.name
            PlannedTask(
                item = item.toModel(),
                state = stateMap[planItem.itemId]?.toModel() ?: newState(planItem.itemId),
                subject = item.subjectId?.let(subjectMap::get)?.toModel(),
                reason = planItem.reason(),
                orderIndex = planItem.orderIndex,
                minutes = planItem.minutes,
                completed = latest != null && !isAgain,
                needsRepractice = isAgain,
            )
        }
    }

    private fun newState(itemId: Long): ReviewState = ReviewState(
        itemId = itemId,
        dueAt = timeProvider.now().plus(Duration.ofMinutes(1)),
        stability = 0.0,
        difficulty = 5.0,
        phase = LearningPhase.NEW,
    )
}
