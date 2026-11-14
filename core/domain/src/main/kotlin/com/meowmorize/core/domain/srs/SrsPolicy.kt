package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.UserSettings

/**
 * 把「用户设置」翻译成「排程参数」的唯一点。
 *
 * 之所以单独抽一个映射，而不是让 [SrsConfig] 直接依赖设置模型：
 * - 领域算法保持对设置结构无知，单测可以只构造参数；
 * - 设置项增减时只改这一处，不会散落到排程器与界面；
 * - 复习页预览与实际落库共用同一个映射，避免「按钮显示 4 天、存进去 6 天」。
 */
object SrsPolicy {

    fun from(settings: UserSettings): SrsConfig = SrsConfig(
        maxIntervalDays = settings.maxIntervalDays,
        fuzzEnabled = settings.intervalFuzzEnabled,
        difficultyReversion = settings.difficultyReversion,
        sameDayRepeatLimit = settings.sameDayRepeatLimit,
        overdueMaxBonus = settings.overdueMaxBonus,
    )
}
