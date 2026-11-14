package com.meowmorize.core.domain.plan

import com.meowmorize.core.model.TodayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElasticityTest {

    private fun input(
        load: LoadPreference = LoadPreference.AUTO,
        state: TodayState = TodayState.AUTO,
        ratios: List<Float> = emptyList(),
        trend: CompletionTrend.Result? = null,
        minuteMode: Boolean = false,
        weekend: Boolean = false,
        dueMinutes: Int = 100,
        base: Int = 100,
    ) = ElasticityPolicy.Input(
        loadPreference = load,
        todayState = state,
        completionRatios = ratios,
        trend = trend,
        minuteCompletionEnabled = minuteMode,
        isWeekend = weekend,
        dueMinutes = dueMinutes,
        baseCapacityMinutes = base,
        dueCandidateCount = 2,
    )

    @Test
    fun `默认输入不改变容量`() {
        val result = ElasticityPolicy.compute(input())
        assertEquals(1.0, result.factor, 1e-9)
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun `累与周末叠加`() {
        val result = ElasticityPolicy.compute(input(state = TodayState.TIRED, weekend = true))
        assertEquals(0.80 * 0.85, result.factor, 1e-9)
        assertTrue(result.notes.any { it.contains("累") })
        assertTrue(result.notes.any { it.contains("周末") })
    }

    @Test
    fun `多个减负因子叠加后有下限`() {
        val result = ElasticityPolicy.compute(
            input(load = LoadPreference.RELAXED, state = TodayState.TIRED, weekend = true),
        )
        // 0.7 × 0.8 × 0.85 = 0.476，必须夹到 0.60
        assertEquals(0.60, result.factor, 1e-9)
    }

    @Test
    fun `积压超过两天容量时最后压到九折`() {
        val result = ElasticityPolicy.compute(
            input(state = TodayState.ENERGETIC, dueMinutes = 300, base = 100),
        )
        assertEquals(0.90, result.factor, 1e-9)
    }

    @Test
    fun `显式负荷偏好时不叠加完成率判断`() {
        val result = ElasticityPolicy.compute(
            input(load = LoadPreference.RELAXED, ratios = listOf(0.2f, 0.3f)),
        )
        assertEquals(0.7, result.factor, 1e-9)
    }

    @Test
    fun `分钟模式下降滑趋势减一成`() {
        val trend = CompletionTrend.analyze(
            days = listOf(0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f).map {
                DayCompletion(plannedMinutes = 100, completedMinutes = (it * 100).toInt())
            },
            windowDays = 7,
        )
        val result = ElasticityPolicy.compute(input(trend = trend, minuteMode = true))
        assertEquals(0.90, result.factor, 1e-9)
    }

    @Test
    fun `分钟模式样本不足时不调整并说明`() {
        val trend = CompletionTrend.analyze(
            days = listOf(DayCompletion(100, 20), DayCompletion(100, 20)),
            windowDays = 7,
        )
        val result = ElasticityPolicy.compute(input(trend = trend, minuteMode = true))
        assertEquals(1.0, result.factor, 1e-9)
        assertTrue(result.notes.any { it.contains("样本还不足") })
    }

    @Test
    fun `趋势分析能识别下滑与恢复`() {
        val declining = CompletionTrend.analyze(
            days = listOf(0.95f, 0.9f, 0.85f, 0.7f, 0.6f, 0.5f).map { DayCompletion(100, (it * 100).toInt()) },
            windowDays = 6,
        )
        assertEquals(CompletionTrend.State.DECLINING, declining.state)
        assertTrue(declining.trusted)

        val recovering = CompletionTrend.analyze(
            days = listOf(0.4f, 0.4f, 0.4f, 0.85f, 0.9f, 0.9f).map { DayCompletion(100, (it * 100).toInt()) },
            windowDays = 6,
        )
        assertEquals(CompletionTrend.State.RECOVERING, recovering.state)
        assertEquals(1.05, CompletionTrend.factor(recovering), 1e-9)
    }

    @Test
    fun `覆盖率过低时不算可信`() {
        val result = CompletionTrend.analyze(
            days = listOf(DayCompletion(100, 100), DayCompletion(100, 100), DayCompletion(100, 100)),
            windowDays = 14,
        )
        assertFalse(result.trusted)
        assertEquals(1.0, CompletionTrend.factor(result), 1e-9)
    }

    @Test
    fun `学科配额在过载时生效且不超发`() {
        val candidates = listOf(
            SubjectQuotaAllocator.Candidate(1, 10L, 40),
            SubjectQuotaAllocator.Candidate(2, 10L, 40),
            SubjectQuotaAllocator.Candidate(3, 20L, 30),
            SubjectQuotaAllocator.Candidate(4, 20L, 30),
        )
        val result = SubjectQuotaAllocator.allocate(
            candidates = candidates,
            capacityMinutes = 60,
            minShare = 0.2,
            maxShare = 0.6,
        )
        assertTrue(result.applied)
        assertTrue(result.budgets.values.sum() <= 60)
        // 两个学科都有份额，没人被饿死
        assertTrue((result.budgets[10L] ?: 0) > 0)
        assertTrue((result.budgets[20L] ?: 0) > 0)
        // 单科不超过 60%
        assertTrue((result.budgets[10L] ?: 0) <= 36)
    }

    @Test
    fun `需求不超过容量时不介入配额`() {
        val result = SubjectQuotaAllocator.allocate(
            candidates = listOf(
                SubjectQuotaAllocator.Candidate(1, 10L, 10),
                SubjectQuotaAllocator.Candidate(2, 20L, 10),
            ),
            capacityMinutes = 100,
        )
        assertFalse(result.applied)
    }
}
