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
    /** 启动时是否自动检查更新。只提示新版本，不自动下载、不自动安装。 */
    val autoCheckUpdate: Boolean = true,
    /** 上次检查更新的时间戳（毫秒）。仅用于节流，不出现在界面上。 */
    val lastUpdateCheckAt: Long = 0L,

    // ---------- 复习算法可调参数 ----------
    // 这些值都直接进入 FSRS 排程，改完下一次评分就生效，不需要重建数据。

    /** 最长复习间隔（天）。S 仍会增长，只是产品上不排到更远。 */
    val maxIntervalDays: Int = 365,
    /** 是否对间隔做确定性抖动（同一批内容不再同日到期）。默认关闭。 */
    val intervalFuzzEnabled: Boolean = false,
    /** 连续遗忘多少次后标记为 leech（需要改写或拆分）。 */
    val leechThreshold: Int = 4,
    /** 难度均值回归强度：越大越「健忘」，越容易把难度拉回容易档。 */
    val difficultyReversion: Double = 0.031,
    /** 同一条内容一天最多评几次；超过后「忘了」不再当天重练。 */
    val sameDayRepeatLimit: Int = 3,
    /** 逾期后成功回忆的稳定性奖励上限（0.10 = 最多 +10%）。 */
    val overdueMaxBonus: Double = 0.10,
    /** 考试/截止日冲刺窗口（天）：进入窗口后自动提高目标保持率。 */
    val deadlineRampDays: Int = 30,

    // ---------- 弹性实验（默认关闭） ----------

    /** 实验：按「分钟」而非「条数」计算完成率，并识别连续下滑/恢复期。 */
    val minuteCompletionEnabled: Boolean = false,
    /** 实验：过载时按学科配额与轮换分配复习时间。 */
    val subjectQuotaEnabled: Boolean = false,
    /** 学科最小份额：任何学科都不至于被完全饿死。 */
    val subjectMinShare: Double = 0.2,
    /** 学科最大份额：避免某一科挤占整天。 */
    val subjectMaxShare: Double = 0.6,
    /** 趋势窗口（天）。 */
    val trendWindowDays: Int = 7,

    /** 实验：首页显示「今天状态」三档入口。 */
    val todayStateEnabled: Boolean = false,
    /** 今天的状态（跨天自动失效）。 */
    val todayState: TodayState = TodayState.AUTO,
    /** 该状态对应的日期；null 表示没有设置过。 */
    val todayStateDate: java.time.LocalDate? = null,
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
        const val MIN_MAX_INTERVAL_DAYS = 30
        const val MAX_MAX_INTERVAL_DAYS = 365
        const val MIN_LEECH_THRESHOLD = 3
        const val MAX_LEECH_THRESHOLD = 10
        const val MIN_DIFFICULTY_REVERSION = 0.0
        const val MAX_DIFFICULTY_REVERSION = 0.20
        const val MIN_SAME_DAY_REPEAT = 1
        const val MAX_SAME_DAY_REPEAT = 5
        const val MIN_DEADLINE_RAMP_DAYS = 10
        const val MAX_DEADLINE_RAMP_DAYS = 60
        const val MIN_OVERDUE_BONUS = 0.0
        const val MAX_OVERDUE_BONUS = 0.30
        const val MIN_SUBJECT_SHARE = 0.10
        const val MAX_SUBJECT_SHARE_LIMIT = 0.80
        const val MIN_TREND_WINDOW = 3
        const val MAX_TREND_WINDOW = 14
    }
}
