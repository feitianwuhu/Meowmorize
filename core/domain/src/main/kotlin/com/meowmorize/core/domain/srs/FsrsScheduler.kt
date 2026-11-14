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
            0.72, 1.65, // Hard 惩罚、Easy 奖励（只作用于间隔，不作用于稳定性）
        )

        val DEFAULT = FsrsParams()
    }
}

/**
 * 基于遗忘曲线的排程器（FSRS-4.5 简化实现，含同日步进与逾期补偿）。
 *
 * 核心：维护「稳定性 S（天）」与「难度 D（1~10）」，
 * `R(t, S) = (1 + 19/81 · t / S) ^ -0.5` 给出任意时刻的回忆概率，
 * 反解出「让回忆概率回落到目标保持率」所需天数作为间隔。
 *
 * 本版修正了三处会系统性算错的地方：
 * 1. 同日重练用真实 elapsed（分钟级），旧实现把 10 分钟当成 6 小时；
 * 2. 逾期成功回忆给稳定性奖励，避免「逾期 1 天与 10 天同结果」；
 * 3. HARD/EASY 只调整间隔，不再乘稳定性（此前与难度变化重复计权）。
 *
 * 纯函数实现，无 Android 依赖，可完整单测。
 */
class FsrsScheduler(private val params: FsrsParams = FsrsParams.DEFAULT) {

    /**
     * 计算 [now] 时刻的回忆概率。
     * 新任务返回 0（还没有任何记忆痕迹）。
     */
    fun retrievability(state: ReviewState, now: Instant): Double {
        if (state.isNew || state.stability <= 0.0) return 0.0
        val last = state.lastReviewedAt ?: return 0.0
        return forgettingCurve(elapsedDays(last, now), state.stability)
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
     * @param todayReviewCount 该内容今天已经评过几次（用于同日步进与上限）
     * @param config 可调参数
     */
    fun review(
        state: ReviewState?,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        itemId: Long = state?.itemId ?: 0L,
        todayReviewCount: Int = 0,
        config: SrsConfig = SrsConfig(),
    ): ReviewOutcome {
        val current = state
        if (current == null || current.isNew) {
            return firstReview(itemId, grade, now, targetRetention, todayReviewCount, config)
        }

        val lastReviewed = current.lastReviewedAt
        // 关键修正：不再把短间隔夹到 0.25 天，10 分钟就是 10 分钟
        val elapsed = if (lastReviewed == null) 1.0 else elapsedDays(lastReviewed, now)
        val retrievability = forgettingCurve(elapsed, current.stability)

        val inShortTerm = current.phase == LearningPhase.LEARNING ||
            current.phase == LearningPhase.RELEARNING ||
            (elapsed < 1.0 && todayReviewCount > 0)
        if (inShortTerm) {
            return shortTermReview(current, grade, now, targetRetention, todayReviewCount, config)
        }

        return longTermReview(current, grade, now, targetRetention, retrievability, todayReviewCount, config)
    }

    /**
     * 预览四个按钮分别对应的下次间隔。
     * fuzz 的种子固定为 (itemId, reps)，保证预览与确认结果一致。
     */
    fun previewIntervals(
        state: ReviewState?,
        now: Instant,
        targetRetention: Double,
        todayReviewCount: Int = 0,
        config: SrsConfig = SrsConfig(),
    ): Map<RecallGrade, Int> {
        return RecallGrade.entries.associateWith { grade ->
            review(
                state = state,
                grade = grade,
                now = now,
                targetRetention = targetRetention,
                todayReviewCount = todayReviewCount,
                config = config,
            ).nextIntervalDays
        }
    }

    // ---------- 首次学习 ----------

    private fun firstReview(
        itemId: Long,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        todayReviewCount: Int,
        config: SrsConfig,
    ): ReviewOutcome {
        val s0 = params.weights[grade.score - 1].coerceIn(MIN_STABILITY, MAX_STABILITY)
        val d0 = initialDifficulty(grade)

        if (grade == RecallGrade.AGAIN) {
            val step = ShortTermScheduler.step(s0, grade, todayReviewCount, config)
            return ReviewOutcome(
                state = ReviewState(
                    itemId = itemId,
                    dueAt = now.plus(Duration.ofMinutes(step.delayMinutes)),
                    stability = step.stability,
                    difficulty = d0,
                    reps = 1,
                    lapses = 1,
                    phase = LearningPhase.LEARNING,
                    lastReviewedAt = now,
                    scheduledDays = 0,
                ),
                nextIntervalDays = 0,
                retrievability = 0.0,
                stabilityBefore = 0.0,
            )
        }

        val days = finalizeInterval(intervalDaysFor(s0, targetRetention), itemId, reps = 1, config)
        return ReviewOutcome(
            state = ReviewState(
                itemId = itemId,
                dueAt = now.plus(Duration.ofDays(days.toLong())),
                stability = s0,
                difficulty = d0,
                reps = 1,
                lapses = 0,
                phase = LearningPhase.REVIEW,
                lastReviewedAt = now,
                scheduledDays = days,
            ),
            nextIntervalDays = days,
            retrievability = 0.0,
            stabilityBefore = 0.0,
        )
    }

    // ---------- 同日重练 ----------

    private fun shortTermReview(
        current: ReviewState,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        todayReviewCount: Int,
        config: SrsConfig,
    ): ReviewOutcome {
        val stabilityBefore = current.stability
        val term = ShortTermScheduler.step(stabilityBefore, grade, todayReviewCount, config)
        val difficulty = nextDifficulty(current.difficulty, grade, config)

        val phase = when {
            grade == RecallGrade.AGAIN -> {
                if (current.phase == LearningPhase.LEARNING) LearningPhase.LEARNING else LearningPhase.RELEARNING
            }
            grade == RecallGrade.HARD -> {
                if (current.phase == LearningPhase.LEARNING) LearningPhase.LEARNING else LearningPhase.RELEARNING
            }
            else -> LearningPhase.REVIEW
        }

        val scheduledDays: Int
        val dueAt: Instant
        if (term.graduated) {
            val modifier = if (grade == RecallGrade.EASY) config.easyBonus else 1.0
            val raw = intervalDaysFor(term.stability, targetRetention) * modifier
            val minimumDays = if (grade == RecallGrade.EASY) 2 else 1
            scheduledDays = finalizeInterval(raw, current.itemId, current.reps + 1, config)
                .coerceAtLeast(minimumDays)
            dueAt = now.plus(Duration.ofDays(scheduledDays.toLong()))
        } else {
            scheduledDays = 0
            dueAt = now.plus(Duration.ofMinutes(term.delayMinutes))
        }

        return ReviewOutcome(
            state = ReviewState(
                itemId = current.itemId,
                dueAt = dueAt,
                stability = term.stability.coerceIn(MIN_STABILITY, MAX_STABILITY),
                difficulty = difficulty,
                reps = current.reps + 1,
                lapses = current.lapses + if (grade == RecallGrade.AGAIN) 1 else 0,
                phase = phase,
                lastReviewedAt = now,
                scheduledDays = scheduledDays,
            ),
            nextIntervalDays = scheduledDays,
            retrievability = forgettingCurve(
                elapsedDays(current.lastReviewedAt ?: now, now),
                stabilityBefore,
            ),
            stabilityBefore = stabilityBefore,
        )
    }

    // ---------- 长期复习 ----------

    private fun longTermReview(
        current: ReviewState,
        grade: RecallGrade,
        now: Instant,
        targetRetention: Double,
        retrievability: Double,
        todayReviewCount: Int,
        config: SrsConfig,
    ): ReviewOutcome {
        val stabilityBefore = current.stability
        val difficulty = nextDifficulty(current.difficulty, grade, config)

        if (grade == RecallGrade.AGAIN) {
            val step = ShortTermScheduler.step(stabilityBefore, grade, todayReviewCount, config)
            return ReviewOutcome(
                state = ReviewState(
                    itemId = current.itemId,
                    dueAt = now.plus(Duration.ofMinutes(step.delayMinutes)),
                    stability = step.stability.coerceIn(MIN_STABILITY, MAX_STABILITY),
                    difficulty = difficulty,
                    reps = current.reps + 1,
                    lapses = current.lapses + 1,
                    phase = LearningPhase.RELEARNING,
                    lastReviewedAt = now,
                    scheduledDays = 0,
                ),
                nextIntervalDays = 0,
                retrievability = retrievability,
                stabilityBefore = stabilityBefore,
            )
        }

        val grew = recallStability(stabilityBefore, difficulty, retrievability)
        val overdueDays = ((Duration.between(current.dueAt, now).toMinutes().toDouble()) / MINUTES_PER_DAY)
            .coerceAtLeast(0.0)
        val withOverdue = grew * overdueBonus(overdueDays, config)
        val stability = withOverdue.coerceIn(MIN_STABILITY, MAX_STABILITY)

        // HARD/EASY 只调间隔，不再乘稳定性
        val modifier = when (grade) {
            RecallGrade.HARD -> config.hardPenalty
            RecallGrade.EASY -> config.easyBonus
            else -> 1.0
        }
        val rawInterval = intervalDaysFor(stability, targetRetention) * modifier
        val days = finalizeInterval(rawInterval, current.itemId, current.reps + 1, config)

        return ReviewOutcome(
            state = ReviewState(
                itemId = current.itemId,
                dueAt = now.plus(Duration.ofDays(days.toLong())),
                stability = stability,
                difficulty = difficulty,
                reps = current.reps + 1,
                lapses = current.lapses,
                phase = LearningPhase.REVIEW,
                lastReviewedAt = now,
                scheduledDays = days,
            ),
            nextIntervalDays = days,
            retrievability = retrievability,
            stabilityBefore = stabilityBefore,
        )
    }

    // ---------- 内部计算 ----------

    private fun initialDifficulty(grade: RecallGrade): Double {
        val d = params.weights[4] - (grade.score - 3) * params.weights[5]
        return d.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /**
     * 难度更新：向「轻松」的初值回归，而不是「记得」的初值。
     * 长期记得的内容应该逐步靠近低难度，否则 (11-D) 会一直压低增长。
     */
    private fun nextDifficulty(current: Double, grade: RecallGrade, config: SrsConfig): Double {
        val delta = -params.weights[6] * (grade.score - 3)
        val raw = current + delta
        val target = params.weights[4] - params.weights[5]
        val reversion = config.difficultyReversion.coerceIn(0.0, 0.5)
        return (reversion * target + (1 - reversion) * raw).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /**
     * 逾期奖励系数（仅作用于稳定性）。
     * 30 天后封顶，避免长期不打开应用时 S 因一次「还记得」而暴涨。
     */
    internal fun overdueBonus(overdueDays: Double, config: SrsConfig): Double =
        if (overdueDays < 1.0) 1.0
        else 1.0 + config.overdueMaxBonus * min(overdueDays, 30.0) / 30.0

    /** 成功回忆后的稳定性增长：越难的内容（D 大）、越临界的回忆（R 低）涨得越少。 */
    private fun recallStability(s: Double, d: Double, r: Double): Double {
        val w = params.weights
        val growth = exp(w[8]) *
            (11 - d) *
            s.pow(-w[9]) *
            (exp(w[10] * (1 - r)) - 1)
        return s * (1 + growth)
    }

    private fun finalizeInterval(
        rawDays: Double,
        itemId: Long,
        reps: Int,
        config: SrsConfig,
    ): Int {
        val jittered = if (config.fuzzEnabled) {
            rawDays * IntervalFuzz.factor(itemId * 1_000_003L + reps)
        } else {
            rawDays
        }
        return jittered.roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(config.maxIntervalDays.coerceAtLeast(1))
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
    }
}
