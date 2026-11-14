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

    // ---------- 以下字段为「可回溯」而存：不参与当前计算，但缺了就无法验证算法 ----------

    /** 距上次复习的真实天数（10 分钟 = 0.0069）。旧版记的是排程间隔，语义不同。 */
    val elapsedDays: Double = 0.0,
    /** 本次复习前，系统认为的到期时间。用于区分「准时」与「逾期」。 */
    val scheduledDueAt: Instant? = null,
    val stabilityBefore: Double = 0.0,
    val difficultyBefore: Double = 0.0,
    val difficultyAfter: Double = 0.0,
    /** 评分时的回忆概率：曲线是否可信，全看这个值。 */
    val retrievability: Double = 0.0,
    /** 本次是否用过提示（用过则评分会偏乐观，校准时需要剔除或降权）。 */
    val hintUsed: Boolean = false,
    /** 是否逾期复习（超过到期时间 1 天以上）。 */
    val wasOverdue: Boolean = false,
    /** 实际背诵耗时（毫秒），-1 表示未记录。 */
    val actualMs: Long = -1,
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
