package com.meowmorize.core.model

import java.time.Instant

/** 复习日志，只追加不修改。算法参数校准与统计都依赖它。 */
data class ReviewLog(
    val id: Long = 0L,
    val itemId: Long,
    val reviewedAt: Instant,
    val grade: RecallGrade,
    /** 本次复习时的间隔（天）。 */
    val intervalBefore: Int,
    /** 本次复习后的间隔（天）。 */
    val intervalAfter: Int,
    val stabilityAfter: Double,
    val phaseAfter: LearningPhase,
    /** 本次复习对应内容的版本号，用于内容变更后的效果对比。 */
    val itemRevision: Int = 1,
)

/** 一天的学习进度快照。 */
data class DayProgress(
    val plannedCount: Int = 0,
    val completedCount: Int = 0,
    val plannedMinutes: Int = 0,
    val completedMinutes: Int = 0,
    /** 今日尚未完成、仍需复习的任务数（含到期与逾期）。 */
    val remainingCount: Int = 0,
) {
    val ratio: Float
        get() = if (plannedCount == 0) 0f else (completedCount.toFloat() / plannedCount).coerceIn(0f, 1f)

    val isFinished: Boolean get() = plannedCount > 0 && completedCount >= plannedCount
}
