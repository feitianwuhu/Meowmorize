package com.meowmorize.feature.settings

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowDivider
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.designsystem.component.MeowPrimaryButton
import com.meowmorize.core.designsystem.component.MeowSegmented
import com.meowmorize.core.designsystem.component.MeowSettingRow
import com.meowmorize.core.designsystem.component.MeowSliderRow
import com.meowmorize.core.designsystem.component.MeowStepper
import com.meowmorize.core.designsystem.component.MeowSwitchRow
import com.meowmorize.core.designsystem.component.SectionHeader
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ThemeMode
import com.meowmorize.core.model.UserSettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 日历权限必须由 Activity 申请；拿到结果后交给 ViewModel 继续同步
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    SettingsScreen(
        state = state,
        onCapacityChange = viewModel::setCapacityMinutes,
        onMaxNewChange = viewModel::setMaxNewPerDay,
        onRetentionChange = viewModel::setTargetRetention,
        onAdaptiveChange = viewModel::setAdaptiveLoad,
        onWeekendChange = viewModel::setWeekendRelief,
        onThemeChange = viewModel::setTheme,
        onStyleChange = viewModel::setAppStyle,
        onRegeneratePlan = viewModel::regenerateTodayPlan,
        onDismissMessage = viewModel::dismissMessage,
        onAdvanceClock = viewModel::advanceByDays,
        onJumpToDateTime = viewModel::jumpTo,
        onResetClock = viewModel::resetClock,
        onToggleCalendarSync = { enabled ->
            if (!enabled) {
                viewModel.disableCalendarSync()
            } else if (state.hasCalendarPermission) {
                viewModel.enableCalendarSync()
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                )
            }
        },
        onSyncWindowChange = viewModel::setSyncWindowHours,
        onSyncNow = viewModel::syncNow,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onCapacityChange: (Int) -> Unit,
    onMaxNewChange: (Int) -> Unit,
    onRetentionChange: (Double) -> Unit,
    onAdaptiveChange: (Boolean) -> Unit,
    onWeekendChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onStyleChange: (AppStyle) -> Unit,
    onRegeneratePlan: () -> Unit,
    onDismissMessage: () -> Unit,
    onAdvanceClock: (Long) -> Unit,
    onJumpToDateTime: (LocalDate, Int, Int) -> Unit,
    onResetClock: () -> Unit,
    onToggleCalendarSync: (Boolean) -> Unit,
    onSyncWindowChange: (Int) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val context = LocalContext.current
    val virtualDateTime = state.virtualNow.atZone(ZoneId.systemDefault())

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2400)
            onDismissMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "所有参数都可以随时改，改完立刻生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                MeowCard {
                    SectionHeader(
                        title = "今日节奏",
                        subtitle = "决定算法每天敢给你排多少任务",
                    )
                    MeowSliderRow(
                        title = "每日可投入时间",
                        valueText = "${settings.dailyCapacityMinutes} 分钟",
                        value = settings.dailyCapacityMinutes.toFloat(),
                        onValueChange = { onCapacityChange(it.roundToInt()) },
                        valueRange = UserSettings.MIN_CAPACITY_MINUTES.toFloat()..
                            UserSettings.MAX_CAPACITY_MINUTES.toFloat(),
                        description = "这是基准容量，忙碌时会自动下调",
                    )
                    MeowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "每日新任务上限",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "新学内容不会挤掉到期复习",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MeowStepper(
                            value = settings.maxNewPerDay,
                            onValueChange = onMaxNewChange,
                            min = UserSettings.MIN_NEW_PER_DAY,
                            max = UserSettings.MAX_NEW_PER_DAY,
                        )
                    }
                    MeowDivider()
                    MeowSliderRow(
                        title = "目标记忆保持率",
                        valueText = "${(settings.targetRetention * 100).roundToInt()}%",
                        value = settings.targetRetention.toFloat(),
                        onValueChange = { onRetentionChange(it.toDouble()) },
                        valueRange = UserSettings.MIN_RETENTION.toFloat()..UserSettings.MAX_RETENTION.toFloat(),
                        description = "越高复习越频繁：90% 是大多数人的舒适档",
                    )
                }
            }

            item {
                MeowCard {
                    SectionHeader(title = "弹性规则", subtitle = "让计划跟着生活走，而不是反过来")
                    MeowSwitchRow(
                        title = "近期完成率偏低时自动减负",
                        description = "连续没完成时，容量会自动下调，先找回节奏",
                        checked = settings.adaptiveLoadEnabled,
                        onCheckedChange = onAdaptiveChange,
                        icon = Icons.Rounded.AutoAwesome,
                    )
                    MeowDivider()
                    MeowSwitchRow(
                        title = "周末放宽",
                        description = "周六周日的容量按 85% 计算",
                        checked = settings.weekendReliefEnabled,
                        onCheckedChange = onWeekendChange,
                        icon = Icons.Rounded.Weekend,
                    )
                }
            }

            item {
                MeowCard {
                    SectionHeader(
                        title = "系统日历同步",
                        subtitle = "把接下来要做的事放进手机日历",
                    )
                    MeowSwitchRow(
                        title = "同步未来 ${settings.calendarSyncWindowHours} 小时的任务",
                        description = if (settings.calendarSyncEnabled) {
                            "任务更新后自动同步；关闭会同时移除已写入的日程"
                        } else {
                            "默认关闭，开启需要日历读写权限"
                        },
                        checked = settings.calendarSyncEnabled,
                        onCheckedChange = onToggleCalendarSync,
                        icon = Icons.Rounded.CalendarMonth,
                    )
                    MeowDivider()
                    Text(
                        text = "同步窗口",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "只同步这段时间内到期的任务，避免把整个任务库塞进日历",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(12, 24, 48, 72).forEach { hours ->
                            ActionChip(
                                text = "${hours} 小时",
                                selected = settings.calendarSyncWindowHours == hours,
                                onClick = { onSyncWindowChange(hours) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MeowOutlinedButton(
                            text = if (state.isSyncing) "同步中…" else "立即同步",
                            onClick = onSyncNow,
                            enabled = settings.calendarSyncEnabled && !state.isSyncing,
                            icon = Icons.Rounded.Refresh,
                        )
                        if (!state.hasCalendarPermission) {
                            Text(
                                text = "未授权",
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalMeowColors.current.warning,
                            )
                        }
                    }
                }
            }

            item {
                MeowCard {
                    SectionHeader(title = "界面风格", subtitle = "两套信息组织方式，不是简单换色")
                    MeowSegmented(
                        options = listOf("柔角卡片", "终端"),
                        selectedIndex = AppStyle.entries.indexOf(settings.appStyle),
                        onSelect = { index -> onStyleChange(AppStyle.entries[index]) },
                    )
                    Text(
                        text = if (settings.appStyle == AppStyle.TERMINAL) {
                            "终端：深蓝黑底、等宽数字、规格条与曲线。固定深色，不提供主题切换。"
                        } else {
                            "柔角卡片：大圆角、柔和层次、暖橙强调、环形进度。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    // 终端风格固定深色，主题模式没有意义，直接不展示
                    if (settings.appStyle != AppStyle.TERMINAL) {
                        Spacer(Modifier.height(16.dp))
                        MeowDivider()
                        SectionHeader(title = "主题模式")
                        MeowSegmented(
                            options = listOf("跟随系统", "浅色", "深色"),
                            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                            onSelect = { index -> onThemeChange(ThemeMode.entries[index]) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            item {
                MeowCard(containerColor = MaterialTheme.colorScheme.surface) {
                    SectionHeader(title = "算法说明", subtitle = "看得懂，才敢放心用")
                    Text(
                        text = "复习时间由 FSRS 遗忘曲线模型计算：每条内容维护「稳定性」与「难度」，" +
                            "按你每次的自评更新，目标是让复习发生在「快要忘记」的时候，而不是固定隔几天。" +
                            "每天的条数则受容量与弹性规则约束：宁可顺延，也不超发。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    MeowOutlinedButton(
                        text = "重新生成今日计划",
                        onClick = onRegeneratePlan,
                        icon = Icons.Rounded.Refresh,
                    )
                }
            }

            item {
                MeowCard(containerColor = LocalMeowColors.current.containerLow, borderColor = Color.Transparent) {
                    SectionHeader(
                        title = "测试工具 · 时间快进",
                        subtitle = "验证遗忘曲线，不用真的等一周",
                    )
                    Text(
                        text = "当前应用时间：" + virtualDateTime.format(
                            DateTimeFormatter.ofPattern("M 月 d 日 HH:mm"),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (state.clockShifted) {
                            val days = state.clockOffsetMillis / 86_400_000.0
                            "已偏移约 " + String.format(java.util.Locale.CHINA, "%.1f", days) + " 天"
                        } else {
                            "与真实时间一致"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1L, 3L, 7L, 30L).forEach { days ->
                            ActionChip(
                                text = "+$days 天",
                                selected = false,
                                onClick = { onAdvanceClock(days) },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeowOutlinedButton(
                            text = "选择日期与时间",
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                onJumpToDateTime(
                                                    LocalDate.of(year, month + 1, day),
                                                    hour,
                                                    minute,
                                                )
                                            },
                                            virtualDateTime.hour,
                                            virtualDateTime.minute,
                                            true,
                                        ).show()
                                    },
                                    virtualDateTime.year,
                                    virtualDateTime.monthValue - 1,
                                    virtualDateTime.dayOfMonth,
                                ).show()
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        MeowTextButton(
                            text = "回到真实时间",
                            onClick = onResetClock,
                            color = if (state.clockShifted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = "快进会改变「今天」的判定、到期任务与日历同步结果，仅建议在测试时使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                // 用主题面（而非透明）保证终端/深色下这一栏不会浮出浅色底
                MeowCard {
                    MeowSettingRow(
                        title = "数据存储",
                        value = "仅本地",
                        description = "所有背诵内容与复习记录都保存在这台设备上",
                    )
                    Text(
                        text = "Meowmorize 1.0.0 · Android 10+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        if (state.message != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = state.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

/** 设置页用小胶囊按钮（快捷加量、快捷快进）。 */
@Composable
private fun ActionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
