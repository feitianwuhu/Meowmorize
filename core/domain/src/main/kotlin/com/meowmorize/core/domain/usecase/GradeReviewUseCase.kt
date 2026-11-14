package com.meowmorize.core.domain.usecase

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.srs.RetentionResolver
import com.meowmorize.core.domain.srs.SrsPolicy
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewOutcome

/** 评分并推进排程。 */
class GradeReviewUseCase(
    private val planRepository: PlanRepository,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val syncCalendar: SyncUpcomingToCalendarUseCase,
) {
    /**
     * @param hintUsed 本次是否用过提示。用过就记进日志——后续若做参数校准，
     *   这类评分必须降权，否则会把「提示帮了忙」误当成「内容变简单了」。
     */
    suspend operator fun invoke(
        itemId: Long,
        grade: RecallGrade,
        hintUsed: Boolean = false,
    ): ReviewOutcome {
        val settings = settingsRepository.current()
        val now = timeProvider.now()
        val item = studyRepository.getItem(itemId)
        val subject = item?.subjectId?.let { id -> studyRepository.subjects().firstOrNull { it.id == id } }
        // 截止日期会抬高目标保持率并压缩间隔，必须与复习页预览使用同一份解析
        val resolved = RetentionResolver.resolve(settings = settings, item = item, subject = subject, now = now)
        val outcome = planRepository.recordReview(
            itemId = itemId,
            grade = grade,
            now = now,
            targetRetention = resolved.targetRetention,
            config = resolved.config,
            hintUsed = hintUsed,
            leechThreshold = settings.leechThreshold,
        )
        // 背完后该任务已移出「未来 24 小时」，顺手把日历里的对应事件撤掉
        runCatching { syncCalendar() }
        return outcome
    }
}
