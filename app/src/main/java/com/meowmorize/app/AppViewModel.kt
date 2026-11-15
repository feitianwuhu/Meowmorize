package com.meowmorize.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.usecase.CheckUpdateUseCase
import com.meowmorize.core.domain.usecase.UpdateCheckResult
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ThemeMode
import com.meowmorize.core.model.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 顶层状态：主题、界面风格，以及「有新版」的提示。
 *
 * 更新只在这里**提示**，不下载也不安装——装不装由用户自己决定。
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val checkUpdate: CheckUpdateUseCase,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.settings
        .map { it.themeMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    val appStyle: StateFlow<AppStyle> = settingsRepository.settings
        .map { it.appStyle }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppStyle.SOFT_CARD,
        )

    private val _updatePrompt = MutableStateFlow<UpdateInfo?>(null)

    /** 非空时弹「发现新版本」对话框。 */
    val updatePrompt: StateFlow<UpdateInfo?> = _updatePrompt.asStateFlow()

    /**
     * 启动时按需检查更新。
     *
     * 三个克制的点：
     * - 关了开关就一次都不查；
     * - 6 小时内不重复查（GitHub 未认证接口有 60 次/小时/IP 的限制）；
     * - 无论成功失败都记下时间戳，避免断网时每次启动都白等一次超时。
     */
    fun checkUpdateOnStart() {
        viewModelScope.launch {
            val settings = runCatching { settingsRepository.current() }.getOrNull() ?: return@launch
            if (!settings.autoCheckUpdate) return@launch

            val now = System.currentTimeMillis()
            if (now - settings.lastUpdateCheckAt < THROTTLE_MILLIS) return@launch
            runCatching { settingsRepository.update { it.copy(lastUpdateCheckAt = now) } }

            when (val result = checkUpdate()) {
                is UpdateCheckResult.Available -> _updatePrompt.value = result.update
                else -> Unit
            }
        }
    }

    fun dismissUpdatePrompt() {
        _updatePrompt.value = null
    }

    private companion object {
        const val THROTTLE_MILLIS = 6 * 60 * 60 * 1000L
    }
}
