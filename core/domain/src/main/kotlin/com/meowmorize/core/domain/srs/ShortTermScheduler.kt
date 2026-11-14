package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.RecallGrade

/**
 * 同日重练的步进器。
 *
 * 之前用「elapsed 至少 0.25 天」把 10 分钟当成 6 小时，导致短周期内容的 R 与 S 全被污染。
 * 这里把同日行为从遗忘曲线里拆出来，用显式的步进（10m → 60m → 240m）表达，
 * 既符合真实学习节奏，也能在 UI 上直接说清「10 分钟后再来一次」。
 *
 * 该对象只做纯计算，不依赖时间与时区；毕业后的天数由 [FsrsScheduler] 反解。
 */
object ShortTermScheduler {

    /** 超过当日上限后的冷却：不排当天，也不拖到第二天早上。 */
    const val COOLDOWN_MINUTES = 360L

    data class Result(
        val stability: Double,
        /** 非毕业时的新增延迟（分钟）；毕业时为 0，由调用方按间隔天数排程。 */
        val delayMinutes: Long,
        val graduated: Boolean,
        val repeatedOut: Boolean,
        /** 建议的下一步序号，便于 UI 或后续持久化。 */
        val nextStep: Int,
    )

    fun step(
        stability: Double,
        grade: RecallGrade,
        todayReviewCount: Int,
        config: SrsConfig,
    ): Result {
        val steps = config.learningStepsMinutes
        val currentStep = todayReviewCount.coerceIn(0, steps.lastIndex)

        return when (grade) {
            RecallGrade.AGAIN -> {
                val nextStability = (stability * 0.7).coerceAtLeast(MIN_SHORT_TERM_STABILITY)
                if (todayReviewCount >= config.sameDayRepeatLimit) {
                    Result(
                        stability = nextStability,
                        delayMinutes = COOLDOWN_MINUTES,
                        graduated = false,
                        repeatedOut = true,
                        nextStep = steps.lastIndex,
                    )
                } else {
                    Result(
                        stability = nextStability,
                        delayMinutes = steps[currentStep].toLong(),
                        graduated = false,
                        repeatedOut = false,
                        nextStep = (currentStep + 1).coerceAtMost(steps.lastIndex),
                    )
                }
            }

            RecallGrade.HARD -> Result(
                stability = (stability * 1.05).coerceAtMost(stability * MAX_SHORT_TERM_GROWTH),
                delayMinutes = steps[currentStep].toLong(),
                graduated = false,
                repeatedOut = false,
                nextStep = (currentStep + 1).coerceAtMost(steps.lastIndex),
            )

            RecallGrade.GOOD -> Result(
                stability = (stability * 1.10).coerceAtMost(stability * MAX_SHORT_TERM_GROWTH),
                delayMinutes = 0,
                graduated = true,
                repeatedOut = false,
                nextStep = 0,
            )

            RecallGrade.EASY -> Result(
                stability = (stability * 1.25).coerceAtMost(stability * MAX_SHORT_TERM_GROWTH),
                delayMinutes = 0,
                graduated = true,
                repeatedOut = false,
                nextStep = 0,
            )
        }
    }

    private const val MIN_SHORT_TERM_STABILITY = 0.4

    /** 同日最多把 S 放大 1.5 倍，避免反复刷同一张卡把稳定性拉飞。 */
    private const val MAX_SHORT_TERM_GROWTH = 1.5
}
