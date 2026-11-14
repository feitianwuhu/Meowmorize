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
    }
}
