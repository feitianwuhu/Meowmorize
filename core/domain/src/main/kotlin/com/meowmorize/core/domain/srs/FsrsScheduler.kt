package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewOutcome
import com.meowmorize.core.model.ReviewState
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-4.5 权重。默认值来自 FSRS 官方开源实现的默认参数，
 * 后续可基于 [com.meowmorize.core.model.ReviewLog] 做离线拟合来个性化。
 */
class FsrsParams(val weights: DoubleArray = DEFAULT_WEIGHTS) {
    init {
        require(weights.size >= 17) { "FSRS 需要 17 个权重" }
    }

    companion object {
        val DEFAULT_WEIGHTS = doubleArrayOf(
            0.4872, 1.4003, 3.7145, 13.8206, // 初始稳定性 S0(Again/Hard/Good/Easy)
            5.1618, 1.2298, // 初始难度 D0 的截距与斜率
            0.8975, 0.031, // 难度更新系数、均值回归强度
            1.6474, 0.1367, 1.0461, // 成功回忆时稳定性的增长曲线
            2.1072, 0.0793, 0.4156, 0.0031, // 遗忘后稳定性的衰减
            0.72, 1.65, // Hard 惩罚、Easy 奖励（对间隔的微调）
        )

        val DEFAULT = FsrsParams()
    }
}

/**
 * 基于遗忘曲线的排程器（FSRS 简化实现）。
 *
 * 核心思想：不再用固定的 1/2/4/7 天间隔，而是维护每条内容的
 * 「稳定性 S（天）」与「难度 D（1~10）」，由
 * `R(t, S) = (1 + 19/81 · t / S) ^ -0.5` 计算任意时刻的回忆概率，
 * 反解出「让回忆概率回落到目标保持率」所需的天数作为下次复习间隔。
 *
 * 这样做的直接好处：用户某天没空（任务被顺延）时，算法只是按真实的
 * 遗忘进度重新计算，而不会像固定间隔那样产生连锁的复习雪崩。
 *
 * 该类为纯函数式实现，无 Android 依赖，可完整单测。
 */
class FsrsScheduler(private val params: FsrsParams = FsrsParams.DEFAULT) {

    /**
     * 计算 [now] 时刻的回忆概率。
     * 新任务返回 0（还没有任何记忆痕迹）。
     */
    fun retrievability(state: ReviewState, now: Instant): Double {
        if (state.isNew || state.stability <= 0.0) return 0.0
        val last = state.lastReviewedAt ?: return 0.0
        val elapsedDays = elapsedDays(last, now)
        return forgettingCurve(elapsedDays, state.stability)
    }

    /** 遗忘曲线：t 天后仍能回忆的概率。 */
    fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = max(elapsedDays, 0.0)
        return (1.0 + FACTOR * t / stability).pow(DECAY).coerceIn(0.0, 1.0)
    }

    /** 由稳定性反解间隔天数：希望到期时回忆概率为 [retention]。 */
    fun intervalDaysFor(stability: Double, retention: Double): Double {
        val r = retention.coerceIn(0.70, 0.99)
        return stability / FACTOR * (r.pow(1.0 / DECAY) - 1.0)
    }

    /**
     * 对一条内容进行评分并生成新的排程。
     *
     * @param state 当前记忆状态；`null` 表示这是一条新任务
     * @param grade 用户自评
     * @param now 评分时刻
     * @param targetRetention 目标保持率
     */
    fun review(
        state: ReviewState?,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        itemId: Long = state?.itemId ?: 0L,
    ): ReviewOutcome {
        val current = state
        if (current == null || current.isNew) {
            return firstReview(itemId, grade, now, targetRetention)
        }

        val lastReviewed = current.lastReviewedAt
        val elapsed = if (lastReviewed == null) 1.0 else max(elapsedDays(lastReviewed, now), MIN_ELAPSED_DAYS)
        val r = forgettingCurve(elapsed, current.stability)

        val stabilityBefore = current.stability
        val newDifficulty = nextDifficulty(current.difficulty, grade)
        val newStability = when (grade) {
            RecallGrade.AGAIN -> forgetStability(stabilityBefore, newDifficulty, r)
            else -> recallStability(stabilityBefore, newDifficulty, r)
        }.let { s ->
            val bonus = when (grade) {
                RecallGrade.HARD -> params.weights[15]
                RecallGrade.EASY -> params.weights[16]
                else -> 1.0
            }
            (s * bonus).coerceIn(MIN_STABILITY, MAX_STABILITY)
        }

        val interval = intervalDaysFor(newStability, targetRetention)
        val phase = when {
            grade == RecallGrade.AGAIN -> LearningPhase.RELEARNING
            current.phase == LearningPhase.RELEARNING || current.phase == LearningPhase.LEARNING ->
                LearningPhase.REVIEW
            else -> LearningPhase.REVIEW
        }

        // 「忘了」意味着当天需要再练一次，但不再累积新的复习次数；
        // 这样才能避免用户连续点「忘了」时被无限追加任务。
        val repeatToday = grade == RecallGrade.AGAIN
        val scheduledDays = if (repeatToday) 0 else interval.roundToInt().coerceAtLeast(1)
        val dueAt = if (repeatToday) now.plus(REPRACTICE_DELAY) else now.plus(Duration.ofDays(scheduledDays.toLong()))

        return ReviewOutcome(
            state = ReviewState(
                itemId = current.itemId,
                dueAt = dueAt,
                stability = newStability,
                difficulty = newDifficulty,
                reps = current.reps + 1,
                lapses = current.lapses + if (grade == RecallGrade.AGAIN) 1 else 0,
                phase = phase,
                lastReviewedAt = now,
                scheduledDays = scheduledDays,
            ),
            nextIntervalDays = scheduledDays,
            retrievability = r,
            stabilityBefore = stabilityBefore,
        )
    }

    /** 首次学习：S0 由评分决定，直接按目标保持率折算第一次间隔。 */
    private fun firstReview(
        itemId: Long,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
    ): ReviewOutcome {
        val s0 = params.weights[grade.score - 1].coerceIn(MIN_STABILITY, MAX_STABILITY)
        val d0 = initialDifficulty(grade)
        val repeatToday = grade == RecallGrade.AGAIN
        val interval = intervalDaysFor(s0, targetRetention)
        val scheduledDays = if (repeatToday) 0 else interval.roundToInt().coerceAtLeast(1)
        val dueAt = if (repeatToday) now.plus(REPRACTICE_DELAY) else now.plus(Duration.ofDays(scheduledDays.toLong()))

        return ReviewOutcome(
            state = ReviewState(
                itemId = itemId,
                dueAt = dueAt,
                stability = s0,
                difficulty = d0,
                reps = 1,
                lapses = 0,
                phase = if (repeatToday) LearningPhase.LEARNING else LearningPhase.REVIEW,
                lastReviewedAt = now,
                scheduledDays = scheduledDays,
            ),
            nextIntervalDays = scheduledDays,
            retrievability = 0.0,
            stabilityBefore = 0.0,
        )
    }

    private fun initialDifficulty(grade: RecallGrade): Double {
        val d = params.weights[4] - (grade.score - 3) * params.weights[5]
        return d.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    private fun nextDifficulty(current: Double, grade: RecallGrade): Double {
        val delta = -params.weights[6] * (grade.score - 3)
        val raw = current + delta
        val reverted = params.weights[7] * initialDifficulty(RecallGrade.GOOD) + (1 - params.weights[7]) * raw
        return reverted.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /** 成功回忆后的稳定性增长：越难的内容（D 大）、越临界的回忆（R 低）涨得越少。 */
    private fun recallStability(s: Double, d: Double, r: Double): Double {
        val w = params.weights
        val growth = exp(w[8]) *
            (11 - d) *
            s.pow(-w[9]) *
            (exp(w[10] * (1 - r)) - 1)
        return s * (1 + growth)
    }

    /** 遗忘后的稳定性：基于再次学习能恢复的记忆强度，通常显著低于遗忘前。 */
    private fun forgetStability(s: Double, d: Double, r: Double): Double {
        val w = params.weights
        return w[11] *
            d.pow(-w[12]) *
            ((s + 1).pow(w[13]) - 1) *
            exp(w[14] * (1 - r))
    }

    /**
     * 预览四个按钮分别对应的下次间隔，用于复习页按钮上的「1 天后」提示。
     * 让用户在点之前就知道代价，是降低心理负担的有效手段。
     */
    fun previewIntervals(state: ReviewState?, now: Instant, targetRetention: Double): Map<RecallGrade, Int> {
        return RecallGrade.entries.associateWith { grade ->
            review(state, grade, now, targetRetention).nextIntervalDays
        }
    }

    private fun elapsedDays(from: Instant, to: Instant): Double {
        val minutes = Duration.between(from, to).toMinutes().toDouble()
        return max(minutes / MINUTES_PER_DAY, 0.0)
    }

    companion object {
        private const val DECAY = -0.5
        private const val FACTOR = 19.0 / 81.0
        private const val MINUTES_PER_DAY = 1440.0
        private const val MIN_STABILITY = 0.1
        private const val MAX_STABILITY = 36500.0
        private const val MIN_DIFFICULTY = 1.0
        private const val MAX_DIFFICULTY = 10.0
        private const val MIN_ELAPSED_DAYS = 0.25

        /** 当天重练的冷却时间：太短会变成机械点击，太长会拖到第二天。 */
        val REPRACTICE_DELAY: Duration = Duration.ofMinutes(10)
    }
}
