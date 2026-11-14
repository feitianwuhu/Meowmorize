package com.meowmorize.core.domain.plan

import com.meowmorize.core.model.LoadLevel
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.PlanReason
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.model.UserSettings
import java.time.LocalDate
import kotlin.math.roundToInt

/** 用户对今日负荷的显式偏好（AUTO 表示完全交给算法）。 */
enum class LoadPreference(val capacityFactor: Double) {
    AUTO(1.0),
    RELAXED(0.7),
    STEADY(1.0),
    INTENSE(1.25),
}

/** 到期/逾期候选任务。 */
data class DueCandidate(
    val item: MemorizeItem,
    val state: ReviewState,
    val overdueDays: Long,
    val retrievability: Double,
)

/** 新学候选任务（尚未建立记忆状态）。 */
data class NewCandidate(val item: MemorizeItem)

data class PlanRequest(
    val date: LocalDate,
    val dueCandidates: List<DueCandidate>,
    val newCandidates: List<NewCandidate>,
    val settings: UserSettings,
    /** 最近若干天的「计划完成率」，用于自适应减负；不含今天。 */
    val recentCompletionRatios: List<Float> = emptyList(),
    val isWeekend: Boolean = false,
    /** 用户手动拖动容量后的分钟数，优先级高于设置里的默认容量。 */
    val forcedCapacityMinutes: Int? = null,
    val loadPreference: LoadPreference = LoadPreference.AUTO,
    /** 今天的状态（累/正常/状态好），只影响当天。 */
    val todayState: com.meowmorize.core.model.TodayState = com.meowmorize.core.model.TodayState.AUTO,
    /** 分钟口径的完成率趋势；null 表示未开启该实验。 */
    val completionTrend: CompletionTrend.Result? = null,
    /** 学科权重，供配额分配使用；缺省按 1.0。 */
    val subjectWeights: Map<Long, Double> = emptyMap(),
)

data class PlanTaskDraft(
    val itemId: Long,
    val reason: PlanReason,
    val minutes: Int,
)

/**
 * 计划生成结果。[notes] 是给人看的解释，UI 会原样展示。
 */
data class PlanDraft(
    val tasks: List<PlanTaskDraft>,
    val capacityMinutes: Int,
    val plannedMinutes: Int,
    val loadLevel: LoadLevel,
    val elasticityFactor: Double,
    val backlogCount: Int,
    val newCount: Int,
    val targetRetention: Double,
    val notes: List<String>,
)

/**
 * 今日计划生成器——「以人为本」的落点。
 *
 * 常规间隔重复算法只回答「今天该复习什么」，不回答「今天该复习多少」。
 * 当积压变多时，机械地全量推送会直接导致放弃。这里的弹性规则做三件事：
 *
 * 1. **硬上限**：今日总时长不超过容量，宁可顺延也不超发（过劳保护）。
 * 2. **自适应容量**：近期完成率偏低时自动下调容量，完成率高且状态好时允许小幅加量。
 * 3. **复习优先、新学补给**：有积压时最多 80% 容量给复习，新学只填剩余空间，
 *    避免「新任务挤掉旧任务」造成二次欠账。
 *
 * 所有调整都写入 [PlanDraft.notes]，用户能看懂为什么今天变少了。
 */
class TodayPlanGenerator {

    fun generate(request: PlanRequest): PlanDraft {
        val notes = mutableListOf<String>()
        val baseCapacity = request.settings.dailyCapacityMinutes
            .coerceIn(UserSettings.MIN_CAPACITY_MINUTES, UserSettings.MAX_CAPACITY_MINUTES)

        // 用户手动设定容量时，以用户的数字为准；但仍要算出「算法推荐值」并解释，
        // 否则手动一调，算法的判断就彻底失声了。
        val forcedCapacity = request.forcedCapacityMinutes
            ?.coerceIn(UserSettings.MIN_CAPACITY_MINUTES, UserSettings.MAX_CAPACITY_MINUTES)

        val elasticityFactor: Double
        val effectiveCapacity: Int
        if (forcedCapacity != null) {
            val recommendedFactor = computeElasticityFactor(request, baseCapacity, mutableListOf())
            val recommendedCapacity = (baseCapacity * recommendedFactor).roundToInt()
                .coerceIn(UserSettings.MIN_CAPACITY_MINUTES, UserSettings.MAX_CAPACITY_MINUTES)
            elasticityFactor = forcedCapacity.toDouble() / baseCapacity.coerceAtLeast(1)
            effectiveCapacity = forcedCapacity
            notes += "已按你设定的 $forcedCapacity 分钟安排；算法推荐 $recommendedCapacity 分钟。"
        } else {
            elasticityFactor = computeElasticityFactor(request, baseCapacity, notes)
            effectiveCapacity = (baseCapacity * elasticityFactor).roundToInt()
                .coerceIn(UserSettings.MIN_CAPACITY_MINUTES, UserSettings.MAX_CAPACITY_MINUTES)
        }

        val dueMinutes = request.dueCandidates.sumOf { it.item.estimatedMinutes }
        val newDemandMinutes = request.newCandidates.sumOf { it.item.estimatedMinutes }
        val hasBacklog = dueMinutes + newDemandMinutes > effectiveCapacity

        // 复习配额：有积压时把 20% 容量留给新学。注意：逾期与今日到期共用这一配额，
        // 旧实现让逾期任务用满容量，恰恰在最需要缓冲时把新学饿死。
        val reviewQuota = if (request.newCandidates.isNotEmpty() && hasBacklog) {
            (effectiveCapacity * 0.8).roundToInt()
        } else {
            effectiveCapacity
        }

        val sortedDue = request.dueCandidates.sortedWith(
            compareBy<DueCandidate> { if (it.overdueDays > 0) 0 else 1 }
                .thenByDescending { it.overdueDays }
                .thenByDescending { it.item.priority.weight }
                .thenBy { it.retrievability }
                .thenBy { it.state.dueAt },
        )

        val selected = mutableListOf<PlanTaskDraft>()
        var usedMinutes = 0

        val quota = if (request.settings.subjectQuotaEnabled && hasBacklog) {
            SubjectQuotaAllocator.allocate(
                candidates = sortedDue.map {
                    SubjectQuotaAllocator.Candidate(
                        itemId = it.item.id,
                        subjectId = it.item.subjectId,
                        minutes = it.item.estimatedMinutes,
                    )
                },
                capacityMinutes = reviewQuota,
                weights = request.subjectWeights,
                minShare = request.settings.subjectMinShare,
                maxShare = request.settings.subjectMaxShare,
            )
        } else {
            null
        }

        if (quota != null && quota.applied) {
            notes += quota.notes
            val picked = mutableSetOf<Long>()
            // 先按学科在各自配额内挑，学科内仍按逾期→优先级→R 排序
            sortedDue.groupBy { it.item.subjectId }.forEach { (subjectId, list) ->
                var budget = quota.budgets[subjectId] ?: 0
                for (candidate in list) {
                    val minutes = candidate.item.estimatedMinutes
                    if (minutes <= budget && usedMinutes + minutes <= reviewQuota) {
                        selected += PlanTaskDraft(
                            itemId = candidate.item.id,
                            reason = if (candidate.overdueDays > 0) PlanReason.OVERDUE else PlanReason.DUE_TODAY,
                            minutes = minutes,
                        )
                        usedMinutes += minutes
                        budget -= minutes
                        picked += candidate.item.id
                    }
                }
            }
            // 配额算完后若还有余量，按原优先级补满，避免浪费容量
            for (candidate in sortedDue) {
                if (candidate.item.id in picked) continue
                val minutes = candidate.item.estimatedMinutes
                if (usedMinutes + minutes <= reviewQuota) {
                    selected += PlanTaskDraft(
                        itemId = candidate.item.id,
                        reason = if (candidate.overdueDays > 0) PlanReason.OVERDUE else PlanReason.DUE_TODAY,
                        minutes = minutes,
                    )
                    usedMinutes += minutes
                }
            }
        } else {
            for (candidate in sortedDue) {
                val minutes = candidate.item.estimatedMinutes
                if (usedMinutes + minutes <= reviewQuota) {
                    selected += PlanTaskDraft(
                        itemId = candidate.item.id,
                        reason = if (candidate.overdueDays > 0) PlanReason.OVERDUE else PlanReason.DUE_TODAY,
                        minutes = minutes,
                    )
                    usedMinutes += minutes
                }
            }
        }

        // 容量连一条都装不下：选「分钟数最小」的逾期任务，而不是排序第一条，
        // 让用户至少能完成一件事，而不是打开就看到一个 40 分钟的大山。
        if (selected.isEmpty() && sortedDue.isNotEmpty()) {
            val smallest = sortedDue.minBy { it.item.estimatedMinutes }
            selected += PlanTaskDraft(
                itemId = smallest.item.id,
                reason = if (smallest.overdueDays > 0) PlanReason.OVERDUE else PlanReason.DUE_TODAY,
                minutes = smallest.item.estimatedMinutes,
            )
            usedMinutes += smallest.item.estimatedMinutes
            notes += "今天可用的时间很短，只保留了最短的一条，其余已顺延。"
        }

        // 新学：两遍法。第一遍严格按优先级顺序，装不下就记入 deferred，
        // 不再让后面的短任务插队；第二遍才用剩余容量碎片填补，并说明原因。
        var newCount = 0
        val sortedNew = request.newCandidates.sortedWith(
            compareByDescending<NewCandidate> { it.item.priority.weight }
                .thenBy { it.item.createdAt },
        )
        val deferredNew = mutableListOf<NewCandidate>()
        var blocked = false
        for (candidate in sortedNew) {
            if (newCount >= request.settings.maxNewPerDay) break
            val minutes = candidate.item.estimatedMinutes
            if (!blocked && usedMinutes + minutes <= effectiveCapacity) {
                selected += PlanTaskDraft(candidate.item.id, PlanReason.NEW, minutes)
                usedMinutes += minutes
                newCount++
            } else {
                blocked = true
                deferredNew += candidate
            }
        }
        if (deferredNew.isNotEmpty() && newCount < request.settings.maxNewPerDay) {
            var filled = 0
            for (candidate in deferredNew.sortedBy { it.item.estimatedMinutes }) {
                if (newCount >= request.settings.maxNewPerDay) break
                val minutes = candidate.item.estimatedMinutes
                if (usedMinutes + minutes <= effectiveCapacity) {
                    selected += PlanTaskDraft(candidate.item.id, PlanReason.NEW, minutes)
                    usedMinutes += minutes
                    newCount++
                    filled++
                }
            }
            if (filled > 0) {
                notes += "因容量碎片，补入了 $filled 条较短的新内容；较长的已顺延。"
            }
        }

        val selectedIds = selected.map { it.itemId }.toSet()
        val backlogCandidates = request.dueCandidates.filter { it.item.id !in selectedIds }
        val backlog = backlogCandidates.size
        val backlogMinutes = backlogCandidates.sumOf { it.item.estimatedMinutes }

        if (backlog > 0) {
            notes += "另有 $backlog 条（约 $backlogMinutes 分钟）不急在今天，已顺延到明天；" +
                "记忆曲线会按真实遗忘进度重新计算，不必补课。"
        }
        if (newCount > 0 && hasBacklog) {
            notes += "今天以复习为主，新学只安排了 $newCount 条。"
        }
        if (selected.isEmpty()) {
            notes += "今天没有到期的内容，安心休息。"
        }
        if (request.settings.targetRetention >= 0.95) {
            notes += "目标保持率设为 ${(request.settings.targetRetention * 100).roundToInt()}%，复习会更频繁，可以适当调低。"
        }

        val plannedMinutes = usedMinutes
        return PlanDraft(
            tasks = selected,
            capacityMinutes = effectiveCapacity,
            plannedMinutes = plannedMinutes,
            loadLevel = LoadLevel.of(plannedMinutes, effectiveCapacity),
            elasticityFactor = elasticityFactor,
            backlogCount = backlog,
            newCount = newCount,
            targetRetention = request.settings.targetRetention,
            notes = notes.toList(),
        )
    }

    /**
     * 弹性系数：把「用户近况」折算成一个容量乘数。
     *
     * 具体规则在 [ElasticityPolicy] 里，这里只做输入装配——
     * 规则要能被单测逐条钉住，生成器不该埋着第二套口径。
     */
    private fun computeElasticityFactor(
        request: PlanRequest,
        capacityBase: Int,
        notes: MutableList<String>,
    ): Double {
        val dueMinutes = request.dueCandidates.sumOf { it.item.estimatedMinutes }
        val result = ElasticityPolicy.compute(
            ElasticityPolicy.Input(
                loadPreference = request.loadPreference,
                todayState = request.todayState,
                adaptiveEnabled = request.settings.adaptiveLoadEnabled,
                minuteCompletionEnabled = request.settings.minuteCompletionEnabled,
                completionRatios = request.recentCompletionRatios,
                trend = request.completionTrend,
                isWeekend = request.isWeekend,
                weekendReliefEnabled = request.settings.weekendReliefEnabled,
                dueMinutes = dueMinutes,
                baseCapacityMinutes = capacityBase,
                dueCandidateCount = request.dueCandidates.size,
            ),
        )
        notes += result.notes
        return result.factor
    }
}
