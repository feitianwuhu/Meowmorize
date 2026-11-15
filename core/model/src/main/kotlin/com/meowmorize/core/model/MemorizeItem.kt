package com.meowmorize.core.model

import java.time.Instant

/** 任务优先级。算法在容量不足时优先保留高优先级任务。 */
enum class Priority(val weight: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    ;

    companion object {
        fun fromName(name: String?): Priority = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** 任务生命周期状态。归档后不再进入每日计划，但历史记录保留。 */
enum class ItemStatus {
    ACTIVE,
    ARCHIVED,
    ;

    companion object {
        fun fromName(name: String?): ItemStatus = entries.firstOrNull { it.name == name } ?: ACTIVE
    }
}

/**
 * 背诵任务（用户视角的「一条要背的东西」）。
 *
 * 注意：这里只保存**内容与属性**，不保存复习进度。
 * 复习进度由 [ReviewState] 承载，两者分离后「编辑内容但不改动排程」才可自然实现。
 */
data class MemorizeItem(
    val id: Long = 0L,
    val subjectId: Long? = null,
    /** 背诵主题，例如「Unit 3 核心词汇」。 */
    val topic: String,
    /** 背诵内容正文，用于自测时遮罩显示。 */
    val content: String,
    /** 来源，例如「考研词汇绿宝书 P32」。可为空。 */
    val source: String? = null,
    val priority: Priority = Priority.NORMAL,
    val status: ItemStatus = ItemStatus.ACTIVE,
    /**
     * 单次复习预计耗时（分钟）。
     * 用户不填写时由 [com.meowmorize.core.domain.text.DurationEstimator] 依据内容长度估算。
     */
    val estimatedMinutes: Int = 5,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    /** 内容版本号，每次编辑自增；用于在日志里区分「背的是哪一版内容」。 */
    val revision: Int = 1,
    /**
     * 时长是否为用户手动指定。
     *
     * 为 false 时，内容变化会重新自动估算；为 true 时尊重用户填写的数字——
     * 用户对自己的记忆速度往往比字数公式更准，算法不该覆盖它。
     */
    val durationIsCustom: Boolean = false,
    /**
     * 背诵提示：用户自己写的一句「想起来的关键线索」。
     * 复习页点「显示提示」才会出现，属于「降低难度」的操作，因此会隐藏「轻松」档。
     */
    val hint: String? = null,
    /** 内容格式（纯文本 / Markdown）。 */
    val contentFormat: ContentFormat = ContentFormat.PLAIN,
    /**
     * 背诵截止时间（考试/汇报）。
     * 为空表示没有期限，算法完全按记忆曲线排；有值时进入冲刺窗口会提高保持率，
     * 并保证「考前至少还能再见一次」。
     */
    val deadlineAt: Instant? = null,
    /**
     * 填空题答案。非 null 表示这条内容按「题干 + 答案」处理：
     * [content] 是题干（例如「第二次世界大战的特点为 ______」），
     * 复习时题干直接可见，答案要点「显示答案」才出现。
     */
    val clozeAnswer: String? = null,
) {
    val isActive: Boolean get() = status == ItemStatus.ACTIVE

    /** 列表中展示的副标题：来源优先，其次内容摘要。 */
    val subtitle: String
        get() = source?.takeIf { it.isNotBlank() }
            ?: content.lineSequence().firstOrNull { it.isNotBlank() }?.take(40)
            ?: "暂无内容"
}

/** 新建任务时的草稿对象，避免 UI 直接构造带 id / 时间戳的实体。 */
data class ItemDraft(
    val subjectId: Long?,
    val topic: String,
    val content: String,
    val source: String?,
    val priority: Priority,
    val estimatedMinutes: Int? = null,
    /** 用户是否手动填写了时长（true 时不再用内容长度覆盖）。 */
    val durationIsCustom: Boolean = false,
    /**
     * 背诵提示：用户自己写的一句「想起来的关键线索」。
     * 复习页点「显示提示」才会出现，属于「降低难度」的操作，因此会隐藏「轻松」档。
     */
    val hint: String? = null,
    /** 内容格式（纯文本 / Markdown）。 */
    val contentFormat: ContentFormat = ContentFormat.PLAIN,
    /** 背诵截止时间（考试/汇报），为空表示没有期限。 */
    val deadlineAt: Instant? = null,
    /** 填空题答案；null 表示普通内容。 */
    val clozeAnswer: String? = null,
)

/** 编辑任务时，用户对「是否更新排程」的选择。 */
enum class ScheduleUpdatePolicy {
    /** 仅改内容与属性，复习进度（即将到来的复习时间）保持不变。 */
    KEEP_SCHEDULE,

    /** 内容变化较大，按「重新学习」处理：重置记忆状态，从今天重新排程。 */
    RESET_SCHEDULE,
}
