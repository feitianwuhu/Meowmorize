package com.meowmorize.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.CalendarSyncRepository
import com.meowmorize.core.domain.repository.CalendarSyncResult
import com.meowmorize.core.domain.app.AppVersionProvider
import com.meowmorize.core.domain.repository.BackupCounts
import com.meowmorize.core.domain.repository.BackupRepository
import com.meowmorize.core.domain.repository.DailyWallpaper
import com.meowmorize.core.domain.repository.WallpaperRepository
import com.meowmorize.core.model.AccentChoice
import com.meowmorize.core.model.GlassTone
import com.meowmorize.core.model.WallpaperScale
import androidx.compose.ui.graphics.toArgb
import com.meowmorize.core.designsystem.theme.MeowAccents
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.TimeTravelRepository
import com.meowmorize.core.domain.usecase.CheckUpdateUseCase
import com.meowmorize.core.domain.usecase.PrepareTodayPlanUseCase
import com.meowmorize.core.domain.usecase.SyncUpcomingToCalendarUseCase
import com.meowmorize.core.domain.usecase.UpdateCheckResult
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ThemeMode
import com.meowmorize.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    /** 应用内当前时间（可能被「时间快进」拨动过）。 */
    val virtualNow: Instant = Instant.EPOCH,
    val clockOffsetMillis: Long = 0L,
    val hasCalendarPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val message: String? = null,
    val update: UpdateUiState = UpdateUiState(),
    val wallpaper: WallpaperUiState = WallpaperUiState(),
    val backup: BackupUiState = BackupUiState(),
) {
    val clockShifted: Boolean get() = clockOffsetMillis != 0L
}

/**
 * 「检查更新」这一小块的状态。
 *
 * 结果一直留着（而不是查完就弹一次提示）：用户可能切走再回来，
 * 版本信息不该凭空消失。
 */
/** 壁纸页状态：元数据用于展示版权，文件用于预览与导出。 */
data class WallpaperUiState(
    val meta: DailyWallpaper? = null,
    val file: java.io.File? = null,
    val busy: Boolean = false,
)

enum class InstallStatus { IDLE, DOWNLOADING, READY, FAILED }

/** 备份页状态：条数用于让用户确认「导的是不是这份数据」。 */
data class BackupUiState(
    val counts: BackupCounts = BackupCounts(),
    val busy: Boolean = false,
)

data class UpdateUiState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val result: UpdateCheckResult? = null,
    val installStatus: InstallStatus = InstallStatus.IDLE,
    val installProgress: Float = 0f,
    val installedFile: java.io.File? = null,
    val installMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timeTravelRepository: TimeTravelRepository,
    private val calendarSyncRepository: CalendarSyncRepository,
    private val syncUpcomingToCalendar: SyncUpcomingToCalendarUseCase,
    private val prepareTodayPlan: PrepareTodayPlanUseCase,
    private val timeProvider: TimeProvider,
    private val checkUpdate: CheckUpdateUseCase,
    private val appVersionProvider: AppVersionProvider,
    private val wallpaperRepository: WallpaperRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val apkUpdate = com.meowmorize.feature.settings.update.ApkUpdateHelper(context)

    private data class LocalState(
        val isSyncing: Boolean = false,
        val message: String? = null,
        val hasPermission: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState(hasPermission = calendarSyncRepository.hasPermission()))

    private val updateState = MutableStateFlow(UpdateUiState(currentVersion = appVersionProvider.versionName()))

    private val wallpaperState = MutableStateFlow(WallpaperUiState())

    private val backupState = MutableStateFlow(BackupUiState())

    /** 每秒刷新一次虚拟时间，让「当前应用时间」是活的。 */
    private val clockTicker = flow {
        while (true) {
            emit(timeTravelRepository.virtualNow())
            delay(1_000)
        }
    }

    /** combine 的具名重载最多 5 路，这里把三块「次要状态」并成一路。 */
    private data class SideStates(
        val update: UpdateUiState,
        val wallpaper: WallpaperUiState,
        val backup: BackupUiState,
    )

    private val sideStates = combine(updateState, wallpaperState, backupState) { update, wallpaper, backup ->
        SideStates(update, wallpaper, backup)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        clockTicker,
        local,
        sideStates,
    ) { settings, now, mutation, side ->
        SettingsUiState(
            settings = settings,
            virtualNow = now,
            clockOffsetMillis = timeTravelRepository.offsetMillis(),
            hasCalendarPermission = mutation.hasPermission,
            isSyncing = mutation.isSyncing,
            message = mutation.message,
            update = side.update,
            wallpaper = side.wallpaper,
            backup = side.backup,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            virtualNow = timeTravelRepository.virtualNow(),
            clockOffsetMillis = timeTravelRepository.offsetMillis(),
            hasCalendarPermission = calendarSyncRepository.hasPermission(),
            update = UpdateUiState(currentVersion = appVersionProvider.versionName()),
        ),
    )

    // ---------- 学习节奏 ----------

    fun setCapacityMinutes(minutes: Int) = updateSettings { it.copy(dailyCapacityMinutes = minutes) }

    fun setMaxNewPerDay(count: Int) = updateSettings { it.copy(maxNewPerDay = count) }

    fun setTargetRetention(retention: Double) = updateSettings { it.copy(targetRetention = retention) }

    fun setAdaptiveLoad(enabled: Boolean) = updateSettings { it.copy(adaptiveLoadEnabled = enabled) }

    fun setWeekendRelief(enabled: Boolean) = updateSettings { it.copy(weekendReliefEnabled = enabled) }

    fun setTheme(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    fun setAppStyle(style: AppStyle) = updateSettings { it.copy(appStyle = style) }

    // ---------- 弹性实验（默认关闭） ----------

    fun setTodayStateEnabled(enabled: Boolean) = updateSettings { it.copy(todayStateEnabled = enabled) }

    fun setMinuteCompletion(enabled: Boolean) = updateSettings { it.copy(minuteCompletionEnabled = enabled) }

    fun setSubjectQuota(enabled: Boolean) = updateSettings { it.copy(subjectQuotaEnabled = enabled) }

    fun setTrendWindow(days: Int) = updateSettings { it.copy(trendWindowDays = days) }

    fun setSubjectShares(minShare: Double, maxShare: Double) = updateSettings {
        it.copy(
            subjectMinShare = minShare.coerceAtMost(maxShare),
            subjectMaxShare = maxShare,
        )
    }

    // ---------- 复习算法 ----------

    // 算法参数不影响「今天排多少」，因此只写设置、不重排计划：
    // 拖滑杆时若每次都重算，会明显卡顿。

    fun setMaxIntervalDays(days: Int) = updateAlgorithm { it.copy(maxIntervalDays = days) }

    fun setIntervalFuzz(enabled: Boolean) = updateAlgorithm { it.copy(intervalFuzzEnabled = enabled) }

    fun setLeechThreshold(count: Int) = updateAlgorithm { it.copy(leechThreshold = count) }

    fun setDifficultyReversion(value: Double) = updateAlgorithm { it.copy(difficultyReversion = value) }

    fun setSameDayRepeatLimit(count: Int) = updateAlgorithm { it.copy(sameDayRepeatLimit = count) }

    fun setDeadlineRampDays(days: Int) = updateAlgorithm { it.copy(deadlineRampDays = days) }

    /** 一键恢复算法默认值：改坏了能回来，用户才敢试。 */
    fun resetAlgorithmDefaults() = updateAlgorithm {
        it.copy(
            maxIntervalDays = 365,
            intervalFuzzEnabled = false,
            leechThreshold = 4,
            difficultyReversion = 0.031,
            sameDayRepeatLimit = 3,
            overdueMaxBonus = 0.10,
            deadlineRampDays = 30,
        )
    }

    fun regenerateTodayPlan() {
        viewModelScope.launch {
            local.value = local.value.copy(message = "正在重新计算…")
            runCatching { prepareTodayPlan(force = true) }
                .onSuccess { plan ->
                    local.value = local.value.copy(
                        message = "已重排：${plan.totalCount} 条 / 约 ${plan.capacityMinutes} 分钟",
                    )
                }
                .onFailure { local.value = local.value.copy(message = "重排失败，请稍后再试") }
        }
    }

    // ---------- 时间快进（测试用） ----------

    fun advanceByDays(days: Long) {
        viewModelScope.launch {
            timeTravelRepository.advanceBy(Duration.ofDays(days))
            afterClockChanged("已快进 $days 天")
        }
    }

    fun advanceByHours(hours: Long) {
        viewModelScope.launch {
            timeTravelRepository.advanceBy(Duration.ofHours(hours))
            afterClockChanged("已快进 $hours 小时")
        }
    }

    fun jumpTo(date: LocalDate, hour: Int, minute: Int) {
        viewModelScope.launch {
            val target = date.atTime(LocalTime.of(hour, minute))
                .atZone(timeProvider.zone())
                .toInstant()
            timeTravelRepository.jumpTo(target)
            afterClockChanged("已切换到 ${target.atZone(timeProvider.zone()).toLocalDateTime()}")
        }
    }

    fun resetClock() {
        viewModelScope.launch {
            timeTravelRepository.reset()
            afterClockChanged("已回到真实时间")
        }
    }

    /**
     * 拨动时钟后必须重算今日计划：
     * 旧的「今天」在新时间点已经过期，继续沿用会让测试结论完全失真。
     */
    private suspend fun afterClockChanged(message: String) {
        runCatching { prepareTodayPlan(force = true) }
        local.value = local.value.copy(message = message)
    }

    // ---------- 系统日历同步 ----------

    fun onPermissionGranted() {
        local.value = local.value.copy(hasPermission = true)
        enableCalendarSync()
    }

    fun onPermissionDenied() {
        local.value = local.value.copy(
            hasPermission = false,
            message = "没有日历权限，无法同步。可到系统设置里开启后再试",
        )
    }

    fun enableCalendarSync() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(calendarSyncEnabled = true) }
            local.value = local.value.copy(isSyncing = true)
            val result = runCatching { syncUpcomingToCalendar() }.getOrNull()
            local.value = local.value.copy(
                isSyncing = false,
                message = describe(result, fallback = "已开启同步"),
            )
        }
    }

    fun disableCalendarSync() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(calendarSyncEnabled = false) }
            local.value = local.value.copy(isSyncing = true)
            val result = runCatching { syncUpcomingToCalendar() }.getOrNull()
            local.value = local.value.copy(
                isSyncing = false,
                message = when {
                    result == null -> "已关闭同步"
                    result.permissionDenied -> "已关闭同步，但没有日历权限，旧日程未能清除"
                    else -> "已关闭同步，并移除 ${result.removed} 条日历事件"
                },
            )
        }
    }

    fun setSyncWindowHours(hours: Int) {
        updateSettings { it.copy(calendarSyncWindowHours = hours) }
        viewModelScope.launch { runCatching { syncUpcomingToCalendar() } }
    }

    /** 手动同步按钮：不依赖「任务是否刚更新」，随时可以补一次。 */
    fun syncNow() {
        viewModelScope.launch {
            local.value = local.value.copy(isSyncing = true, hasPermission = calendarSyncRepository.hasPermission())
            val result = runCatching { syncUpcomingToCalendar() }.getOrNull()
            local.value = local.value.copy(
                isSyncing = false,
                message = describe(result, fallback = "同步完成"),
            )
        }
    }

    fun dismissMessage() {
        local.value = local.value.copy(message = null)
    }

    private fun describe(result: CalendarSyncResult?, fallback: String): String = when {
        result == null -> "同步失败，请稍后再试"
        result.permissionDenied -> "没有日历权限，请先授权"
        result.failed -> "系统日历不可用，同步失败"
        else -> "$fallback：${result.synced} 条已同步，${result.removed} 条已移除"
    }

    // ---------- 外观：壁纸与磨砂 ----------

    /** 打开壁纸页时拉一次元数据；已有缓存就不联网。 */
    fun loadWallpaper() {
        viewModelScope.launch {
            val cached = runCatching { wallpaperRepository.cached() }.getOrNull()
            if (cached != null) {
                wallpaperState.value = wallpaperState.value.copy(meta = cached.first, file = cached.second)
                return@launch
            }
            wallpaperState.value = wallpaperState.value.copy(busy = true)
            // ensureDownloaded 内部已经会取元数据，不必先单独 today() 再下一遍
            val downloaded = runCatching { wallpaperRepository.ensureDownloaded() }.getOrNull()
            val meta = downloaded?.first
                ?: runCatching { wallpaperRepository.today() }.getOrNull()
            wallpaperState.value = WallpaperUiState(
                meta = meta,
                file = downloaded?.second,
                busy = false,
            )
        }
    }

    /** 把今日壁纸存到系统「下载」目录，方便用户自己拿去当壁纸。 */
    fun downloadWallpaper() {
        viewModelScope.launch {
            val state = wallpaperState.value
            val file = state.file
            val meta = state.meta
            if (file == null || meta == null) {
                local.value = local.value.copy(message = "还没有可下载的壁纸")
                return@launch
            }
            wallpaperState.value = state.copy(busy = true)
            val name = runCatching { wallpaperRepository.exportToDownloads(meta, file) }.getOrNull()
            wallpaperState.value = wallpaperState.value.copy(busy = false)
            local.value = local.value.copy(
                message = if (name != null) "已保存到 下载/Meowmorize" else "保存失败",
            )
        }
    }

    fun setWallpaperEnabled(enabled: Boolean) = persistOnly { it.copy(wallpaperEnabled = enabled) }

    fun setWallpaperScale(scale: WallpaperScale) = persistOnly { it.copy(wallpaperScale = scale) }

    fun setWallpaperBlur(dp: Int) = persistOnly { it.copy(wallpaperBlur = dp) }

    fun setWallpaperScrim(value: Double) = persistOnly { it.copy(wallpaperScrim = value) }

    fun setGlassTone(tone: GlassTone) = persistOnly { it.copy(glassTone = tone) }

    fun setAccent(choice: AccentChoice) = persistOnly { it.copy(accentChoice = choice) }

    fun setAccentHue(hue: Float) = persistOnly {
        // 固定较高饱和度与亮度，避免用户滑到灰色看不出强调
        it.copy(
            accentChoice = AccentChoice.CUSTOM,
            accentCustomArgb = MeowAccents.fromHue(hue).toArgb().toLong(),
        )
    }

    // ---------- 数据与备份 ----------

    fun loadBackupCounts() {
        viewModelScope.launch {
            val counts = runCatching { backupRepository.counts() }.getOrNull() ?: return@launch
            backupState.value = backupState.value.copy(counts = counts)
        }
    }

    fun exportBackup(uriString: String) {
        viewModelScope.launch {
            backupState.value = backupState.value.copy(busy = true)
            val result = runCatching { backupRepository.exportTo(uriString) }
            backupState.value = backupState.value.copy(busy = false)
            result
                .onSuccess { counts ->
                    backupState.value = backupState.value.copy(counts = counts)
                    local.value = local.value.copy(
                        message = "已导出 ${counts.subjects} 个学科 / ${counts.items} 条任务",
                    )
                }
                .onFailure {
                    local.value = local.value.copy(message = "导出失败：${it.message ?: "未知原因"}")
                }
        }
    }

    fun importBackup(uriString: String) {
        viewModelScope.launch {
            backupState.value = backupState.value.copy(busy = true)
            val result = runCatching { backupRepository.importFrom(uriString) }
            backupState.value = backupState.value.copy(busy = false)
            result
                .onSuccess { summary ->
                    val counts = runCatching { backupRepository.counts() }.getOrNull()
                    if (counts != null) backupState.value = backupState.value.copy(counts = counts)
                    local.value = local.value.copy(
                        message = buildString {
                            append("已导入 ${summary.items} 条任务")
                            if (summary.subjects > 0) append("、${summary.subjects} 个学科")
                            if (summary.skipped > 0) append("；跳过 ${summary.skipped} 条重复")
                        },
                    )
                }
                .onFailure {
                    local.value = local.value.copy(message = "导入失败：${it.message ?: "未知原因"}")
                }
        }
    }

    // ---------- 检查更新 ----------

    /**
     * 手动检查更新。
     *
     * 只查、不装：查到新版本后要不要装由用户点「去下载」自己决定，
     * 不会出现「打开应用就被替换掉」的意外。
     */
    fun checkForUpdate() {
        if (updateState.value.checking) return
        viewModelScope.launch {
            updateState.value = updateState.value.copy(checking = true, result = null)
            val result = checkUpdate()
            updateState.value = UpdateUiState(
                currentVersion = result.currentVersion,
                checking = false,
                result = result,
            )
            // 手动查过就刷新节流时间，免得刚查完重启又发一次请求
            persistOnly { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
        }
    }

    /** 启动时自动检查的开关。只影响「查不查」，不影响「装不装」。 */
    fun setAutoCheckUpdate(enabled: Boolean) = persistOnly { it.copy(autoCheckUpdate = enabled) }

    /**
     * 应用内更新：下载 APK 并交由系统安装器。
     *
     * 相比「跳浏览器」，页内下载能给出明确的百分比与失败原因，也不会把用户丢出应用。
     */
    fun startUpdateDownload(url: String, versionName: String) {
        if (updateState.value.installStatus == InstallStatus.DOWNLOADING) return
        viewModelScope.launch {
            updateState.value = updateState.value.copy(
                installStatus = InstallStatus.DOWNLOADING,
                installProgress = 0f,
                installMessage = null,
            )
            var lastNotified = -1
            val result = apkUpdate.download(url, versionName) { progress ->
                updateState.value = updateState.value.copy(installProgress = progress)
                // 每 5% 刷新一次通知，避免高频写通知栏
                val percent = (progress * 100).toInt()
                if (percent / 5 != lastNotified / 5) {
                    lastNotified = percent
                    apkUpdate.notifyProgress(progress, versionName)
                }
            }
            result
                .onSuccess { file ->
                    apkUpdate.notifyReady(versionName)
                    updateState.value = updateState.value.copy(
                        installStatus = InstallStatus.READY,
                        installProgress = 1f,
                        installedFile = file,
                        installMessage = null,
                    )
                }
                .onFailure { error ->
                    apkUpdate.clearNotification()
                    updateState.value = updateState.value.copy(
                        installStatus = InstallStatus.FAILED,
                        installMessage = error.message ?: "下载失败",
                    )
                }
        }
    }

    /** 唤起系统安装器；未授权「安装未知应用」时先跳授权页。 */
    fun installDownloaded() {
        val current = updateState.value
        val file = current.installedFile ?: return
        if (!apkUpdate.canInstall()) {
            apkUpdate.openUnknownSourcesSettings()
            updateState.value = current.copy(
                installMessage = "请先允许 Meowmorize 安装应用，然后回来再点一次",
            )
            return
        }
        runCatching { apkUpdate.launchInstaller(file) }
            .onFailure {
                updateState.value = current.copy(
                    installStatus = InstallStatus.FAILED,
                    installMessage = "无法唤起系统安装器：${it.message ?: "未知原因"}",
                )
            }
    }

    fun openInstallerPermission() = apkUpdate.openUnknownSourcesSettings()

    fun openDownloadInBrowser(url: String) = apkUpdate.openInBrowser(url)

    /** 只落盘，不触发今日计划重排——这类设置跟排程无关。 */
    private fun persistOnly(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            runCatching { settingsRepository.update(transform) }
                .onFailure { local.value = local.value.copy(message = "设置保存失败") }
        }
    }

    private fun updateAlgorithm(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            runCatching { settingsRepository.update(transform) }
                .onFailure { local.value = local.value.copy(message = "设置保存失败") }
        }
    }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.update(transform)
                prepareTodayPlan(force = true)
            }.onFailure { local.value = local.value.copy(message = "设置已保存，但今日计划重排失败") }
        }
    }
}
