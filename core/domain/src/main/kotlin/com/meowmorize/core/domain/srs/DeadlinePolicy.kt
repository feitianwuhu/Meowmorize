package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.Subject
import com.meowmorize.core.model.UserSettings
import java.time.Duration
import java.time.Instant
import kotlin.math.floor
import kotlin.math.min

/**
 * 考试/截止日期的冲刺策略。
 *
 * 两个动作，都很克制：
 * 1. **提高目标保持率**：越临近截止，越不能忘。但只在冲刺窗口内线性提高，
 *    避免「一设截止日期，复习量突然翻倍」。
 * 2. **压缩最长间隔**：保证截止日前还能再见一次。否则曲线可能把下一次排到考试之后。
 *
 * 已过期的截止日期不做任何惩罚——过期还加码复习，只会制造焦虑。
 */
object DeadlinePolicy {

    /** 冲刺期允许的最高目标保持率。再高会让复习变成每天重刷。 */
    const val PEAK_RETENTION = 0.97

    /** 距离截止多少天开始进入冲刺（低于 1 天视为已到）。 */
    fun daysLeft(deadline: Instant?, now: Instant): Double? {
        deadline ?: return null
        val minutes = Duration.between(now, deadline).toMinutes()
        return minutes / 1440.0
    }

    /**
     * @param base 用户设定的目标保持率
     * @param deadline 截止时间；为空表示无期限
     * @param rampDays 冲刺窗口（天）
     */
    fun targetRetention(
        base: Double,
        deadline: Instant?,
        now: Instant,
        rampDays: Int,
    ): Double {
        val left = daysLeft(deadline, now) ?: return base
        if (left <= 0.0) return base
        val ramp = rampDays.coerceAtLeast(1).toDouble()
        val progress = (1.0 - left / ramp).coerceIn(0.0, 1.0)
        if (progress <= 0.0) return base
        return (base + (PEAK_RETENTION - base) * progress).coerceIn(base, PEAK_RETENTION)
    }

    /**
     * 压缩最长间隔，确保截止日前至少还能再见一次。
     * 剩余不足 1 天时不再压缩——已经来不及通过间隔安排补救了，强压只会把任务堆到今天。
     */
    fun capConfig(config: SrsConfig, deadline: Instant?, now: Instant): SrsConfig {
        val left = daysLeft(deadline, now) ?: return config
        if (left < 1.0) return config
        val cap = floor(left).toInt().coerceAtLeast(1)
        val effective = min(config.maxIntervalDays, cap)
        return if (effective == config.maxIntervalDays) config else config.copy(maxIntervalDays = effective)
    }
}

/**
 * 复习时的「目标保持率 + 排程参数」解析器。
 *
 * 单独抽出来的原因：评分落库（[com.meowmorize.core.domain.usecase.GradeReviewUseCase]）
 * 与复习页按钮上的预览必须用同一套计算，否则会出现「按钮写 4 天、存进去 2 天」。
 */
object RetentionResolver {

    data class Resolved(
        val targetRetention: Double,
        val config: SrsConfig,
        /** 实际生效的截止时间（任务优先，学科兜底）。 */
        val deadline: Instant?,
    )

    fun resolve(
        settings: UserSettings,
        item: MemorizeItem?,
        subject: Subject?,
        now: Instant,
    ): Resolved {
        val deadline = item?.deadlineAt ?: subject?.examAt
        val retention = DeadlinePolicy.targetRetention(
            base = settings.targetRetention,
            deadline = deadline,
            now = now,
            rampDays = settings.deadlineRampDays,
        )
        val config = DeadlinePolicy.capConfig(SrsPolicy.from(settings), deadline, now)
        return Resolved(targetRetention = retention, config = config, deadline = deadline)
    }
}
