package com.meowmorize.core.domain.srs

/**
 * 排程器的可调参数。
 *
 * 这些值以后会从设置页读取；在设置项落地前，默认值保持与修正前完全一致的行为，
 * 仅「确定性缺陷」按新语义生效。
 */
data class SrsConfig(
    /** 最长复习间隔（天）。S 仍可增长，但产品上不再把内容排到更远。 */
    val maxIntervalDays: Int = 365,
    /** 是否对间隔做确定性抖动，避免同批内容同日到期。默认关闭。 */
    val fuzzEnabled: Boolean = false,
    /** 难度均值回归强度（原 w7）。 */
    val difficultyReversion: Double = 0.031,
    /** 同一条内容一天内最多评几次；超过后「忘了」不再当天重练。 */
    val sameDayRepeatLimit: Int = 3,
    /** 逾期后成功回忆的稳定性奖励上限（10%）。 */
    val overdueMaxBonus: Double = 0.10,
    /** HARD 的间隔惩罚。 */
    val hardPenalty: Double = 0.72,
    /** EASY 的间隔奖励。 */
    val easyBonus: Double = 1.65,
    /** 同日重练的步进（分钟）。 */
    val learningStepsMinutes: IntArray = intArrayOf(10, 60, 240),
) {
    init {
        require(maxIntervalDays >= 1) { "最大间隔必须为正" }
        require(sameDayRepeatLimit >= 1) { "同日重评上限至少为 1" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SrsConfig) return false
        return maxIntervalDays == other.maxIntervalDays &&
            fuzzEnabled == other.fuzzEnabled &&
            difficultyReversion == other.difficultyReversion &&
            sameDayRepeatLimit == other.sameDayRepeatLimit &&
            overdueMaxBonus == other.overdueMaxBonus &&
            hardPenalty == other.hardPenalty &&
            easyBonus == other.easyBonus &&
            learningStepsMinutes.contentEquals(other.learningStepsMinutes)
    }

    override fun hashCode(): Int {
        var result = maxIntervalDays
        result = 31 * result + fuzzEnabled.hashCode()
        result = 31 * result + difficultyReversion.hashCode()
        result = 31 * result + sameDayRepeatLimit
        result = 31 * result + overdueMaxBonus.hashCode()
        result = 31 * result + hardPenalty.hashCode()
        result = 31 * result + easyBonus.hashCode()
        result = 31 * result + learningStepsMinutes.contentHashCode()
        return result
    }
}
