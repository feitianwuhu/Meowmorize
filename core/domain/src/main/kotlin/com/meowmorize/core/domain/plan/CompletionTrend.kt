package com.meowmorize.core.domain.plan

import kotlin.math.abs

/** 某一天的「计划量 vs 实际完成量」，单位统一为分钟。 */
data class DayCompletion(
    val plannedMinutes: Int,
    val completedMinutes: Int,
) {
    val ratio: Float
        get() = if (plannedMinutes <= 0) 0f
        else (completedMinutes.toFloat() / plannedMinutes).coerceIn(0f, 1f)
}

/**
 * 完成率的「趋势」而不只是「均值」。
 *
 * 为什么需要：只看均值分不清两种情况——
 * 一个人连续 5 天 50% 完成，和「前 4 天 80%、昨天突然 10%」，
 * 均值可能接近，但后者应当立刻减负，前者更像稳定的容量上限。
 *
 * 这里用最小二乘斜率 + 前后半段对比做两个判断：
 * - `DECLINING`：在往下掉，先减负；
 * - `RECOVERING`：最近在回升且已回到七成以上，可以小幅加量。
 */
object CompletionTrend {

    enum class State { NONE, STEADY, DECLINING, RECOVERING }

    data class Result(
        val state: State,
        /** 样本天数（只统计有计划的日期）。 */
        val sampleDays: Int,
        /** 覆盖率：有完成记录的天数 / 窗口天数，用于判断信号是否可信。 */
        val coverage: Float,
        val average: Float,
        /** 最小二乘斜率：单位是「每天提升的完成率」。 */
        val slope: Float,
    ) {
        val trusted: Boolean get() = sampleDays >= MIN_SAMPLE_DAYS && coverage >= MIN_COVERAGE
    }

    /** 少于 3 天样本不调：第一周就按完成率打折，只会让人觉得算法在惩罚新人。 */
    const val MIN_SAMPLE_DAYS = 3
    const val MIN_COVERAGE = 0.5f
    private const val DECLINE_SLOPE = -0.04f
    private const val RECOVERY_GAIN = 0.08f

    /** @param days 按时间正序（最早在前），中间缺日请直接不传。 */
    fun analyze(days: List<DayCompletion>, windowDays: Int): Result {
        val valid = days.filter { it.plannedMinutes > 0 }
        if (valid.isEmpty()) {
            return Result(State.NONE, 0, 0f, 0f, 0f)
        }
        val ratios = valid.map { it.ratio }
        val average = ratios.average().toFloat()
        val coverage = (valid.size.toFloat() / windowDays.coerceAtLeast(1)).coerceIn(0f, 1f)
        val slope = slopeOf(ratios)

        val state = when {
            valid.size < MIN_SAMPLE_DAYS || coverage < MIN_COVERAGE -> State.STEADY
            slope <= DECLINE_SLOPE -> State.DECLINING
            isRecovering(ratios) -> State.RECOVERING
            else -> State.STEADY
        }
        return Result(state, valid.size, coverage, average, slope)
    }

    /** 后 3 天比前 3 天高 0.08，且最后一天不低于 0.75，才算「恢复」。 */
    private fun isRecovering(ratios: List<Float>): Boolean {
        if (ratios.size < 6) return false
        val former = ratios.take(ratios.size - 3).takeLast(3).average()
        val latter = ratios.takeLast(3).average()
        return latter - former >= RECOVERY_GAIN && ratios.last() >= 0.75
    }

    private fun slopeOf(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val n = values.size
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var num = 0.0
        var den = 0.0
        values.forEachIndexed { index, value ->
            val dx = index - meanX
            num += dx * (value - meanY)
            den += dx * dx
        }
        return if (abs(den) < 1e-9) 0f else (num / den).toFloat()
    }

    /**
     * 趋势折算成容量系数。幅度刻意保守：单次最多 ±10%，
     * 因为「趋势」本身就是噪声较大的信号，不该主导排程。
     */
    fun factor(result: Result): Double = when {
        !result.trusted -> 1.0
        result.state == State.DECLINING -> 0.90
        result.state == State.RECOVERING -> 1.05
        else -> 1.0
    }

    fun reason(result: Result): String? = when {
        !result.trusted -> null
        result.state == State.DECLINING ->
            "最近 ${result.sampleDays} 天按分钟算的完成率在下滑（${(result.average * 100).toInt()}%），今天先降 10%。"
        result.state == State.RECOVERING ->
            "最近完成率在回升，今天可小幅加量 5%。"
        else -> null
    }
}
