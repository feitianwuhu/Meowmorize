package com.meowmorize.core.domain.repository

import com.meowmorize.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** 用户设置读写。实现基于 DataStore，读取是同步可用的（[current] 用于算法即时决策）。 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun current(): UserSettings

    suspend fun update(transform: (UserSettings) -> UserSettings)
}
