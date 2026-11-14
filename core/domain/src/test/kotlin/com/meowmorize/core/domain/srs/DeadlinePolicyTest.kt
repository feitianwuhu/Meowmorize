package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.Subject
import com.meowmorize.core.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DeadlinePolicyTest {

    private val now = Instant.parse("2026-05-01T09:00:00Z")

    private fun deadlineIn(days: Double): Instant =
        now.plus(Duration.ofMinutes((days * 1440).toLong()))

    @Test
    fun `没有截止日期时保持率不变`() {
        assertEquals(0.9, DeadlinePolicy.targetRetention(0.9, null, now, 30), 1e-9)
    }

    @Test
    fun `冲刺窗口之外保持率不变`() {
        val r = DeadlinePolicy.targetRetention(0.9, deadlineIn(40.0), now, 30)
        assertEquals(0.9, r, 1e-9)
    }

    @Test
    fun `窗口正中点按线性提高`() {
        val r = DeadlinePolicy.targetRetention(0.9, deadlineIn(15.0), now, 30)
        assertEquals(0.935, r, 1e-6)
    }

    @Test
    fun `截止当天达到峰值但不超上限`() {
        val r = DeadlinePolicy.targetRetention(0.9, deadlineIn(0.1), now, 30)
        assertEquals(DeadlinePolicy.PEAK_RETENTION, r, 1e-3)
        assertTrue(r <= 0.97)
    }

    @Test
    fun `已过期的截止日期不惩罚`() {
        val r = DeadlinePolicy.targetRetention(0.9, deadlineIn(-5.0), now, 30)
        assertEquals(0.9, r, 1e-9)
    }

    @Test
    fun `最长间隔被压缩到剩余天数以内`() {
        val config = SrsConfig(maxIntervalDays = 365)
        val capped = DeadlinePolicy.capConfig(config, deadlineIn(10.0), now)
        assertEquals(10, capped.maxIntervalDays)
    }

    @Test
    fun `剩余不足一天时不压缩间隔`() {
        val config = SrsConfig(maxIntervalDays = 365)
        val capped = DeadlinePolicy.capConfig(config, deadlineIn(0.4), now)
        assertEquals(365, capped.maxIntervalDays)
    }

    @Test
    fun `任务截止时间优先于学科考试时间`() {
        val item = MemorizeItem(
            id = 1,
            subjectId = 2,
            topic = "TCP 三次握手",
            content = "…",
            source = null,
            priority = Priority.NORMAL,
            createdAt = now,
            updatedAt = now,
            deadlineAt = deadlineIn(5.0),
        )
        val subject = Subject(id = 2, name = "计算机网络", colorArgb = 0, examAt = deadlineIn(20.0))
        val resolved = RetentionResolver.resolve(UserSettings(), item, subject, now)
        assertEquals(item.deadlineAt, resolved.deadline)
        assertTrue(resolved.targetRetention > 0.9)
    }

    @Test
    fun `没有任务截止日期时回落到学科考试时间`() {
        val item = MemorizeItem(
            id = 1,
            subjectId = 2,
            topic = "TCP",
            content = "…",
            source = null,
            priority = Priority.NORMAL,
            createdAt = now,
            updatedAt = now,
        )
        val subject = Subject(id = 2, name = "计算机网络", colorArgb = 0, examAt = deadlineIn(3.0))
        val resolved = RetentionResolver.resolve(UserSettings(), item, subject, now)
        assertEquals(subject.examAt, resolved.deadline)
    }

    @Test
    fun `反复遗忘判定同时看次数与比例`() {
        assertTrue(LeechPolicy.isLeech(lapses = 4, reps = 10))
        assertTrue(LeechPolicy.isLeech(lapses = 3, reps = 6))
        assertTrue(!LeechPolicy.isLeech(lapses = 2, reps = 5))
        // 阈值可调：设成 8 时，4 次遗忘不再算 leech
        assertTrue(!LeechPolicy.isLeech(lapses = 4, reps = 5, threshold = 8))
    }
}
