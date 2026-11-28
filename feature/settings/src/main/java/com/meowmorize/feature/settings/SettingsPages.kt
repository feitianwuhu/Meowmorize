package com.meowmorize.feature.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import com.meowmorize.core.designsystem.theme.MeowAccents
import com.meowmorize.core.model.AccentChoice
import com.meowmorize.core.model.GlassTone
import com.meowmorize.core.model.WallpaperScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.meowTween
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowDivider
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import com.meowmorize.core.designsystem.component.MeowPrimaryButton
import com.meowmorize.core.designsystem.component.MeowSegmented
import com.meowmorize.core.designsystem.component.MeowSettingRow
import com.meowmorize.core.designsystem.component.MeowSliderRow
import com.meowmorize.core.designsystem.component.MeowStepper
import com.meowmorize.core.designsystem.component.MeowSwitchRow
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.designsystem.component.SectionHeader
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.domain.usecase.UpdateCheckResult
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ThemeMode
import com.meowmorize.core.model.UserSettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 设置主页的分组。每个分组对应一个二级页面。
 *
 * 拆分的标准是「一次性要不要同时看」而不是功能多少：
 * 调今日节奏时不该被算法参数干扰，反之亦然。
 */
internal enum class SettingsGroup(
    val title: String,
    val subtitle: String,
) {
    RHYTHM("今日节奏", "决定算法每天敢给你排多少任务"),
    FLEXIBILITY("弹性规则", "让计划跟着生活走，而不是反过来"),
    ALGORITHM("复习算法", "改完下一次评分就生效，不会重算历史"),
    APPEARANCE("外观", "三套信息组织方式，不是简单换色"),
    CALENDAR("日历同步", "把接下来要做的事放进手机日历"),
    UPDATE("检查更新", "应用内下载安装，失败可回退到浏览器"),
    DATA("数据与备份", "导出 .mmr，换手机前先做一份"),
    ADVANCED("测试工具", "验证遗忘曲线，不用真的等一周"),
}

/** 按分组路由到具体页面。 */
@Composable
internal fun SettingsGroupPage(
    group: SettingsGroup,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onToggleCalendarSync: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    when (group) {
        SettingsGroup.RHYTHM -> RhythmPage(state, viewModel, onBack)
        SettingsGroup.FLEXIBILITY -> FlexibilityPage(state, viewModel, onBack)
        SettingsGroup.ALGORITHM -> AlgorithmPage(state, viewModel, onBack)
        SettingsGroup.APPEARANCE -> AppearancePage(state, viewModel, onBack)
        SettingsGroup.CALENDAR -> CalendarPage(state, viewModel, onToggleCalendarSync, onBack)
        SettingsGroup.UPDATE -> UpdatePage(state, viewModel, onOpenUrl, onBack)
        SettingsGroup.DATA -> DataPage(state, viewModel, onBack)
        SettingsGroup.ADVANCED -> AdvancedPage(state, viewModel, onBack)
    }
}

// ---------- 页面骨架 ----------

@Composable
private fun PageScaffold(
    group: SettingsGroup,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MeowIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回设置",
                        onClick = onBack,
                    )
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(
                    text = group.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }
        content()
    }
}

/** 二级页用的小胶囊按钮（快捷加量、快捷快进、同步窗口）。 */
@Composable
private fun ActionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(CircleShape)
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

// ---------- 今日节奏 ----------

@Composable
private fun RhythmPage(state: SettingsUiState, vm: SettingsViewModel, onBack: () -> Unit) {
    val settings = state.settings
    PageScaffold(SettingsGroup.RHYTHM, onBack) {
        item {
            MeowCard {
                MeowSliderRow(
                    title = "每日可投入时间",
                    valueText = "${settings.dailyCapacityMinutes} 分钟",
                    value = settings.dailyCapacityMinutes.toFloat(),
                    onValueChange = { vm.setCapacityMinutes(it.roundToInt()) },
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
                        onValueChange = vm::setMaxNewPerDay,
                        min = UserSettings.MIN_NEW_PER_DAY,
                        max = UserSettings.MAX_NEW_PER_DAY,
                    )
                }
                MeowDivider()
                MeowSliderRow(
                    title = "目标记忆保持率",
                    valueText = "${(settings.targetRetention * 100).roundToInt()}%",
                    value = settings.targetRetention.toFloat(),
                    onValueChange = { vm.setTargetRetention(it.toDouble()) },
                    valueRange = UserSettings.MIN_RETENTION.toFloat()..UserSettings.MAX_RETENTION.toFloat(),
                    description = "越高复习越频繁：90% 是大多数人的舒适档",
                )
            }
        }
        item {
            MeowCard {
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
                    onClick = vm::regenerateTodayPlan,
                    icon = Icons.Rounded.Refresh,
                )
            }
        }
    }
}

// ---------- 弹性规则 ----------

@Composable
private fun FlexibilityPage(state: SettingsUiState, vm: SettingsViewModel, onBack: () -> Unit) {
    val settings = state.settings
    PageScaffold(SettingsGroup.FLEXIBILITY, onBack) {
        item {
            MeowCard {
                SectionHeader(title = "自动调整", subtitle = "默认都开着，觉得被管得太多可以关")
                MeowSwitchRow(
                    title = "近期完成率偏低时自动减负",
                    description = "连续没完成时，容量会自动下调，先找回节奏",
                    checked = settings.adaptiveLoadEnabled,
                    onCheckedChange = vm::setAdaptiveLoad,
                    icon = Icons.Rounded.AutoAwesome,
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "周末放宽",
                    description = "周六周日的容量按 85% 计算",
                    checked = settings.weekendReliefEnabled,
                    onCheckedChange = vm::setWeekendRelief,
                    icon = Icons.Rounded.Weekend,
                )
            }
        }
        item {
            MeowCard {
                SectionHeader(
                    title = "弹性实验",
                    subtitle = "默认关闭；打开后可以对比「算法是不是更懂我」",
                )
                MeowSwitchRow(
                    title = "今天的状态入口",
                    description = "首页多三枚胶囊：累了 / 正常 / 状态不错。只影响当天，跨天自动失效",
                    checked = settings.todayStateEnabled,
                    onCheckedChange = vm::setTodayStateEnabled,
                    icon = Icons.Rounded.SelfImprovement,
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "按分钟计算完成率",
                    description = "现在按「条数」算，一道 30 分钟的题和 5 分钟的一样重；切到分钟更接近真实负担",
                    checked = settings.minuteCompletionEnabled,
                    onCheckedChange = vm::setMinuteCompletion,
                    icon = Icons.Rounded.Timer,
                )
                if (settings.minuteCompletionEnabled) {
                    MeowSliderRow(
                        title = "趋势窗口",
                        valueText = "${settings.trendWindowDays} 天",
                        value = settings.trendWindowDays.toFloat(),
                        onValueChange = { vm.setTrendWindow(it.toInt()) },
                        valueRange = 3f..14f,
                        description = "识别连续下滑与恢复期用；样本不足 3 天不会调整",
                    )
                }
                MeowDivider()
                MeowSwitchRow(
                    title = "学科配额与轮换",
                    description = "过载时按学科分容量，避免某一科连续几天吃满，其他科目被饿死",
                    checked = settings.subjectQuotaEnabled,
                    onCheckedChange = vm::setSubjectQuota,
                    icon = Icons.Rounded.CalendarMonth,
                )
                if (settings.subjectQuotaEnabled) {
                    MeowSliderRow(
                        title = "单科最小份额",
                        valueText = "${(settings.subjectMinShare * 100).toInt()}%",
                        value = settings.subjectMinShare.toFloat(),
                        onValueChange = {
                            vm.setSubjectShares(it.toDouble(), settings.subjectMaxShare)
                        },
                        valueRange = 0.10f..0.40f,
                        description = "任何学科都不至于整天排不进去",
                    )
                    MeowSliderRow(
                        title = "单科最大份额",
                        valueText = "${(settings.subjectMaxShare * 100).toInt()}%",
                        value = settings.subjectMaxShare.toFloat(),
                        onValueChange = {
                            vm.setSubjectShares(settings.subjectMinShare, it.toDouble())
                        },
                        valueRange = 0.40f..0.80f,
                        description = "避免某一科挤占整天",
                    )
                }
            }
        }
    }
}

// ---------- 复习算法 ----------

@Composable
private fun AlgorithmPage(state: SettingsUiState, vm: SettingsViewModel, onBack: () -> Unit) {
    val settings = state.settings
    PageScaffold(SettingsGroup.ALGORITHM, onBack) {
        item {
            MeowCard {
                MeowSliderRow(
                    title = "最长间隔",
                    valueText = if (settings.maxIntervalDays >= 365) {
                        "1 年"
                    } else {
                        "${settings.maxIntervalDays} 天"
                    },
                    value = settings.maxIntervalDays.toFloat(),
                    onValueChange = { vm.setMaxIntervalDays(it.toInt()) },
                    valueRange = 30f..365f,
                    description = "再熟的内容也会在这个期限内再见一次；设短一些更适合应付考试",
                )
                MeowDivider()
                MeowSliderRow(
                    title = "同日重练上限",
                    valueText = "${settings.sameDayRepeatLimit} 次",
                    value = settings.sameDayRepeatLimit.toFloat(),
                    onValueChange = { vm.setSameDayRepeatLimit(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    description = "一天内反复「忘了」超过这个次数，就冷却 6 小时再来，避免硬耗",
                )
                MeowDivider()
                MeowSliderRow(
                    title = "难度回归强度",
                    valueText = String.format(java.util.Locale.CHINA, "%.3f", settings.difficultyReversion),
                    value = settings.difficultyReversion.toFloat(),
                    onValueChange = { vm.setDifficultyReversion(it.toDouble()) },
                    valueRange = 0f..0.2f,
                    description = "越大越容易把难度拉回「较简单」，长期记得的内容会更早复习完",
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "间隔抖动",
                    description = "同一批内容不再同一天到期，代价是间隔不再精确可预期",
                    checked = settings.intervalFuzzEnabled,
                    onCheckedChange = vm::setIntervalFuzz,
                    icon = Icons.Rounded.AutoAwesome,
                )
                MeowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "反复遗忘提示阈值",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "遗忘 ${settings.leechThreshold} 次后标记为「反复遗忘」，建议改写或拆分",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MeowStepper(
                        value = settings.leechThreshold,
                        onValueChange = vm::setLeechThreshold,
                        min = UserSettings.MIN_LEECH_THRESHOLD,
                        max = UserSettings.MAX_LEECH_THRESHOLD,
                    )
                }
                Spacer(Modifier.height(12.dp))
                MeowTextButton(text = "恢复算法默认值", onClick = vm::resetAlgorithmDefaults)
            }
        }
    }
}

// ---------- 外观 ----------

@Composable
private fun AppearancePage(state: SettingsUiState, vm: SettingsViewModel, onBack: () -> Unit) {
    val settings = state.settings
    val isGlass = settings.appStyle == AppStyle.GLASS
    PageScaffold(SettingsGroup.APPEARANCE, onBack) {
        item {
            MeowCard {
                SectionHeader(title = "界面风格", subtitle = "三套信息组织方式，不是简单换色")
                MeowSegmented(
                    options = listOf("柔角卡片", "终端", "磨砂玻璃"),
                    selectedIndex = AppStyle.entries.indexOf(settings.appStyle),
                    onSelect = { index -> vm.setAppStyle(AppStyle.entries[index]) },
                )
                Text(
                    text = when (settings.appStyle) {
                        AppStyle.TERMINAL ->
                            "终端：深蓝黑底、等宽数字、规格条与曲线。固定深色，不提供主题切换。"
                        AppStyle.GLASS ->
                            "磨砂玻璃：每日壁纸做底，卡片与按钮是半透明玻璃。下面的壁纸与模糊设置都已生效。"
                        AppStyle.SOFT_CARD ->
                            "柔角卡片：大圆角、柔和层次、暖橙强调、环形进度。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // 只有柔角卡片需要「主题模式」：终端固定深色，磨砂由下面的「磨砂色调」决定
                if (settings.appStyle == AppStyle.SOFT_CARD) {
                    Spacer(Modifier.height(16.dp))
                    MeowDivider()
                    SectionHeader(title = "主题模式")
                    MeowSegmented(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { index -> vm.setTheme(ThemeMode.entries[index]) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // 选了磨砂玻璃，相关设置就在下面展开；换回其它风格则收起
        item {
            AnimatedVisibility(
                visible = isGlass,
                enter = fadeIn(meowTween(MeowMotion.BASE)) +
                    expandVertically(animationSpec = meowTween(MeowMotion.ENTER, MeowMotion.easeOut)),
                exit = fadeOut(meowTween(MeowMotion.FAST)) +
                    shrinkVertically(animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut)),
            ) {
                GlassSettingsSection(state, vm)
            }
        }

        // 强调色对所有风格都有效，常驻
        item {
            AccentCard(settings = settings, vm = vm)
        }
    }
}

/**
 * 磨砂玻璃专属设置。
 *
 * 只在「界面风格 = 磨砂玻璃」时出现，避免不用这套风格的人被壁纸参数打扰。
 */
@Composable
private fun GlassSettingsSection(state: SettingsUiState, vm: SettingsViewModel) {
    val settings = state.settings
    val wallpaper = state.wallpaper

    LaunchedEffect(Unit) { vm.loadWallpaper() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MeowCard {
            SectionHeader(
                title = "每日壁纸",
                subtitle = "微软 Bing 每日一图，按屏幕尺寸自动选图",
            )
            MeowSwitchRow(
                title = "使用每日壁纸",
                description = "关掉后使用中性底色；需要能访问 cn.bing.com",
                checked = settings.wallpaperEnabled,
                onCheckedChange = vm::setWallpaperEnabled,
                icon = Icons.Rounded.Wallpaper,
            )
            if (settings.wallpaperEnabled) {
                Spacer(Modifier.height(14.dp))
                WallpaperPreview(file = wallpaper.file)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = wallpaper.meta?.title?.takeIf { it.isNotBlank() } ?: "正在获取今日壁纸…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!wallpaper.meta?.copyright.isNullOrBlank()) {
                    Text(
                        text = wallpaper.meta.copyright,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                MeowOutlinedButton(
                    text = if (wallpaper.busy) "处理中…" else "下载今日壁纸",
                    onClick = vm::downloadWallpaper,
                    enabled = !wallpaper.busy && wallpaper.file != null,
                    icon = Icons.Rounded.Download,
                )
            }
        }

        MeowCard {
            SectionHeader(title = "显示方式", subtitle = "和 Windows 的铺法一致")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperScale.entries.forEach { scale ->
                    ActionChip(
                        text = scale.label,
                        selected = settings.wallpaperScale == scale,
                        onClick = { vm.setWallpaperScale(scale) },
                    )
                }
            }
            Text(
                text = settings.wallpaperScale.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        MeowCard {
            SectionHeader(title = "可读性", subtitle = "背景越花，越需要压一压")
            MeowSliderRow(
                title = "模糊强度",
                valueText = "${settings.wallpaperBlur} dp",
                value = settings.wallpaperBlur.toFloat(),
                onValueChange = { vm.setWallpaperBlur(it.roundToInt()) },
                valueRange = 0f..48f,
                description = "默认 24dp，比 iOS 的浅磨砂重得多——背诵时背景不该抢注意力",
            )
            MeowDivider()
            MeowSliderRow(
                title = "遮罩浓度",
                valueText = "${(settings.wallpaperScrim * 100).roundToInt()}%",
                value = settings.wallpaperScrim.toFloat(),
                onValueChange = { vm.setWallpaperScrim(it.toDouble()) },
                valueRange = 0f..0.7f,
                description = "亮磨砂压白、暗磨砂压黑，保证文字对比度",
            )
        }

        MeowCard {
            SectionHeader(title = "磨砂色调", subtitle = "亮磨砂更通透，暗磨砂更沉")
            MeowSegmented(
                options = listOf("跟随系统", "亮磨砂", "暗磨砂"),
                selectedIndex = GlassTone.entries.indexOf(settings.glassTone),
                onSelect = { index -> vm.setGlassTone(GlassTone.entries[index]) },
            )
        }
    }
}

/** 强调色：对所有风格生效，所以常驻。 */
@Composable
private fun AccentCard(settings: UserSettings, vm: SettingsViewModel) {
    MeowCard {
        SectionHeader(title = "强调色", subtitle = "按钮与高亮用的主色")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentSwatch(
                color = MaterialTheme.colorScheme.primary,
                label = "默认",
                selected = settings.accentChoice == AccentChoice.SYSTEM,
                onClick = { vm.setAccent(AccentChoice.SYSTEM) },
            )
            MeowAccents.presets.forEach { (choice, color) ->
                AccentSwatch(
                    color = color,
                    label = choice.label,
                    selected = settings.accentChoice == choice,
                    onClick = { vm.setAccent(choice) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自定义色相",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            AccentSwatch(
                color = Color(settings.accentCustomArgb),
                label = "自选",
                selected = settings.accentChoice == AccentChoice.CUSTOM,
                onClick = { vm.setAccent(AccentChoice.CUSTOM) },
            )
        }
        MeowSliderRow(
            title = "色相",
            valueText = "${MeowAccents.hueOf(Color(settings.accentCustomArgb)).roundToInt()}°",
            value = MeowAccents.hueOf(Color(settings.accentCustomArgb)),
            onValueChange = { vm.setAccentHue(it) },
            valueRange = 0f..360f,
            description = "拖动即选中「自选」；饱和度与亮度已固定在好看的范围",
        )
    }
}

// ---------- 日历同步 ----------

@Composable
private fun CalendarPage(
    state: SettingsUiState,
    vm: SettingsViewModel,
    onToggleCalendarSync: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    PageScaffold(SettingsGroup.CALENDAR, onBack) {
        item {
            MeowCard {
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
                            text = "$hours 小时",
                            selected = settings.calendarSyncWindowHours == hours,
                            onClick = { vm.setSyncWindowHours(hours) },
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
                        onClick = vm::syncNow,
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
    }
}

// ---------- 检查更新 ----------

@Composable
private fun UpdatePage(
    state: SettingsUiState,
    vm: SettingsViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val update = state.update
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也不影响页内进度，不必处理 */ }
    PageScaffold(SettingsGroup.UPDATE, onBack) {
        item {
            MeowCard {
                MeowSwitchRow(
                    title = "启动时检查更新",
                    description = "打开后每次启动最多查一次，6 小时内不重复",
                    checked = settings.autoCheckUpdate,
                    onCheckedChange = vm::setAutoCheckUpdate,
                    icon = Icons.Rounded.Refresh,
                )
                MeowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前版本 ${update.currentVersion}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = when (val result = update.result) {
                                null -> "尚未检查"
                                is UpdateCheckResult.UpToDate -> "已是最新版本"
                                is UpdateCheckResult.Failed -> "检查失败：${result.message}"
                                is UpdateCheckResult.Available -> "有可用的新版本"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MeowOutlinedButton(
                        text = if (update.checking) "检查中…" else "检查更新",
                        enabled = !update.checking,
                        onClick = vm::checkForUpdate,
                    )
                }

                val available = update.result as? UpdateCheckResult.Available
                if (available != null) {
                    MeowDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "新版本 ${available.update.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = available.update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 优先应用内下载安装；浏览器只作为兜底
                    when (update.installStatus) {
                        InstallStatus.DOWNLOADING -> {
                            LinearProgressIndicator(
                                progress = { update.installProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "正在下载 ${(update.installProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InstallStatus.READY -> {
                            Text(
                                text = "已下载完成。安装会交给系统界面确认，本应用不会静默安装。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            MeowPrimaryButton(
                                text = "安装",
                                onClick = vm::installDownloaded,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        InstallStatus.FAILED -> {
                            Text(
                                text = "更新失败：${update.installMessage ?: "未知原因"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MeowOutlinedButton(
                                    text = "重试",
                                    onClick = {
                                        val url = available.update.downloadUrl
                                        if (url != null) {
                                            vm.startUpdateDownload(url, available.update.versionName)
                                        } else {
                                            vm.openDownloadInBrowser(available.update.bestUrl)
                                        }
                                    },
                                )
                                MeowTextButton(
                                    text = "在浏览器打开",
                                    onClick = { vm.openDownloadInBrowser(available.update.bestUrl) },
                                )
                            }
                        }
                        InstallStatus.IDLE -> {
                            val downloadUrl = available.update.downloadUrl
                            if (downloadUrl != null) {
                                MeowPrimaryButton(
                                    text = "下载并安装",
                                    onClick = {
                                        // Android 13+ 的通知权限只为「进度通知」，拒绝也不影响下载
                                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                                            notificationPermission.launch(
                                                android.Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        }
                                        vm.startUpdateDownload(downloadUrl, available.update.versionName)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "下载完成后会唤起系统安装器，需要你在系统界面确认",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MeowTextButton(
                                text = "在浏览器打开",
                                onClick = { vm.openDownloadInBrowser(available.update.bestUrl) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- 测试工具 ----------

@Composable
private fun AdvancedPage(state: SettingsUiState, vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val virtualDateTime = state.virtualNow.atZone(ZoneId.systemDefault())

    PageScaffold(SettingsGroup.ADVANCED, onBack) {
        item {
            MeowCard(
                containerColor = LocalMeowColors.current.containerLow,
                borderColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
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
                            onClick = { vm.advanceByDays(days) },
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
                                            vm.jumpTo(
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
                        onClick = vm::resetClock,
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
    }
}

/** 主页与二级页共用：把状态里的值浓缩成一行摘要。 */
internal object SettingsSummary {

    fun rhythm(settings: UserSettings): String =
        "${settings.dailyCapacityMinutes} 分钟 · 新任务 ${settings.maxNewPerDay} 条 · " +
            "保持率 ${(settings.targetRetention * 100).roundToInt()}%"

    fun flexibility(settings: UserSettings): String {
        val parts = mutableListOf(
            if (settings.adaptiveLoadEnabled) "自动减负" else "固定容量",
            if (settings.weekendReliefEnabled) "周末放宽" else "周末照常",
        )
        val experiments = listOf(
            settings.todayStateEnabled,
            settings.minuteCompletionEnabled,
            settings.subjectQuotaEnabled,
        ).count { it }
        if (experiments > 0) parts += "实验 $experiments 项"
        return parts.joinToString(" · ")
    }

    fun algorithm(settings: UserSettings): String =
        "最长 ${if (settings.maxIntervalDays >= 365) "1 年" else "${settings.maxIntervalDays} 天"} · " +
            "反复遗忘 ${settings.leechThreshold} 次"

    fun appearance(settings: UserSettings): String = when (settings.appStyle) {
        AppStyle.TERMINAL -> "终端 · 固定深色"
        AppStyle.GLASS -> {
            val tone = when (settings.glassTone) {
                GlassTone.LIGHT -> "亮"
                GlassTone.DARK -> "暗"
                GlassTone.SYSTEM -> "跟随系统"
            }
            if (settings.wallpaperEnabled) "磨砂 · $tone · 每日壁纸" else "磨砂 · $tone"
        }
        AppStyle.SOFT_CARD -> {
            val theme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> "跟随系统"
                ThemeMode.LIGHT -> "浅色"
                ThemeMode.DARK -> "深色"
            }
            "$theme · 柔角卡片"
        }
    }

    fun calendar(settings: UserSettings): String = if (settings.calendarSyncEnabled) {
        "已开启 · 未来 ${settings.calendarSyncWindowHours} 小时"
    } else {
        "未开启"
    }

    fun update(state: SettingsUiState): String {
        val available = state.update.result as? UpdateCheckResult.Available
        return if (available != null) {
            "有新版本 ${available.update.versionName}"
        } else {
            "当前 ${state.update.currentVersion}"
        }
    }

    fun data(state: SettingsUiState): String =
        "${state.backup.counts.subjects} 学科 · ${state.backup.counts.items} 条任务"

    fun advanced(state: SettingsUiState): String = if (state.clockShifted) {
        "已偏移 " + String.format(
            java.util.Locale.CHINA,
            "%.1f",
            state.clockOffsetMillis / 86_400_000.0,
        ) + " 天"
    } else {
        "与真实时间一致"
    }
}

/** 强调色色点。 */
@Composable
private fun AccentSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 壁纸预览：直接解码本地缓存图，尺寸不大（≤2200px），一张 8MB 左右可接受。 */
@Composable
private fun WallpaperPreview(file: java.io.File?) {
    // 预览也要异步 + 降采样：原图 8MB，直接解码会明显卡一下
    val painter by produceState<BitmapPainter?>(
        initialValue = null,
        file?.path,
        file?.lastModified(),
    ) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            file?.let { target ->
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(target.path, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
                    android.graphics.BitmapFactory.decodeFile(
                        target.path,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
                    )?.asImageBitmap()?.let { BitmapPainter(it) }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val ready = painter
        if (ready != null) {
            Image(
                painter = ready,
                contentDescription = "今日壁纸预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "预览准备中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- 数据与备份 ----------

@Composable
private fun DataPage(state: SettingsUiState, vm: SettingsViewModel, onBack: () -> Unit) {
    val backup = state.backup
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
            "application/octet-stream",
        ),
    ) { uri -> uri?.let { vm.exportBackup(it.toString()) } }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importBackup(it.toString()) } }

    LaunchedEffect(Unit) { vm.loadBackupCounts() }

    PageScaffold(SettingsGroup.DATA, onBack) {
        item {
            MeowCard {
                SectionHeader(
                    title = "备份与恢复",
                    subtitle = "换手机、重装、刷机前，请先导出一份",
                )
                MeowSettingRow(
                    title = "当前数据",
                    value = "${backup.counts.subjects} 学科 · ${backup.counts.items} 条",
                    description = "全部保存在这台设备上，没有云端副本",
                )
                MeowDivider()
                Spacer(Modifier.height(12.dp))
                MeowPrimaryButton(
                    text = if (backup.busy) "处理中…" else "导出 .mmr",
                    onClick = {
                        val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                            .format(java.util.Date())
                        exportLauncher.launch("meowmorize-$date.mmr")
                    },
                    enabled = !backup.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                MeowOutlinedButton(
                    text = "从 .mmr 导入",
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/octet-stream", "application/json", "*/*"),
                        )
                    },
                    enabled = !backup.busy,
                )
            }
        }

        item {
            MeowCard {
                SectionHeader(title = "关于 .mmr", subtitle = "一个带版本号的 JSON 文件")
                Text(
                    text = "导出内容包含：学科、任务（主题 / 背诵内容 / 答案 / 提示 / 截止日期 / " +
                        "优先级 / 用时），以及每条的记忆进度（稳定性、难度、下次到期时间）。\n\n" +
                        "不包含复习日志——它是历史明细，体积大且不影响恢复后的排程。\n\n" +
                        "导入采用**合并**策略：同「学科 + 主题」视为同一条并跳过，不会覆盖你现有数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
