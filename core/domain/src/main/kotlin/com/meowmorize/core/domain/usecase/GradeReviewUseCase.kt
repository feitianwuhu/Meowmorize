package com.meowmorize.core.domain.usecase

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewOutcome

/** 评分并推进排程。 */
class GradeReviewUseCase(
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val syncCalendar: SyncUpcomingToCalendarUseCase,
) {
    suspend operator fun invoke(itemId: Long, grade: RecallGrade): ReviewOutcome {
        val settings = settingsRepository.current()
        val outcome = planRepository.recordReview(
            itemId = itemId,
            grade = grade,
            now = timeProvider.now(),
            targetRetention = settings.targetRetention,
        )
        // 背完后该任务已移出「未来 24 小时」，顺手把日历里的对应事件撤掉
        runCatching { syncCalendar() }
        return outcome
    }
}
