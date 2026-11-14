package com.meowmorize.core.domain.sort

import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.TaskSortOrder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskSorterTest {

    private val base: Instant = Instant.parse("2026-03-01T08:00:00Z")

    private fun item(
        id: Long,
        topic: String,
        minutes: Int = 5,
        priority: Priority = Priority.NORMAL,
        updatedHoursAgo: Long = 0,
        createdDaysAgo: Long = 0,
    ) = MemorizeItem(
        id = id,
        topic = topic,
        content = "content-$id",
        priority = priority,
        estimatedMinutes = minutes,
        createdAt = base.minusSeconds(createdDaysAgo * 86_400),
        updatedAt = base.minusSeconds(updatedHoursAgo * 3_600),
    )

    @Test
    fun `按下次复习排序时未开始的排在最后`() {
        val keys = listOf(
            TaskSortKey(item(1, "有复习"), base.plusSeconds(7_200)),
            TaskSortKey(item(2, "新任务"), null),
            TaskSortKey(item(3, "逾期"), base.minusSeconds(86_400)),
        )
        val sorted = TaskSorter.sort(keys, TaskSortOrder.NEXT_REVIEW)
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.item.id })
    }

    @Test
    fun `按主题排序时按中文拼音而不是码位`() {
        val keys = listOf(
            TaskSortKey(item(1, "政治"), null),
            TaskSortKey(item(2, "英语"), null),
            TaskSortKey(item(3, "专业课"), null),
        )
        val sorted = TaskSorter.sort(keys, TaskSortOrder.TOPIC)
        // 拼音序：英语(y) < 政治(z)，专业课(zhuanye) 与政治(zhèngzhi) 相邻
        assertEquals("英语", sorted.first().item.topic)
    }

    @Test
    fun `按时长排序时长的在前`() {
        val keys = listOf(
            TaskSortKey(item(1, "短", minutes = 3), null),
            TaskSortKey(item(2, "长", minutes = 30), null),
            TaskSortKey(item(3, "中", minutes = 12), null),
        )
        val sorted = TaskSorter.sort(keys, TaskSortOrder.DURATION)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.item.id })
    }

    @Test
    fun `按优先级排序时高优先级在前`() {
        val keys = listOf(
            TaskSortKey(item(1, "普通", priority = Priority.NORMAL), null),
            TaskSortKey(item(2, "高", priority = Priority.HIGH), null),
            TaskSortKey(item(3, "低", priority = Priority.LOW), null),
        )
        val sorted = TaskSorter.sort(keys, TaskSortOrder.PRIORITY)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.item.id })
    }

    @Test
    fun `最近更新排序按更新时间倒序`() {
        val keys = listOf(
            TaskSortKey(item(1, "一小时前", updatedHoursAgo = 1), null),
            TaskSortKey(item(2, "刚改", updatedHoursAgo = 0), null),
            TaskSortKey(item(3, "昨天", updatedHoursAgo = 24), null),
        )
        val sorted = TaskSorter.sort(keys, TaskSortOrder.RECENT_UPDATED)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.item.id })
    }
}
