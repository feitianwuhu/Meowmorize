package com.meowmorize.core.domain.plan

import com.meowmorize.core.model.LearningPhase
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

/** 批次 A 对计划选择逻辑的修正测试。 */
class PlanSelectionFixTest {

    private val generator = TodayPlanGenerator()
    private val date: LocalDate = LocalDate.of(2026, 3, 10)
    private val now: Instant = Instant.parse("2026-03-10T08:00:00Z")

    private fun item(id: Long, minutes: Int, priority: Priority = Priority.NORMAL) = MemorizeItem(
        id = id,
        topic = "任务 $id",
        content = "内容 $id",
        priority = priority,
        estimatedMinutes = minutes,
        createdAt = now.minusSeconds(id * 60),
        updatedAt = now.minusSeconds(id * 60),
    )

    private fun due(id: Long, minutes: Int, overdueDays: Long = 0, priority: Priority = Priority.NORMAL) =
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
        capacity: Int = 30,
        maxNew: Int = 5,
        forced: Int? = null,
    ) = PlanRequest(
        date = date,
        dueCandidates = due,
        newCandidates = new,
        settings = UserSettings(dailyCapacityMinutes = capacity, maxNewPerDay = maxNew),
        forcedCapacityMinutes = forced,
    )

    @Test
    fun `逾期任务不再绕过复习配额`() {
        val due = (1L..3L).map { due(it, minutes = 10, overdueDays = 2) }
        val new = (10L..11L).map { NewCandidate(item(it, minutes = 10)) }
        val draft = generator.generate(request(due = due, new = new, capacity = 30))

        val reviewMinutes = draft.tasks.filter { it.reason != PlanReason.NEW }.sumOf { it.minutes }
        assertTrue(reviewMinutes <= 24, "复习应限制在 80% 容量（24 分钟），实际 $reviewMinutes")
        assertEquals(1, draft.newCount, "剩余 6 分钟不足以放 10 分钟？实际新学 ${draft.newCount}")
    }

    @Test
    fun `新学不会让短任务插队且会用碎片补齐`() {
        val new = listOf(
            NewCandidate(item(1, minutes = 10, priority = Priority.HIGH)),
            NewCandidate(item(2, minutes = 8, priority = Priority.NORMAL)),
            NewCandidate(item(3, minutes = 3, priority = Priority.LOW)),
        )
        val draft = generator.generate(request(new = new, capacity = 13, maxNew = 5))
        val newIds = draft.tasks.map { it.itemId }

        assertTrue(2L !in newIds || newIds.indexOf(3L) < newIds.indexOf(2L), "8 分钟不应先于 3 分钟被排入：$newIds")
        assertTrue(draft.notes.any { it.contains("碎片") }, "补入较短内容时应说明原因：${draft.notes}")
    }

    @Test
    fun `容量极小时选择最短的一条而不是排序第一条`() {
        val due = listOf(
            due(1, minutes = 40, overdueDays = 9),
            due(2, minutes = 20, overdueDays = 1),
        )
        val draft = generator.generate(request(due = due, capacity = 5))
        assertEquals(1, draft.tasks.size)
        assertEquals(2L, draft.tasks.first().itemId)
    }

    @Test
    fun `手动容量仍会解释算法推荐值`() {
        val due = listOf(due(1, minutes = 10))
        val draft = generator.generate(request(due = due, capacity = 40, forced = 12))
        assertEquals(12, draft.capacityMinutes)
        assertTrue(draft.notes.any { it.contains("算法推荐") }, "notes=${draft.notes}")
    }

    @Test
    fun `顺延提示同时给出条数与分钟`() {
        val due = (1L..6L).map { due(it, minutes = 10) }
        val draft = generator.generate(request(due = due, capacity = 30))
        assertEquals(3, draft.backlogCount)
        assertTrue(draft.notes.any { it.contains("分钟") && it.contains("顺延") }, "notes=${draft.notes}")
    }

    @Test
    fun `容量够时不触发配额限制`() {
        val due = (1L..2L).map { due(it, minutes = 10) }
        val new = listOf(NewCandidate(item(10, minutes = 5)))
        val draft = generator.generate(request(due = due, new = new, capacity = 60))
        assertEquals(3, draft.tasks.size)
        assertEquals(1, draft.newCount)
    }
}
