package com.meowmorize.core.model

import java.time.Instant

/** 记忆阶段，语义对齐 FSRS / Anki 的卡片状态机。 */
enum class LearningPhase {
    /** 从未复习过。 */
    NEW,

    /** 首次学习后的短期巩固阶段。 */
    LEARNING,

    /** 正常复习阶段。 */
    REVIEW,

    /** 遗忘后重新学习，稳定性大幅下降。 */
    RELEARNING,
    ;

    companion object {
        fun fromName(name: String?): LearningPhase = entries.firstOrNull { it.name == name } ?: NEW
    }
}

/** 自评等级。三档太粗、五档太累，四档是背诵场景的舒适区。 */
enum class RecallGrade(val score: Int) {
    /** 忘了。 */
    AGAIN(1),

    /** 有印象但说不全。 */
    HARD(2),

    /** 基本记得。 */
    GOOD(3),

    /** 很轻松。 */
    EASY(4),
}

/**
 * 一条任务当前的记忆状态，即 FSRS 模型中的「记忆卡片」。
 *
 * - [stability]：记忆稳定性（天），可理解为「回忆概率衰减到 90% 所需的天数」。
 * - [difficulty]：内容难度，1~10，越大越难。
 * - [dueAt]：下次复习时间点。
 * - [scheduledDays]：上次排程给出的间隔天数，用于展示「已复习 N 次 / 间隔 N 天」。
 */
data class ReviewState(
    val itemId: Long,
    val dueAt: Instant,
    val stability: Double,
    val difficulty: Double,
    val reps: Int = 0,
    val lapses: Int = 0,
    val phase: LearningPhase = LearningPhase.NEW,
    val lastReviewedAt: Instant? = null,
    val scheduledDays: Int = 0,
) {
    /** 是否尚未开始学习。 */
    val isNew: Boolean get() = phase == LearningPhase.NEW

    /** 已复习次数（不含本次）。 */
    val reviewCount: Int get() = reps
}

/** 一次评分后的排程结果。 */
data class ReviewOutcome(
    val state: ReviewState,
    /** 距离下次复习的天数；0 表示当天还需重练。 */
    val nextIntervalDays: Int,
    /** 评分时刻的可回忆概率（0~1），用于向用户解释「为什么今天要复习」。 */
    val retrievability: Double,
    /** 评分前的稳定性，便于做进度可视化。 */
    val stabilityBefore: Double,
)
