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
     * 只有样本足够（≥2 天）时才启用自适应，避免第一天就被误判。
     */
    private fun computeElasticityFactor(
        request: PlanRequest,
        capacityBase: Int,
        notes: MutableList<String>,
    ): Double {
        var factor = request.loadPreference.capacityFactor
        when (request.loadPreference) {
            LoadPreference.RELAXED -> notes += "已按「轻松」模式降低今日目标。"
            LoadPreference.INTENSE -> notes += "已按「冲刺」模式提高今日目标，注意别硬撑。"
            LoadPreference.STEADY -> notes += "已按标准容量安排今日任务。"
            LoadPreference.AUTO -> Unit
        }

        val ratios = request.recentCompletionRatios.filter { it >= 0f }
        if (request.settings.adaptiveLoadEnabled && request.loadPreference == LoadPreference.AUTO && ratios.size >= 2) {
            val avg = ratios.average()
            when {
                avg < 0.6 -> {
                    factor *= 0.7
                    notes += "最近 ${ratios.size} 天完成率 ${(avg * 100).roundToInt()}%，今天自动减负 30%，先把节奏找回来。"
                }
                avg < 0.8 -> {
                    factor *= 0.85
                    notes += "最近完成率 ${(avg * 100).roundToInt()}%，今天容量下调 15%。"
                }
                avg > 0.95 && request.dueCandidates.size > 3 -> {
                    factor *= 1.1
                    notes += "最近状态不错，今天可适度加量 10%。"
                }
            }
        }

        if (request.isWeekend && request.settings.weekendReliefEnabled) {
            factor *= 0.85
            notes += "周末放宽：容量按 85% 计算。"
        }

        val dueMinutes = request.dueCandidates.sumOf { it.item.estimatedMinutes }
        if (dueMinutes > capacityBase * 2) {
            factor = factor.coerceAtMost(0.9)
            notes += "积压时长已超过两天的容量，今天优先处理最关键的，其余继续顺延。"
        }

        return factor.coerceIn(0.6, 1.25)
    }
}
