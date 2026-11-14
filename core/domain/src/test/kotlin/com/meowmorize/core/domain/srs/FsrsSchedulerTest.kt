package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewState
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsSchedulerTest {

    private val scheduler = FsrsScheduler()
    private val base: Instant = Instant.parse("2026-03-01T08:00:00Z")

    private fun reviewedState(
        stability: Double = 2.0,
        difficulty: Double = 5.0,
        daysAgo: Long = 2,
    ) = ReviewState(
        itemId = 1L,
        dueAt = base.minus(Duration.ofDays(daysAgo)).plus(Duration.ofDays(stability.toLong())),
        stability = stability,
        difficulty = difficulty,
        reps = 1,
        phase = LearningPhase.REVIEW,
        lastReviewedAt = base.minus(Duration.ofDays(daysAgo)),
        scheduledDays = stability.toInt(),
    )

    @Test
    fun `首次评分为记得时至少安排一天后`() {
        val outcome = scheduler.review(null, RecallGrade.GOOD, base, 0.9, itemId = 42L)
        assertTrue(outcome.nextIntervalDays >= 1, "首次好评应至少排到明天")
        assertEquals(LearningPhase.REVIEW, outcome.state.phase)
        assertEquals(42L, outcome.state.itemId)
        assertEquals(1, outcome.state.reps)
    }

    @Test
    fun `回忆概率在稳定性天数处回落到约九成`() {
        val state = reviewedState(stability = 2.0, daysAgo = 0)
        val retrievability = scheduler.retrievability(state, base.plus(Duration.ofDays(2)))
        assertEquals(0.9, retrievability, 0.02)
    }

    @Test
    fun `回忆概率随时间单调下降`() {
        val state = reviewedState(stability = 5.0, daysAgo = 0)
        val r0 = scheduler.retrievability(state, base)
        val r3 = scheduler.retrievability(state, base.plus(Duration.ofDays(3)))
        val r10 = scheduler.retrievability(state, base.plus(Duration.ofDays(10)))
        assertTrue(r0 > r3, "起点概率应最高")
        assertTrue(r3 > r10, "时间越久概率越低")
        assertEquals(1.0, r0, 0.01)
    }

    @Test
    fun `评分忘了会当天重练并进入重学`() {
        val outcome = scheduler.review(reviewedState(), RecallGrade.AGAIN, base, 0.9)
        assertEquals(0, outcome.nextIntervalDays)
        assertEquals(LearningPhase.RELEARNING, outcome.state.phase)
        assertTrue(outcome.state.dueAt.isBefore(base.plus(Duration.ofHours(1))))
        assertEquals(1, outcome.state.lapses)
    }

    @Test
    fun `成功回忆会提升稳定性`() {
        val before = reviewedState(stability = 4.0)
        val outcome = scheduler.review(before, RecallGrade.GOOD, base, 0.9)
        assertTrue(outcome.state.stability > before.stability, "记住之后稳定性应增长")
        assertTrue(outcome.nextIntervalDays >= 1)
    }

    @Test
    fun `目标保持率越高间隔越短`() {
        val relaxed = scheduler.intervalDaysFor(stability = 30.0, retention = 0.85)
        val strict = scheduler.intervalDaysFor(stability = 30.0, retention = 0.95)
        assertTrue(strict < relaxed, "更高保持率意味着更频繁复习")
    }

    @Test
    fun `四个评分的预览间隔单调不减`() {
        val state = reviewedState(stability = 10.0, daysAgo = 6)
        val previews = scheduler.previewIntervals(state, base, 0.9)
        val again = previews.getValue(RecallGrade.AGAIN)
        val hard = previews.getValue(RecallGrade.HARD)
        val good = previews.getValue(RecallGrade.GOOD)
        val easy = previews.getValue(RecallGrade.EASY)
        assertTrue(again <= hard, "忘了不应比模糊更久")
        assertTrue(hard <= good, "模糊不应比记得更久")
        assertTrue(good <= easy, "记得不应比轻松更久")
    }

    @Test
    fun `间隔不会因为极端评分溢出`() {
        val state = reviewedState(stability = 5000.0, daysAgo = 400)
        val outcome = scheduler.review(state, RecallGrade.EASY, base, 0.9)
        assertTrue(outcome.state.stability.isFinite())
        assertTrue(outcome.nextIntervalDays in 1..36500)
    }
}
