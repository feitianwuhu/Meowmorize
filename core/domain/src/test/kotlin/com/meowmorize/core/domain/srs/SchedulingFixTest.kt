package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewState
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批次 A 的回归测试：同日重练、逾期补偿、间隔调整、fuzz 与 leech。
 */
class SchedulingFixTest {

    private val scheduler = FsrsScheduler()
    private val now: Instant = Instant.parse("2026-03-10T20:00:00Z")

    private fun reviewedState(
        stability: Double = 10.0,
        difficulty: Double = 5.0,
        lastReviewedDaysAgo: Double = 10.0,
        dueAt: Instant = now.minus(Duration.ofDays(1)),
        phase: LearningPhase = LearningPhase.REVIEW,
        lapses: Int = 0,
        reps: Int = 1,
    ) = ReviewState(
        itemId = 42L,
        dueAt = dueAt,
        stability = stability,
        difficulty = difficulty,
        reps = reps,
        lapses = lapses,
        phase = phase,
        lastReviewedAt = now.minus(Duration.ofMinutes((lastReviewedDaysAgo * 1440).toLong())),
        scheduledDays = stability.toInt(),
    )

    @Test
    fun `十分钟后重练的回忆概率接近 1 而不是被当成六小时`() {
        val state = reviewedState(lastReviewedDaysAgo = 10.0 / 1440.0)
        val outcome = scheduler.review(
            state = state,
            grade = RecallGrade.GOOD,
            now = now,
            targetRetention = 0.9,
            todayReviewCount = 1,
        )
        assertTrue(outcome.retrievability > 0.999, "10 分钟的 R 应接近 1，实际 ${outcome.retrievability}")
    }

    @Test
    fun `同日第二次忘记使用第二步冷却`() {
        val state = reviewedState(phase = LearningPhase.LEARNING, lastReviewedDaysAgo = 10.0 / 1440.0)
        val outcome = scheduler.review(
            state = state,
            grade = RecallGrade.AGAIN,
            now = now,
            targetRetention = 0.9,
            todayReviewCount = 1,
        )
        val minutes = Duration.between(now, outcome.state.dueAt).toMinutes()
        assertEquals(60L, minutes, "第二次应进入 60 分钟步进")
        assertEquals(0, outcome.nextIntervalDays)
    }

    @Test
    fun `超过当日重评上限后不再排十分钟`() {
        val state = reviewedState(phase = LearningPhase.RELEARNING, lastReviewedDaysAgo = 5.0 / 1440.0)
        val outcome = scheduler.review(
            state = state,
            grade = RecallGrade.AGAIN,
            now = now,
            targetRetention = 0.9,
            todayReviewCount = 3,
        )
        val minutes = Duration.between(now, outcome.state.dueAt).toMinutes()
        assertTrue(minutes >= 300, "超过上限应冷却到 6 小时左右，实际 $minutes 分钟")
        assertEquals(0, outcome.nextIntervalDays)
        assertEquals(state.lapses + 1, outcome.state.lapses, "lapses 仍要如实累加")
    }

    @Test
    fun `学习阶段的记得会毕业到复习阶段`() {
        val state = reviewedState(phase = LearningPhase.LEARNING, lastReviewedDaysAgo = 30.0 / 1440.0)
        val outcome = scheduler.review(
            state = state,
            grade = RecallGrade.GOOD,
            now = now,
            targetRetention = 0.9,
            todayReviewCount = 1,
        )
        assertEquals(LearningPhase.REVIEW, outcome.state.phase)
        assertTrue(outcome.nextIntervalDays >= 1)
    }

    @Test
    fun `逾期后成功回忆会获得稳定性奖励`() {
        val onTime = scheduler.review(
            state = reviewedState(lastReviewedDaysAgo = 10.0, dueAt = now),
            grade = RecallGrade.GOOD,
            now = now,
            targetRetention = 0.9,
        )
        val overdue = scheduler.review(
            state = reviewedState(lastReviewedDaysAgo = 20.0, dueAt = now.minus(Duration.ofDays(10))),
            grade = RecallGrade.GOOD,
            now = now,
            targetRetention = 0.9,
        )
        assertTrue(
            overdue.state.stability > onTime.state.stability,
            "逾期 10 天仍记得，S 应高于准时复习：${overdue.state.stability} vs ${onTime.state.stability}",
        )
    }

    @Test
    fun `逾期奖励系数在三十天封顶`() {
        val config = SrsConfig()
        assertEquals(1.0, scheduler.overdueBonus(0.5, config), 1e-9)
        assertEquals(1.0 + 0.10 * 10.0 / 30.0, scheduler.overdueBonus(10.0, config), 1e-9)
        assertEquals(1.10, scheduler.overdueBonus(30.0, config), 1e-9)
        assertEquals(1.10, scheduler.overdueBonus(100.0, config), 1e-9)
    }

    @Test
    fun `轻松档的间隔明显长于记得档`() {
        val state = reviewedState(stability = 10.0, difficulty = 5.0, lastReviewedDaysAgo = 10.0)
        val good = scheduler.review(state, RecallGrade.GOOD, now, 0.9)
        val easy = scheduler.review(state, RecallGrade.EASY, now, 0.9)
        val ratio = easy.nextIntervalDays.toDouble() / good.nextIntervalDays
        assertTrue(ratio > 1.3, "EASY 应只通过间隔获得奖励，实际倍率 $ratio")
    }

    @Test
    fun `fuzz 对同一张卡是确定性的`() {
        val state = reviewedState(stability = 6.0, difficulty = 5.0, lastReviewedDaysAgo = 6.0)
        val config = SrsConfig(fuzzEnabled = true)
        val first = scheduler.review(state, RecallGrade.GOOD, now, 0.9, config = config)
        val second = scheduler.review(state, RecallGrade.GOOD, now, 0.9, config = config)
        assertEquals(first.nextIntervalDays, second.nextIntervalDays)
    }

    @Test
    fun `fuzz 会把不同内容打散`() {
        val config = SrsConfig(fuzzEnabled = true)
        val intervals = (1L..12L).map { id ->
            val state = reviewedState(stability = 8.0, difficulty = 5.0, lastReviewedDaysAgo = 8.0)
                .copy(itemId = id)
            scheduler.review(state, RecallGrade.GOOD, now, 0.9, itemId = id, config = config).nextIntervalDays
        }
        assertTrue(intervals.distinct().size >= 2, "fuzz 至少应产生两个不同间隔：$intervals")
    }

    @Test
    fun `最大间隔会被强制夹取`() {
        val state = reviewedState(stability = 500.0, difficulty = 3.0, lastReviewedDaysAgo = 500.0)
        val outcome = scheduler.review(
            state = state,
            grade = RecallGrade.EASY,
            now = now,
            targetRetention = 0.9,
            config = SrsConfig(maxIntervalDays = 30),
        )
        assertTrue(outcome.nextIntervalDays <= 30, "实际 ${outcome.nextIntervalDays}")
    }

    @Test
    fun `leech 判定覆盖次数与比例两条规则`() {
        assertTrue(!LeechPolicy.isLeech(lapses = 3, reps = 10))
        assertTrue(LeechPolicy.isLeech(lapses = 4, reps = 10))
        assertTrue(LeechPolicy.isLeech(lapses = 3, reps = 6))
        assertTrue(!LeechPolicy.isLeech(lapses = 1, reps = 8))
    }
}
