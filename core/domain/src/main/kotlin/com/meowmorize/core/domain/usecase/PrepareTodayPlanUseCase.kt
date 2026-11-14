package com.meowmorize.core.domain.usecase

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.plan.CompletionTrend
import com.meowmorize.core.domain.plan.LoadPreference
import com.meowmorize.core.domain.plan.PlanRequest
import com.meowmorize.core.domain.plan.TodayPlanGenerator
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.model.DailyPlan
import com.meowmorize.core.model.activeTodayStateOn

/**
 * 打开应用时的第一步：保证「今天有一份计划」。
 *
 * 规则：
 * - 当天已有计划且未要求重算 → 直接复用，避免每次切前台都把顺序洗一遍。
 * - 否则拉取候选、读取用户近况与设置，生成并落库。
 */
class PrepareTodayPlanUseCase(
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val studyRepository: StudyRepository,
    private val generator: TodayPlanGenerator,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        force: Boolean = false,
        forcedCapacityMinutes: Int? = null,
        loadPreference: LoadPreference = LoadPreference.AUTO,
    ): DailyPlan {
        val today = timeProvider.today()
        if (!force) {
            planRepository.getPlan(today)?.let { return it }
        }

        val now = timeProvider.now()
        val settings = settingsRepository.current()
        val inputs = planRepository.loadPlanInputs(now, timeProvider.endOfToday())

        // 「今天状态」只对当天有效：跨天后自动失效，不需要用户手动清空
        val todayState = settings.activeTodayStateOn(today)
        val trend = if (settings.minuteCompletionEnabled) {
            CompletionTrend.analyze(
                days = planRepository.recentDailyCompletion(today, settings.trendWindowDays),
                windowDays = settings.trendWindowDays,
            )
        } else {
            null
        }

        val draft = generator.generate(
            PlanRequest(
                date = today,
                dueCandidates = inputs.due,
                newCandidates = inputs.newItems,
                settings = settings,
                recentCompletionRatios = planRepository.recentCompletionRatios(today),
                isWeekend = timeProvider.isWeekend(today),
                forcedCapacityMinutes = forcedCapacityMinutes,
                loadPreference = loadPreference,
                todayState = todayState,
                completionTrend = trend,
                subjectWeights = studyRepository.subjects().associate { it.id to it.weight },
            ),
        )
        return planRepository.savePlan(
            date = today,
            draft = draft,
            now = now,
            userAdjusted = forcedCapacityMinutes != null || loadPreference != LoadPreference.AUTO,
        )
    }
}
