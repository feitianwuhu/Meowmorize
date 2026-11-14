package com.meowmorize.core.model

/**
 * 「今天状态」：用户对自己当下精力的一次性描述。
 *
 * 与 [LoadPreference] 的区别：LoadPreference 是「我想怎么安排」，长期有效；
 * TodayState 是「我今天怎么样」，只影响当天，跨天自动失效。
 * 两者相乘，因为「今天累」和「想轻松点」是两件事。
 */
enum class TodayState(val capacityFactor: Double) {
    /** 没表态，完全交给算法。 */
    AUTO(1.0),

    /** 累：容量打八折。 */
    TIRED(0.80),

    /** 正常：不调整。 */
    NORMAL(1.0),

    /** 状态好：容量上浮 12%。 */
    ENERGETIC(1.12),
    ;

    companion object {
        fun fromName(name: String?): TodayState = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * 取「某天生效的状态」：设置过一次只对当天有效，跨天自动回到 [TodayState.AUTO]。
 * 抽成扩展而不是让每个调用方各写一遍判断，避免某处忘了判日期。
 */
fun UserSettings.activeTodayStateOn(date: java.time.LocalDate): TodayState =
    if (todayStateDate == date) todayState else TodayState.AUTO
