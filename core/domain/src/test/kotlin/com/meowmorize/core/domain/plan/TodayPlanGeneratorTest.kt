package com.meowmorize.core.domain.plan

import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.LoadLevel
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.PlanReason
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayPlanGeneratorTest {

    private val generator = TodayPlanGenerator()
    private val date: LocalDate = LocalDate.of(2026, 3, 2)
    private val now: Instant = Instant.parse("2026-03-02T08:00:00Z")

    private fun item(
        id: Long,
        minutes: Int,
        priority: Priority = Priority.NORMAL,
        createdAt: Instant = now.minusSeconds(id * 60),
    ) = MemorizeItem(
        id = id,
        topic = "任务 $id",
        content = "内容 $id",
        priority = priority,
        estimatedMinutes = minutes,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun dueCandidate(id: Long, minutes: Int, overdueDays: Long = 0, priority: Priority = Priority.NORMAL) =
        DueCandidate(
            item = item(id, minutes, priority),
            state = ReviewState(
                itemId = id,
                dueAt = now.minusSeconds(overdueDays * 86_400),
                stability = 3.0,
                difficulty = 5.0,
                reps = 1,
                phase = LearningPhase.REVIEW,
            ),
            overdueDays = overdueDays,
            retrievability = 0.85,
        )

    private fun request(
        due: List<DueCandidate> = emptyList(),
        new: List<NewCandidate> = emptyList(),
        settings: UserSettings = UserSettings(dailyCapacityMinutes = 30),
        ratios: List<Float> = emptyList(),
        weekend: Boolean = false,
        forced: Int? = null,
    ) = PlanRequest(
        date = date,
        dueCandidates = due,
        newCandidates = new,
        settings = settings,
        recentCompletionRatios = ratios,
        isWeekend = weekend,
        forcedCapacityMinutes = forced,
    )

    @Test
    fun `总时长不会超过当日容量`() {
        val due = (1L..6L).map { dueCandidate(it, minutes = 10) }
        val draft = generator.generate(request(due = due, settings = UserSettings(dailyCapacityMinutes = 30)))

        assertTrue(draft.plannedMinutes <= 30, "容量 30 分钟时不能排出 ${draft.plannedMinutes} 分钟")
        assertEquals(3, draft.tasks.size)
        assertEquals(3, draft.backlogCount)
        assertTrue(draft.notes.any { it.contains("顺延") })
    }

    @Test
    fun `容量极小时也至少安排一条`() {
        val draft = generator.generate(
            request(due = listOf(dueCandidate(1L, minutes = 20)), settings = UserSettings(dailyCapacityMinutes = 5)),
        )
        assertEquals(1, draft.tasks.size)
    }

    @Test
    fun `近期完成率偏低会自动减负`() {
        val due = (1L..4L).map { dueCandidate(it, minutes = 10) }
        val draft = generator.generate(
            request(
                due = due,
                settings = UserSettings(dailyCapacityMinutes = 40),
                ratios = listOf(0.3f, 0.4f, 0.5f),
            ),
        )
        assertTrue(draft.capacityMinutes < 40, "低完成率应下调容量")
        assertEquals(28, draft.capacityMinutes, "40 分钟 × 0.7 = 28 分钟")
        assertTrue(draft.elasticityFactor < 1.0)
        assertTrue(draft.notes.any { it.contains("减负") })
    }

    @Test
    fun `手动设定的容量不会被弹性规则放大`() {
        val due = (1L..5L).map { dueCandidate(it, minutes = 10) }
        val draft = generator.generate(
            request(
                due = due,
                settings = UserSettings(dailyCapacityMinutes = 40),
                ratios = listOf(0.2f, 0.3f),
                weekend = true,
                forced = 12,
            ),
        )
        assertEquals(12, draft.capacityMinutes)
        assertTrue(draft.plannedMinutes <= 12)
    }

    @Test
    fun `周末容量按八五折计算`() {
        val due = (1L..4L).map { dueCandidate(it, minutes = 10) }
        val weekday = generator.generate(request(due = due, settings = UserSettings(dailyCapacityMinutes = 40)))
        val weekend = generator.generate(
            request(due = due, settings = UserSettings(dailyCapacityMinutes = 40), weekend = true),
        )
        assertTrue(weekend.capacityMinutes < weekday.capacityMinutes)
        assertEquals(34, weekend.capacityMinutes)
    }

    @Test
    fun `每日新任务数量受上限约束`() {
        val new = (1L..10L).map { NewCandidate(item(it, minutes = 5)) }
        val draft = generator.generate(
            request(
                new = new,
                settings = UserSettings(dailyCapacityMinutes = 60, maxNewPerDay = 2),
            ),
        )
        assertEquals(2, draft.newCount)
        assertTrue(draft.tasks.all { it.reason == PlanReason.NEW })
    }

    @Test
    fun `有积压时优先复习并给新学留出空间`() {
        val due = (1L..5L).map { dueCandidate(it, minutes = 10) }
        val new = (10L..12L).map { NewCandidate(item(it, minutes = 10)) }
        val draft = generator.generate(
            request(
                due = due,
                new = new,
                settings = UserSettings(dailyCapacityMinutes = 30, maxNewPerDay = 5),
            ),
        )
        val dueCount = draft.tasks.count { it.reason != PlanReason.NEW }
        assertEquals(2, dueCount, "80% 容量留给复习")
        assertEquals(1, draft.newCount, "剩余 10 分钟安排 1 条新学")
        assertTrue(draft.backlogCount >= 3)
    }

    @Test
    fun `逾期任务排在今日到期之前`() {
        val due = listOf(
            dueCandidate(1L, minutes = 5, overdueDays = 0),
            dueCandidate(2L, minutes = 5, overdueDays = 3),
        )
        val draft = generator.generate(request(due = due))
        assertEquals(2L, draft.tasks.first().itemId)
        assertEquals(PlanReason.OVERDUE, draft.tasks.first().reason)
    }

    @Test
    fun `没有候选任务时给出休息提示`() {
        val draft = generator.generate(request())
        assertTrue(draft.tasks.isEmpty())
        assertTrue(draft.notes.any { it.contains("休息") })
        assertEquals(LoadLevel.RELAXED, draft.loadLevel)
    }

    @Test
    fun `高目标保持率会提示复习更频繁`() {
        val draft = generator.generate(
            request(
                due = listOf(dueCandidate(1L, minutes = 5)),
                settings = UserSettings(dailyCapacityMinutes = 30, targetRetention = 0.96),
            ),
        )
        assertTrue(draft.notes.any { it.contains("保持率") })
    }
}
