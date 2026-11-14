package com.meowmorize.core.domain.repository

import com.meowmorize.core.model.DailyPlan
import com.meowmorize.core.model.PlannedTask
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewOutcome
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.domain.plan.NewCandidate
import com.meowmorize.core.domain.plan.DueCandidate
import com.meowmorize.core.domain.plan.DayCompletion
import com.meowmorize.core.domain.plan.PlanDraft
import com.meowmorize.core.domain.srs.LeechPolicy
import com.meowmorize.core.domain.srs.SrsConfig
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** 生成计划所需的原始候选集合。 */
data class PlanInputs(
    val due: List<DueCandidate>,
    val newItems: List<NewCandidate>,
)

/** 每日计划、复习进度与日志的统一入口。 */
interface PlanRepository {

    /** 观察某天的计划（含完成状态）。计划不存在时发出 `null`。 */
    fun observePlan(date: LocalDate): Flow<DailyPlan?>

    suspend fun getPlan(date: LocalDate): DailyPlan?

    /** 观察单条任务的记忆状态（复习页用来预览四个评分对应的间隔）。 */
    fun observeReviewState(itemId: Long): Flow<ReviewState?>

    /** 观察全部记忆状态，任务库列表用它展示「下次复习」时间。 */
    fun observeReviewStates(): Flow<Map<Long, ReviewState>>

    /**
     * 窗口内即将到期的任务（不区分是否已排进今日计划）。
     * 日历同步用它补齐「今天排不下、但明天到期」的内容。
     */
    suspend fun upcomingTasks(until: Instant): List<PlannedTask>

    /** 拉取候选：已到期的复习任务 + 尚未学习的新任务。 */
    suspend fun loadPlanInputs(now: Instant, endOfToday: Instant): PlanInputs

    /** 覆盖保存某天的计划，返回可直接渲染的 [DailyPlan]。 */
    suspend fun savePlan(
        date: LocalDate,
        draft: PlanDraft,
        now: Instant,
        userAdjusted: Boolean,
    ): DailyPlan

    suspend fun deletePlan(date: LocalDate)

    /** 记录一次评分，返回新的排程结果。 */
    suspend fun recordReview(
        itemId: Long,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        config: SrsConfig = SrsConfig(),
        /** 本次是否用过提示：用过则评分偏乐观，必须在日志里标注。 */
        hintUsed: Boolean = false,
        leechThreshold: Int = LeechPolicy.DEFAULT_THRESHOLD,
    ): ReviewOutcome

    /**
     * 今天已经评过几次。复习页用它预览间隔——
     * 没有这个数字，第 3 次「忘了」的预览会显示 10 分钟，实际却排到次日。
     */
    suspend fun todayReviewCount(itemId: Long, now: Instant): Int

    /** 最近 N 天的完成率（不含 [date] 当天），用于自适应容量。 */
    suspend fun recentCompletionRatios(date: LocalDate, days: Int = 7): List<Float>

    /**
     * 最近 N 天的「计划分钟 vs 完成分钟」（不含 [date] 当天）。
     * 分母用当天首次生成时的快照，重排不会改写历史。
     */
    suspend fun recentDailyCompletion(date: LocalDate, days: Int = 7): List<DayCompletion>
}
