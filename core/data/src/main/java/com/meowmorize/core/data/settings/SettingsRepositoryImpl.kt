package com.meowmorize.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.model.AccentChoice
import com.meowmorize.core.model.GlassTone
import com.meowmorize.core.model.WallpaperScale
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.TaskSortOrder
import com.meowmorize.core.model.TodayState
import com.meowmorize.core.model.ThemeMode
import com.meowmorize.core.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import java.time.LocalDate
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meow_settings")

/**
 * 设置用 DataStore 存储（而不是 Room）：键值、低频写、需要按 Flow 订阅。
 * 读取时做一次 Range 校验，避免历史脏数据把算法算出负数容量。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<UserSettings> =
        context.settingsDataStore.data.map { it.toUserSettings() }

    override suspend fun current(): UserSettings =
        context.settingsDataStore.data.first().toUserSettings()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.toUserSettings()).sanitized()
            prefs.write(updated)
        }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            dailyCapacityMinutes = this[Keys.DAILY_CAPACITY] ?: defaults.dailyCapacityMinutes,
            maxNewPerDay = this[Keys.MAX_NEW_PER_DAY] ?: defaults.maxNewPerDay,
            targetRetention = this[Keys.TARGET_RETENTION] ?: defaults.targetRetention,
            adaptiveLoadEnabled = this[Keys.ADAPTIVE_LOAD] ?: defaults.adaptiveLoadEnabled,
            weekendReliefEnabled = this[Keys.WEEKEND_RELIEF] ?: defaults.weekendReliefEnabled,
            calendarSyncEnabled = this[Keys.CALENDAR_SYNC_ENABLED] ?: defaults.calendarSyncEnabled,
            calendarSyncWindowHours = this[Keys.CALENDAR_SYNC_WINDOW] ?: defaults.calendarSyncWindowHours,
            themeMode = ThemeMode.fromName(this[Keys.THEME_MODE]),
            librarySortOrder = TaskSortOrder.fromName(this[Keys.LIBRARY_SORT]),
            appStyle = AppStyle.fromName(this[Keys.APP_STYLE]),
            onboarded = this[Keys.ONBOARDED] ?: defaults.onboarded,
            autoCheckUpdate = this[Keys.AUTO_CHECK_UPDATE] ?: defaults.autoCheckUpdate,
            wallpaperEnabled = this[Keys.WALLPAPER_ENABLED] ?: defaults.wallpaperEnabled,
            wallpaperScale = WallpaperScale.fromName(this[Keys.WALLPAPER_SCALE]),
            wallpaperBlur = this[Keys.WALLPAPER_BLUR] ?: defaults.wallpaperBlur,
            wallpaperScrim = this[Keys.WALLPAPER_SCRIM] ?: defaults.wallpaperScrim,
            glassTone = GlassTone.fromName(this[Keys.GLASS_TONE]),
            accentChoice = AccentChoice.fromName(this[Keys.ACCENT_CHOICE]),
            accentCustomArgb = this[Keys.ACCENT_CUSTOM] ?: defaults.accentCustomArgb,
            lastUpdateCheckAt = this[Keys.LAST_UPDATE_CHECK] ?: defaults.lastUpdateCheckAt,
            maxIntervalDays = this[Keys.MAX_INTERVAL_DAYS] ?: defaults.maxIntervalDays,
            intervalFuzzEnabled = this[Keys.INTERVAL_FUZZ] ?: defaults.intervalFuzzEnabled,
            leechThreshold = this[Keys.LEECH_THRESHOLD] ?: defaults.leechThreshold,
            difficultyReversion = this[Keys.DIFFICULTY_REVERSION] ?: defaults.difficultyReversion,
            sameDayRepeatLimit = this[Keys.SAME_DAY_REPEAT] ?: defaults.sameDayRepeatLimit,
            overdueMaxBonus = this[Keys.OVERDUE_BONUS] ?: defaults.overdueMaxBonus,
            deadlineRampDays = this[Keys.DEADLINE_RAMP_DAYS] ?: defaults.deadlineRampDays,
            minuteCompletionEnabled = this[Keys.MINUTE_COMPLETION] ?: defaults.minuteCompletionEnabled,
            subjectQuotaEnabled = this[Keys.SUBJECT_QUOTA] ?: defaults.subjectQuotaEnabled,
            subjectMinShare = this[Keys.SUBJECT_MIN_SHARE] ?: defaults.subjectMinShare,
            subjectMaxShare = this[Keys.SUBJECT_MAX_SHARE] ?: defaults.subjectMaxShare,
            trendWindowDays = this[Keys.TREND_WINDOW] ?: defaults.trendWindowDays,
            todayStateEnabled = this[Keys.TODAY_STATE_ENABLED] ?: defaults.todayStateEnabled,
            todayState = TodayState.fromName(this[Keys.TODAY_STATE]),
            todayStateDate = this[Keys.TODAY_STATE_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        ).sanitized()
    }

    private fun MutablePreferences.write(settings: UserSettings) {
        this[Keys.DAILY_CAPACITY] = settings.dailyCapacityMinutes
        this[Keys.MAX_NEW_PER_DAY] = settings.maxNewPerDay
        this[Keys.TARGET_RETENTION] = settings.targetRetention
        this[Keys.ADAPTIVE_LOAD] = settings.adaptiveLoadEnabled
        this[Keys.WEEKEND_RELIEF] = settings.weekendReliefEnabled
        this[Keys.CALENDAR_SYNC_ENABLED] = settings.calendarSyncEnabled
        this[Keys.CALENDAR_SYNC_WINDOW] = settings.calendarSyncWindowHours
        this[Keys.THEME_MODE] = settings.themeMode.name
        this[Keys.LIBRARY_SORT] = settings.librarySortOrder.name
        this[Keys.APP_STYLE] = settings.appStyle.name
        this[Keys.ONBOARDED] = settings.onboarded
        this[Keys.AUTO_CHECK_UPDATE] = settings.autoCheckUpdate
        this[Keys.WALLPAPER_ENABLED] = settings.wallpaperEnabled
        this[Keys.WALLPAPER_SCALE] = settings.wallpaperScale.name
        this[Keys.WALLPAPER_BLUR] = settings.wallpaperBlur
        this[Keys.WALLPAPER_SCRIM] = settings.wallpaperScrim
        this[Keys.GLASS_TONE] = settings.glassTone.name
        this[Keys.ACCENT_CHOICE] = settings.accentChoice.name
        this[Keys.ACCENT_CUSTOM] = settings.accentCustomArgb
        this[Keys.LAST_UPDATE_CHECK] = settings.lastUpdateCheckAt
        this[Keys.MAX_INTERVAL_DAYS] = settings.maxIntervalDays
        this[Keys.INTERVAL_FUZZ] = settings.intervalFuzzEnabled
        this[Keys.LEECH_THRESHOLD] = settings.leechThreshold
        this[Keys.DIFFICULTY_REVERSION] = settings.difficultyReversion
        this[Keys.SAME_DAY_REPEAT] = settings.sameDayRepeatLimit
        this[Keys.OVERDUE_BONUS] = settings.overdueMaxBonus
        this[Keys.DEADLINE_RAMP_DAYS] = settings.deadlineRampDays
        this[Keys.MINUTE_COMPLETION] = settings.minuteCompletionEnabled
        this[Keys.SUBJECT_QUOTA] = settings.subjectQuotaEnabled
        this[Keys.SUBJECT_MIN_SHARE] = settings.subjectMinShare
        this[Keys.SUBJECT_MAX_SHARE] = settings.subjectMaxShare
        this[Keys.TREND_WINDOW] = settings.trendWindowDays
        this[Keys.TODAY_STATE_ENABLED] = settings.todayStateEnabled
        this[Keys.TODAY_STATE] = settings.todayState.name
        settings.todayStateDate?.let { this[Keys.TODAY_STATE_DATE] = it.toString() }
            ?: remove(Keys.TODAY_STATE_DATE)
    }

    private fun UserSettings.sanitized(): UserSettings = copy(
        dailyCapacityMinutes = dailyCapacityMinutes.coerceIn(
            UserSettings.MIN_CAPACITY_MINUTES,
            UserSettings.MAX_CAPACITY_MINUTES,
        ),
        maxNewPerDay = maxNewPerDay.coerceIn(UserSettings.MIN_NEW_PER_DAY, UserSettings.MAX_NEW_PER_DAY),
        targetRetention = targetRetention.coerceIn(UserSettings.MIN_RETENTION, UserSettings.MAX_RETENTION),
        calendarSyncWindowHours = calendarSyncWindowHours.coerceIn(
            UserSettings.MIN_SYNC_WINDOW_HOURS,
            UserSettings.MAX_SYNC_WINDOW_HOURS,
        ),
        maxIntervalDays = maxIntervalDays.coerceIn(
            UserSettings.MIN_MAX_INTERVAL_DAYS,
            UserSettings.MAX_MAX_INTERVAL_DAYS,
        ),
        leechThreshold = leechThreshold.coerceIn(
            UserSettings.MIN_LEECH_THRESHOLD,
            UserSettings.MAX_LEECH_THRESHOLD,
        ),
        difficultyReversion = difficultyReversion.coerceIn(
            UserSettings.MIN_DIFFICULTY_REVERSION,
            UserSettings.MAX_DIFFICULTY_REVERSION,
        ),
        sameDayRepeatLimit = sameDayRepeatLimit.coerceIn(
            UserSettings.MIN_SAME_DAY_REPEAT,
            UserSettings.MAX_SAME_DAY_REPEAT,
        ),
        overdueMaxBonus = overdueMaxBonus.coerceIn(
            UserSettings.MIN_OVERDUE_BONUS,
            UserSettings.MAX_OVERDUE_BONUS,
        ),
        deadlineRampDays = deadlineRampDays.coerceIn(
            UserSettings.MIN_DEADLINE_RAMP_DAYS,
            UserSettings.MAX_DEADLINE_RAMP_DAYS,
        ),
        subjectMinShare = subjectMinShare.coerceIn(
            UserSettings.MIN_SUBJECT_SHARE,
            UserSettings.MAX_SUBJECT_SHARE_LIMIT,
        ).let { min ->
            // 下限不能大于上限，否则配额算法会互相打架
            min.coerceAtMost(subjectMaxShare)
        },
        subjectMaxShare = subjectMaxShare.coerceIn(
            UserSettings.MIN_SUBJECT_SHARE,
            UserSettings.MAX_SUBJECT_SHARE_LIMIT,
        ),
        trendWindowDays = trendWindowDays.coerceIn(
            UserSettings.MIN_TREND_WINDOW,
            UserSettings.MAX_TREND_WINDOW,
        ),
        wallpaperBlur = wallpaperBlur.coerceIn(MIN_WALLPAPER_BLUR, MAX_WALLPAPER_BLUR),
        wallpaperScrim = wallpaperScrim.coerceIn(0.0, MAX_WALLPAPER_SCRIM),
    )

    private companion object {
        const val MIN_WALLPAPER_BLUR = 0
        const val MAX_WALLPAPER_BLUR = 48
        const val MAX_WALLPAPER_SCRIM = 0.7
    }

    private object Keys {
        val DAILY_CAPACITY = intPreferencesKey("daily_capacity_minutes")
        val MAX_NEW_PER_DAY = intPreferencesKey("max_new_per_day")
        val TARGET_RETENTION = doublePreferencesKey("target_retention")
        val ADAPTIVE_LOAD = booleanPreferencesKey("adaptive_load_enabled")
        val WEEKEND_RELIEF = booleanPreferencesKey("weekend_relief_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIBRARY_SORT = stringPreferencesKey("library_sort_order")
        val APP_STYLE = stringPreferencesKey("app_style")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
        val WALLPAPER_ENABLED = booleanPreferencesKey("wallpaper_enabled")
        val WALLPAPER_SCALE = stringPreferencesKey("wallpaper_scale")
        val WALLPAPER_BLUR = intPreferencesKey("wallpaper_blur")
        val WALLPAPER_SCRIM = doublePreferencesKey("wallpaper_scrim")
        val GLASS_TONE = stringPreferencesKey("glass_tone")
        val ACCENT_CHOICE = stringPreferencesKey("accent_choice")
        val ACCENT_CUSTOM = longPreferencesKey("accent_custom_argb")
        val CALENDAR_SYNC_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
        val CALENDAR_SYNC_WINDOW = intPreferencesKey("calendar_sync_window_hours")
        val MAX_INTERVAL_DAYS = intPreferencesKey("srs_max_interval_days")
        val INTERVAL_FUZZ = booleanPreferencesKey("srs_interval_fuzz")
        val LEECH_THRESHOLD = intPreferencesKey("srs_leech_threshold")
        val DIFFICULTY_REVERSION = doublePreferencesKey("srs_difficulty_reversion")
        val SAME_DAY_REPEAT = intPreferencesKey("srs_same_day_repeat_limit")
        val OVERDUE_BONUS = doublePreferencesKey("srs_overdue_bonus")
        val DEADLINE_RAMP_DAYS = intPreferencesKey("srs_deadline_ramp_days")
        val MINUTE_COMPLETION = booleanPreferencesKey("minute_completion_enabled")
        val SUBJECT_QUOTA = booleanPreferencesKey("subject_quota_enabled")
        val SUBJECT_MIN_SHARE = doublePreferencesKey("subject_min_share")
        val SUBJECT_MAX_SHARE = doublePreferencesKey("subject_max_share")
        val TREND_WINDOW = intPreferencesKey("trend_window_days")
        val TODAY_STATE_ENABLED = booleanPreferencesKey("today_state_enabled")
        val TODAY_STATE = stringPreferencesKey("today_state")
        val TODAY_STATE_DATE = stringPreferencesKey("today_state_date")
    }
}
