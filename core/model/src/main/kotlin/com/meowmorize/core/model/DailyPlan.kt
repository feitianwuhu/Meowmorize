package com.meowmorize.core.model

import java.time.Instant
import java.time.LocalDate

/** 今日负荷档位，直接映射到 UI 上的一个标签。 */
enum class LoadLevel {
    /** 轻松：不足容量 60%，适合忙碌日。 */
    RELAXED,

    /** 标准：60%~95%，日常推荐。 */
    STEADY,

    /** 冲刺：接近或达到容量上限，只在状态好时出现。 */
    INTENSE,
    ;

    companion object {
        fun fromName(name: String?): LoadLevel = entries.firstOrNull { it.name == name } ?: STEADY

        fun of(plannedMinutes: Int, capacityMinutes: Int): LoadLevel {
            if (capacityMinutes <= 0) return STEADY
            val ratio = plannedMinutes.toFloat() / capacityMinutes
            return when {
                ratio < 0.6f -> RELAXED
                ratio < 0.95f -> STEADY
                else -> INTENSE
            }
        }
    }
}

/** 任务进入今日计划的原因，决定卡片上的小标签与排序权重。 */
enum class PlanReason(val weight: Int) {
    /** 逾期未复习，优先级最高。 */
    OVERDUE(0),

    /** 今天到期。 */
    DUE_TODAY(1),

    /** 昨天未完成而被顺延。 */
    CARRY_OVER(2),

    /** 新学任务，用来填充剩余容量。 */
    NEW(3),
    ;

    companion object {
        fun fromName(name: String?): PlanReason = entries.firstOrNull { it.name == name } ?: DUE_TODAY
    }
}

/**
 * 今日计划中的一条任务。
 * 这是「任务 + 记忆状态 + 学科」的读模型，UI 可以直接渲染。
 */
data class PlannedTask(
    val item: MemorizeItem,
    val state: ReviewState,
    val subject: Subject?,
    val reason: PlanReason,
    val orderIndex: Int,
    val minutes: Int,
    /** 今日是否已评分完成。 */
    val completed: Boolean = false,
    /** 若今日评分「忘了」，需要当天再练一次。 */
    val needsRepractice: Boolean = false,
)

/**
 * 一天的计划。
 *
 * [notes] 保存算法做出的弹性调整说明，例如「近 3 天完成率偏低，今日容量已下调 25%」。
 * 这些说明会直接展示给用户——减轻负担的前提是让用户理解为什么任务变少了。
 */
data class DailyPlan(
    val date: LocalDate,
    val generatedAt: Instant,
    val tasks: List<PlannedTask>,
    val capacityMinutes: Int,
    val plannedMinutes: Int,
    val loadLevel: LoadLevel,
    val targetRetention: Double,
    val elasticityFactor: Double = 1.0,
    /** 因容量不足被顺延到明天的任务数。 */
    val backlogCount: Int = 0,
    /** 今日新学任务数。 */
    val newCount: Int = 0,
    val notes: List<String> = emptyList(),
    /** 用户是否手动调整过今日任务量。 */
    val userAdjusted: Boolean = false,
) {
    val totalCount: Int get() = tasks.size
    val completedCount: Int get() = tasks.count { it.completed }
    val remainingTasks: List<PlannedTask> get() = tasks.filter { !it.completed }
    val isEmpty: Boolean get() = tasks.isEmpty()

    val progress: DayProgress
        get() = DayProgress(
            plannedCount = tasks.size,
            completedCount = completedCount,
            plannedMinutes = plannedMinutes,
            completedMinutes = tasks.filter { it.completed }.sumOf { it.minutes },
            remainingCount = tasks.count { !it.completed },
        )
}
