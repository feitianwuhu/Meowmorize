package com.meowmorize.core.domain.plan

import com.meowmorize.core.model.TodayState

/**
 * 弹性系数计算。
 *
 * 抽成纯函数的理由：这套规则是「用户能感知到算法在照顾他」的地方，
 * 必须能被单测逐条钉住，也要能解释每一步。生成器只负责按系数挑任务。
 *
 * 组合顺序（乘法的顺序不影响结果，但影响解释的书写顺序）：
 * ```
 * factor = 负荷偏好 × 今天状态 × 完成率/趋势 × 周末
 * factor = clamp(factor, 0.60, 1.25)
 * if (到期量 > 两天容量) factor = min(factor, 0.90)   // 积压时最后压一手
 * ```
 */
object ElasticityPolicy {

    const val MIN_FACTOR = 0.60
    const val MAX_FACTOR = 1.25
    private const val BACKLOG_CAP = 0.90

    data class Input(
        val loadPreference: LoadPreference = LoadPreference.AUTO,
        val todayState: TodayState = TodayState.AUTO,
        val adaptiveEnabled: Boolean = true,
        /** 是否按分钟口径（开启后优先用 [trend]，旧口径不再参与）。 */
        val minuteCompletionEnabled: Boolean = false,
        /** 近 N 天的条数完成率（旧口径）。 */
        val completionRatios: List<Float> = emptyList(),
        /** 分钟口径的趋势分析（新口径）。 */
        val trend: CompletionTrend.Result? = null,
        val isWeekend: Boolean = false,
        val weekendReliefEnabled: Boolean = true,
        val dueMinutes: Int = 0,
        val baseCapacityMinutes: Int = 30,
        val dueCandidateCount: Int = 0,
    )

    data class Result(
        val factor: Double,
        /** 给人看的逐步解释。 */
        val notes: List<String>,
    )

    fun compute(input: Input): Result {
        val notes = mutableListOf<String>()
        var factor = 1.0

        when (input.loadPreference) {
            LoadPreference.RELAXED -> {
                factor *= LoadPreference.RELAXED.capacityFactor
                notes += "已按「轻松」模式降低今日目标。"
            }
            LoadPreference.INTENSE -> {
                factor *= LoadPreference.INTENSE.capacityFactor
                notes += "已按「冲刺」模式提高今日目标，注意别硬撑。"
            }
            LoadPreference.STEADY -> notes += "已按标准容量安排今日任务。"
            LoadPreference.AUTO -> Unit
        }

        if (input.todayState != TodayState.AUTO) {
            factor *= input.todayState.capacityFactor
            notes += when (input.todayState) {
                TodayState.TIRED -> "你说今天有点累，容量按 80% 安排。"
                TodayState.ENERGETIC -> "你说今天状态不错，容量上浮 12%。"
                else -> "今天按正常状态安排。"
            }
        }

        // 负荷偏好一旦显式指定，就尊重用户，不再叠加完成率判断
        if (input.adaptiveEnabled && input.loadPreference == LoadPreference.AUTO) {
            if (input.minuteCompletionEnabled) {
                val trend = input.trend
                if (trend != null) {
                    factor *= CompletionTrend.factor(trend)
                    CompletionTrend.reason(trend)?.let { notes += it }
                    if (!trend.trusted && trend.sampleDays > 0) {
                        notes += "完成率样本还不足（${trend.sampleDays} 天），暂不据此调整。"
                    }
                }
            } else {
                val ratios = input.completionRatios.filter { it >= 0f }
                if (ratios.size >= 2) {
                    val avg = ratios.average()
                    when {
                        avg < 0.6 -> {
                            factor *= 0.7
                            notes += "最近 ${ratios.size} 天完成率 ${(avg * 100).toInt()}%，今天自动减负 30%，先把节奏找回来。"
                        }
                        avg < 0.8 -> {
                            factor *= 0.85
                            notes += "最近完成率 ${(avg * 100).toInt()}%，今天容量下调 15%。"
                        }
                        avg > 0.95 && input.dueCandidateCount > 3 -> {
                            factor *= 1.1
                            notes += "最近状态不错，今天可适度加量 10%。"
                        }
                    }
                }
            }
        }

        if (input.isWeekend && input.weekendReliefEnabled) {
            factor *= 0.85
            notes += "周末放宽：容量按 85% 计算。"
        }

        factor = factor.coerceIn(MIN_FACTOR, MAX_FACTOR)

        if (input.dueMinutes > input.baseCapacityMinutes * 2) {
            factor = factor.coerceAtMost(BACKLOG_CAP)
            notes += "积压时长已超过两天的容量，今天优先处理最关键的，其余继续顺延。"
        }

        return Result(factor = factor, notes = notes)
    }
}
