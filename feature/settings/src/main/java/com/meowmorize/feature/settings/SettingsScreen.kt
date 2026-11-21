package com.meowmorize.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowDivider
import com.meowmorize.core.designsystem.component.MeowSettingRow
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.meowTween
import com.meowmorize.core.designsystem.component.SectionHeader
import kotlinx.coroutines.delay

/**
 * 设置入口。
 *
 * 结构上做了两件事：
 * 1. **分二级页面**：主页只留分组入口与一行摘要，具体参数进各自页面改。
 *    调「今日节奏」时不该被算法参数干扰，反之亦然。
 * 2. **可搜索**：想改哪一项直接搜名字，命中后跳到它所在的分组。
 *
 * 功能一项没少——只是从「一屏到底」换成了「主页摘要 + 二级页面」。
 */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 用字符串保存，rememberSaveable 才能跨进程重建；枚举本身不可直接存取
    var openGroupName by rememberSaveable { mutableStateOf<String?>(null) }
    val openGroup = openGroupName?.let { name -> SettingsGroup.entries.firstOrNull { it.name == name } }

    // 日历权限必须由 Activity 申请；拿到结果后交给 ViewModel 继续同步
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    val onToggleCalendarSync: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> viewModel.disableCalendarSync()
            state.hasCalendarPermission -> viewModel.enableCalendarSync()
            else -> permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            )
        }
    }

    val onOpenUrl: (String) -> Unit = { url ->
        // 只负责把用户送到能下载的地方，安装仍由系统安装器接管
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // 二级页面用返回键回到主页，而不是直接退出应用
    BackHandler(enabled = openGroup != null) { openGroupName = null }

    // 提示条自动消失。放在 Route 而不是主页：二级页里触发的提示同样要自己消失
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2400)
            viewModel.dismissMessage()
        }
    }

    // 过渡规格要在组合里算好：transitionSpec 的 lambda 不是 @Composable
    val pageEnterSpec = meowTween<androidx.compose.ui.unit.IntOffset>(MeowMotion.ENTER, MeowMotion.easeOut)
    val pageExitSpec = meowTween<androidx.compose.ui.unit.IntOffset>(MeowMotion.EXIT, MeowMotion.easeOut)
    val fadeFastSpec = meowTween<Float>(MeowMotion.FAST)
    val fadeBaseSpec = meowTween<Float>(MeowMotion.BASE)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = openGroup,
            transitionSpec = {
                if (targetState != null) {
                    // 进二级页：从右侧轻推入（只移 1/6 屏，不做整屏平移）
                    (slideInHorizontally(pageEnterSpec) { it / 6 } + fadeIn(fadeBaseSpec))
                        .togetherWith(fadeOut(fadeFastSpec))
                } else {
                    // 返回：二级页向右让开，主页淡入并轻微回位
                    (fadeIn(fadeBaseSpec) + slideInHorizontally(pageEnterSpec) { -it / 10 })
                        .togetherWith(
                            slideOutHorizontally(pageExitSpec) { it / 6 } + fadeOut(fadeFastSpec),
                        )
                }
            },
            label = "settingsPage",
        ) { group ->
            if (group == null) {
                SettingsHome(
                    state = state,
                    onOpenGroup = { target -> openGroupName = target.name },
                )
            } else {
                SettingsGroupPage(
                    group = group,
                    state = state,
                    viewModel = viewModel,
                    onBack = { openGroupName = null },
                    onToggleCalendarSync = onToggleCalendarSync,
                    onOpenUrl = onOpenUrl,
                )
            }
        }

        if (state.message != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
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

@Composable
private fun SettingsHome(
    state: SettingsUiState,
    onOpenGroup: (SettingsGroup) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = query.trim()
    val hits = remember(keyword) {
        if (keyword.isBlank()) {
            emptyList()
        } else {
            ALL_HITS.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.group.title.contains(keyword, ignoreCase = true)
            }
        }
    }

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
                    text = "按分组整理，想改哪项直接搜",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SearchBox(
                query = query,
                onQueryChange = { query = it },
            )
        }

        if (keyword.isNotBlank()) {
            item {
                MeowCard {
                    SectionHeader(
                        title = if (hits.isEmpty()) "没有找到「$keyword」" else "搜索结果",
                        subtitle = if (hits.isEmpty()) {
                            "换个说法试试，例如「保持率」「间隔」「日历」"
                        } else {
                            "共 ${hits.size} 项，点一下跳到所在分组"
                        },
                    )
                    hits.forEachIndexed { index, hit ->
                        if (index > 0) MeowDivider()
                        MeowSettingRow(
                            title = hit.title,
                            value = hit.group.title,
                            onClick = { onOpenGroup(hit.group) },
                            showChevron = true,
                        )
                    }
                }
            }
        } else {
            item {
                MeowCard {
                    SectionHeader(title = "学习", subtitle = "每天排多少、怎么排")
                    GroupRow(SettingsGroup.RHYTHM, SettingsSummary.rhythm(state.settings), onOpenGroup)
                    MeowDivider()
                    GroupRow(SettingsGroup.FLEXIBILITY, SettingsSummary.flexibility(state.settings), onOpenGroup)
                    MeowDivider()
                    GroupRow(SettingsGroup.ALGORITHM, SettingsSummary.algorithm(state.settings), onOpenGroup)
                    MeowDivider()
                    GroupRow(SettingsGroup.APPEARANCE, SettingsSummary.appearance(state.settings), onOpenGroup)
                }
            }

            item {
                MeowCard {
                    SectionHeader(title = "系统", subtitle = "和系统、版本打交道的部分")
                    GroupRow(SettingsGroup.CALENDAR, SettingsSummary.calendar(state.settings), onOpenGroup)
                    MeowDivider()
                    GroupRow(SettingsGroup.UPDATE, SettingsSummary.update(state), onOpenGroup)
                    MeowDivider()
                    GroupRow(SettingsGroup.ADVANCED, SettingsSummary.advanced(state), onOpenGroup)
                }
            }

            item {
                // 用主题面（而非透明）保证终端/深色下这一栏不会浮出浅色底
                MeowCard {
                    MeowSettingRow(
                        title = "数据存储",
                        value = "仅本地",
                        description = "所有背诵内容与复习记录都保存在这台设备上，不上传任何服务器",
                    )
                    Text(
                        text = "Meowmorize ${state.update.currentVersion} · Android 10+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: SettingsGroup,
    summary: String,
    onOpenGroup: (SettingsGroup) -> Unit,
) {
    MeowSettingRow(
        title = group.title,
        description = summary,
        onClick = { onOpenGroup(group) },
        showChevron = true,
    )
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    MeowCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索设置项，如「保持率」「间隔」「日历」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清空",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 一条可搜索的设置项：命中后就跳它所在的分组。 */
private data class SettingHit(val title: String, val group: SettingsGroup)

/**
 * 搜索索引。
 *
 * 刻意手写而不是从界面反射生成：这些名字就是用户嘴里的说法，
 * 保持和界面文案一致比自动收集更重要。
 */
private val ALL_HITS: List<SettingHit> = listOf(
    SettingHit("每日可投入时间", SettingsGroup.RHYTHM),
    SettingHit("每日新任务上限", SettingsGroup.RHYTHM),
    SettingHit("目标记忆保持率", SettingsGroup.RHYTHM),
    SettingHit("重新生成今日计划", SettingsGroup.RHYTHM),

    SettingHit("自动减负", SettingsGroup.FLEXIBILITY),
    SettingHit("周末放宽", SettingsGroup.FLEXIBILITY),
    SettingHit("按分钟计算完成率", SettingsGroup.FLEXIBILITY),
    SettingHit("趋势窗口", SettingsGroup.FLEXIBILITY),
    SettingHit("学科配额", SettingsGroup.FLEXIBILITY),
    SettingHit("单科最小份额", SettingsGroup.FLEXIBILITY),
    SettingHit("单科最大份额", SettingsGroup.FLEXIBILITY),

    SettingHit("最长间隔", SettingsGroup.ALGORITHM),
    SettingHit("同日重练上限", SettingsGroup.ALGORITHM),
    SettingHit("难度回归强度", SettingsGroup.ALGORITHM),
    SettingHit("间隔抖动", SettingsGroup.ALGORITHM),
    SettingHit("反复遗忘提示阈值", SettingsGroup.ALGORITHM),
    SettingHit("恢复算法默认值", SettingsGroup.ALGORITHM),

    SettingHit("界面风格", SettingsGroup.APPEARANCE),
    SettingHit("主题模式", SettingsGroup.APPEARANCE),

    SettingHit("日历同步", SettingsGroup.CALENDAR),
    SettingHit("同步窗口", SettingsGroup.CALENDAR),
    SettingHit("立即同步", SettingsGroup.CALENDAR),

    SettingHit("检查更新", SettingsGroup.UPDATE),
    SettingHit("启动时检查更新", SettingsGroup.UPDATE),

    SettingHit("时间快进", SettingsGroup.ADVANCED),
    SettingHit("选择日期与时间", SettingsGroup.ADVANCED),
    SettingHit("回到真实时间", SettingsGroup.ADVANCED),
)
