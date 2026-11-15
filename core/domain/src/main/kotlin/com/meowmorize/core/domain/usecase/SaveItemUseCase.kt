package com.meowmorize.core.domain.usecase

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.model.ItemDraft
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.ScheduleUpdatePolicy

/** 新建任务，并把新任务纳入今天的计划（若还有余量）。 */
class SaveItemUseCase(
    private val studyRepository: StudyRepository,
    private val planRepository: PlanRepository,
    private val prepareTodayPlan: PrepareTodayPlanUseCase,
    private val timeProvider: TimeProvider,
    private val syncCalendar: SyncUpcomingToCalendarUseCase,
) {
    suspend fun create(draft: ItemDraft): Long {
        val id = studyRepository.createItem(draft)
        refreshTodayPlan()
        return id
    }

    suspend fun update(item: MemorizeItem, policy: ScheduleUpdatePolicy) {
        studyRepository.updateItem(item, policy)
        if (policy == ScheduleUpdatePolicy.RESET_SCHEDULE) {
            refreshTodayPlan()
        } else {
            syncCalendarQuietly()
        }
    }

    /** 彻底删除：内容、记忆状态与日志一起没了，因此 UI 必须二次确认。 */
    suspend fun delete(itemId: Long) {
        studyRepository.deleteItem(itemId)
        refreshTodayPlan()
    }

    /** 删除学科，任务变成「未分类」而不是被删掉。 */
    suspend fun deleteSubject(subjectId: Long) {
        studyRepository.deleteSubject(subjectId)
        refreshTodayPlan()
    }

    suspend fun archive(itemId: Long) {
        studyRepository.archiveItem(itemId)
        refreshTodayPlan()
    }

    suspend fun restore(itemId: Long) {
        studyRepository.restoreItem(itemId)
        refreshTodayPlan()
    }

    /**
     * 重排今日计划时沿用当前容量，避免用户刚拖好的任务量被设置里的默认值覆盖。
     * 已完成的条目不受影响——完成状态来自复习日志，而不是计划表。
     */
    private suspend fun refreshTodayPlan() {
        val today = timeProvider.today()
        val capacity = planRepository.getPlan(today)?.capacityMinutes
        prepareTodayPlan(force = true, forcedCapacityMinutes = capacity)
        syncCalendarQuietly()
    }

    /** 日历同步失败不能影响「保存任务」这件正事，因此吞掉异常。 */
    private suspend fun syncCalendarQuietly() {
        runCatching { syncCalendar() }
    }
}
