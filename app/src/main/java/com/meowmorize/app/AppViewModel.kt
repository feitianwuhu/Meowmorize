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
import com.meowmorize.core.domain.repository.WallpaperRepository
import com.meowmorize.core.model.AccentChoice
import com.meowmorize.core.model.GlassTone
import com.meowmorize.core.model.WallpaperScale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 顶层外观状态：风格、强调色、磨砂明暗、壁纸。
 *
 * 壁纸文件也放在这里：它由设置变化驱动加载（切到 GLASS 或打开壁纸才会去下载），
 * 放在 ViewModel 才能跨 Activity 重建保持。
 */
data class AppearanceState(
    val style: AppStyle = AppStyle.SOFT_CARD,
    val accentChoice: AccentChoice = AccentChoice.SYSTEM,
    val accentCustomArgb: Long = 0xFF3B82F6,
    val glassTone: GlassTone = GlassTone.SYSTEM,
    val wallpaperEnabled: Boolean = true,
    val wallpaperScale: WallpaperScale = WallpaperScale.FILL,
    val wallpaperBlur: Int = 24,
    val wallpaperScrim: Double = 0.35,
    val wallpaperFile: java.io.File? = null,
)

/**
 * 顶层状态：主题、界面风格，以及「有新版」的提示。
 *
 * 更新只在这里**提示**，不下载也不安装——装不装由用户自己决定。
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val checkUpdate: CheckUpdateUseCase,
    private val wallpaperRepository: WallpaperRepository,
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

    /**
     * 壁纸文件：只有 GLASS + 开启壁纸时才去取（必要时联网下载当天的图）。
     * 用 mapLatest：用户来回切开关时，前一次下载会被取消，不会堆积。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val wallpaperFile: Flow<java.io.File?> = settingsRepository.settings
        .map { it.appStyle == AppStyle.GLASS && it.wallpaperEnabled }
        .distinctUntilChanged()
        .mapLatest { enabled ->
            if (!enabled) {
                null
            } else {
                runCatching { wallpaperRepository.ensureDownloaded()?.second }.getOrNull()
            }
        }

    val appearance: StateFlow<AppearanceState> = combine(
        settingsRepository.settings,
        wallpaperFile,
    ) { settings, wallpaper ->
        AppearanceState(
            style = settings.appStyle,
            accentChoice = settings.accentChoice,
            accentCustomArgb = settings.accentCustomArgb,
            glassTone = settings.glassTone,
            wallpaperEnabled = settings.wallpaperEnabled,
            wallpaperScale = settings.wallpaperScale,
            wallpaperBlur = settings.wallpaperBlur,
            wallpaperScrim = settings.wallpaperScrim,
            wallpaperFile = wallpaper,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearanceState(),
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
