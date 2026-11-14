package com.meowmorize.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.TaskSortOrder
import com.meowmorize.core.model.ThemeMode
import com.meowmorize.core.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
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
            maxIntervalDays = this[Keys.MAX_INTERVAL_DAYS] ?: defaults.maxIntervalDays,
            intervalFuzzEnabled = this[Keys.INTERVAL_FUZZ] ?: defaults.intervalFuzzEnabled,
            leechThreshold = this[Keys.LEECH_THRESHOLD] ?: defaults.leechThreshold,
            difficultyReversion = this[Keys.DIFFICULTY_REVERSION] ?: defaults.difficultyReversion,
            sameDayRepeatLimit = this[Keys.SAME_DAY_REPEAT] ?: defaults.sameDayRepeatLimit,
            overdueMaxBonus = this[Keys.OVERDUE_BONUS] ?: defaults.overdueMaxBonus,
            deadlineRampDays = this[Keys.DEADLINE_RAMP_DAYS] ?: defaults.deadlineRampDays,
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
        this[Keys.MAX_INTERVAL_DAYS] = settings.maxIntervalDays
        this[Keys.INTERVAL_FUZZ] = settings.intervalFuzzEnabled
        this[Keys.LEECH_THRESHOLD] = settings.leechThreshold
        this[Keys.DIFFICULTY_REVERSION] = settings.difficultyReversion
        this[Keys.SAME_DAY_REPEAT] = settings.sameDayRepeatLimit
        this[Keys.OVERDUE_BONUS] = settings.overdueMaxBonus
        this[Keys.DEADLINE_RAMP_DAYS] = settings.deadlineRampDays
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
    )

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
        val CALENDAR_SYNC_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
        val CALENDAR_SYNC_WINDOW = intPreferencesKey("calendar_sync_window_hours")
        val MAX_INTERVAL_DAYS = intPreferencesKey("srs_max_interval_days")
        val INTERVAL_FUZZ = booleanPreferencesKey("srs_interval_fuzz")
        val LEECH_THRESHOLD = intPreferencesKey("srs_leech_threshold")
        val DIFFICULTY_REVERSION = doublePreferencesKey("srs_difficulty_reversion")
        val SAME_DAY_REPEAT = intPreferencesKey("srs_same_day_repeat_limit")
        val OVERDUE_BONUS = doublePreferencesKey("srs_overdue_bonus")
        val DEADLINE_RAMP_DAYS = intPreferencesKey("srs_deadline_ramp_days")
    }
}
