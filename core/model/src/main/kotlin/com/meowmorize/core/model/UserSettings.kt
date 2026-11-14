package com.meowmorize.core.model

/** 主题模式。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * 用户偏好设置。
 *
 * 这些参数刻意保持「少而可解释」：每一项都直接影响今日任务量，
 * 用户改完能立刻看到计划变化，而不是面对一堆玄学开关。
 */
data class UserSettings(
    /** 每日可投入的背诵时间（分钟），是弹性排程的基准容量。 */
    val dailyCapacityMinutes: Int = 30,
    /** 每日最多引入的新任务数，避免「新任务挤掉复习」。 */
    val maxNewPerDay: Int = 5,
    /** 目标保持率：0.9 表示希望每次复习时仍有 90% 的回忆概率。越高越累。 */
    val targetRetention: Double = 0.9,
    /** 是否允许算法根据近期完成率自动增减任务量。 */
    val adaptiveLoadEnabled: Boolean = true,
    /** 周末是否自动放宽（容量打 85 折）。 */
    val weekendReliefEnabled: Boolean = true,
    /** 是否把即将到期的任务同步到系统日历（默认关闭，需要用户授权）。 */
    val calendarSyncEnabled: Boolean = false,
    /** 同步窗口（小时）：只同步这段时间内要做的任务。 */
    val calendarSyncWindowHours: Int = 24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 界面风格：HyperOS 圆润风 / 终端仪表盘风。 */
    val appStyle: AppStyle = AppStyle.SOFT_CARD,
    /** 任务库排序偏好，跨重启保留。 */
    val librarySortOrder: TaskSortOrder = TaskSortOrder.RECENT_UPDATED,
    /** 是否已完成新手引导（用于首次进入时插入示例数据说明）。 */
    val onboarded: Boolean = false,
) {
    companion object {
        const val MIN_CAPACITY_MINUTES = 5
        const val MAX_CAPACITY_MINUTES = 180
        const val MIN_NEW_PER_DAY = 0
        const val MAX_NEW_PER_DAY = 20
        const val MIN_RETENTION = 0.80
        const val MAX_RETENTION = 0.97
        const val MIN_SYNC_WINDOW_HOURS = 1
        const val MAX_SYNC_WINDOW_HOURS = 24 * 30
    }
}
