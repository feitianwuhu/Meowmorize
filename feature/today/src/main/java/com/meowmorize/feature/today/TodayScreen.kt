package com.meowmorize.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.common.format.Formatters
import com.meowmorize.core.designsystem.component.EmptyState
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.MeowProgressBar
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.designsystem.component.PillTag
import com.meowmorize.core.designsystem.component.ProgressRing
import com.meowmorize.core.designsystem.component.SectionHeader
import com.meowmorize.core.designsystem.component.SubjectDot
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.designsystem.theme.displayName
import com.meowmorize.core.designsystem.theme.tagColors
import com.meowmorize.core.model.DailyPlan
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.TodayState
import com.meowmorize.core.model.PlannedTask
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun TodayRoute(
    onOpenReview: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(false) }

    // 回到前台/跨天后重新对齐今天，避免显示昨天的计划
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TodayScreen(
        state = state,
        onOpenReview = onOpenReview,
        onEditTask = onEditTask,
        onCreateTask = onCreateTask,
        onAdjustLoad = { sheetVisible = true },
        onTodayState = viewModel::setTodayState,
        onDismissMessage = viewModel::dismissMessage,
    )

    if (sheetVisible) {
        val plan = state.plan
        AdjustLoadSheet(
            initialMinutes = plan?.capacityMinutes ?: state.settings.dailyCapacityMinutes,
            recommendedMinutes = state.settings.dailyCapacityMinutes,
            taskCount = plan?.totalCount ?: 0,
            onDismiss = { sheetVisible = false },
            onConfirm = { minutes ->
                sheetVisible = false
                viewModel.adjustLoad(minutes)
            },
            onResetRecommended = {
                sheetVisible = false
                viewModel.resetToRecommended()
            },
        )
    }
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onOpenReview: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onAdjustLoad: () -> Unit,
    onDismissMessage: () -> Unit,
    onTodayState: (TodayState) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 终端风格走完全不同的信息组织方式：规格条 + 曲线 + 柱状图 + 队列
    if (LocalAppStyle.current == AppStyle.TERMINAL) {
        Box(modifier = modifier.fillMaxSize()) {
            if (state.isLoading) {
                LoadingState()
            } else {
                TerminalTodayContent(
                    state = state,
                    onOpenReview = onOpenReview,
                    onEditTask = onEditTask,
                    onCreateTask = onCreateTask,
                    onAdjustLoad = onAdjustLoad,
                    onDismissMessage = onDismissMessage,
                )
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingState()
            state.plan == null || state.plan.isEmpty -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TodayHeader(onCreateTask = onCreateTask, plan = state.plan)
                    EmptyState(
                        icon = Icons.Rounded.SelfImprovement,
                        title = "今天没有安排",
                        description = "没有到期的内容，或者还没有添加背诵任务。",
                        actionLabel = "新增背诵任务",
                        onAction = onCreateTask,
                    )
                }
            }
            else -> {
                val plan = state.plan
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { TodayHeader(onCreateTask = onCreateTask, plan = plan) }
                    if (state.settings.todayStateEnabled) {
                        item {
                            TodayStateRow(
                                active = state.activeTodayState,
                                onSelect = onTodayState,
                            )
                        }
                    }
                    item { HeroCard(plan = plan, onAdjustLoad = onAdjustLoad) }
                    if (plan.notes.isNotEmpty()) {
                        item { NotesCard(notes = plan.notes) }
                    }

                    val pending = plan.remainingTasks
                    item {
                        SectionHeader(
                            title = if (pending.isEmpty()) "今日已完成" else "今日待办",
                            subtitle = if (pending.isEmpty()) {
                                "太棒了，今天的任务都完成了"
                            } else {
                                "还剩 ${pending.size} 条 · 约 ${Formatters.minutesLabel(pending.sumOf { it.minutes })}"
                            },
                        )
                    }

                    items(pending, key = { it.item.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onOpenReview(task.item.id) },
                            onEdit = { onEditTask(task.item.id) },
                        )
                    }

                    val completed = plan.tasks.filter { it.completed }
                    if (completed.isNotEmpty()) {
                        item {
                            SectionHeader(title = "已完成", subtitle = "${completed.size} 条")
                        }
                        items(completed, key = { "done-${it.item.id}" }) { task ->
                            CompletedRow(task)
                        }
                    }
                }
            }
        }

        MessageBanner(
            message = state.message,
            onDismiss = onDismissMessage,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TodayHeader(plan: DailyPlan?, onCreateTask: () -> Unit) {
    val hour = remember { LocalTime.now().hour }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Formatters.dateLabel(java.time.LocalDate.now()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${Formatters.greeting(hour)}，慢慢来",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        MeowIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = "新增背诵任务",
            onClick = onCreateTask,
            containerColor = MaterialTheme.colorScheme.primary,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun HeroCard(plan: DailyPlan, onAdjustLoad: () -> Unit) {
    val progress = plan.progress
    val remainingMinutes = plan.remainingTasks.sumOf { it.minutes }
    val colors = LocalMeowColors.current

    MeowCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = progress.ratio) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${progress.completedCount}/${progress.plannedCount}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "已完成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillTag(
                        text = plan.loadLevel.displayName(),
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    PillTag(text = "${plan.capacityMinutes} 分钟上限")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (remainingMinutes > 0) {
                        "还需约 ${Formatters.minutesLabel(remainingMinutes)}"
                    } else {
                        "今天的任务已经完成"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "进度按你说的来，不必赶",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                MeowProgressBar(
                    progress = progress.ratio,
                    color = colors.success,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MeowTextButton(text = "调整今日任务量", onClick = onAdjustLoad)
                    Spacer(Modifier.weight(1f))
                    if (plan.backlogCount > 0) {
                        Text(
                            text = "${plan.backlogCount} 条已顺延",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesCard(notes: List<String>) {
    val colors = LocalMeowColors.current
    MeowCard(containerColor = colors.containerLow, borderColor = Color.Transparent) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = "为什么今天是这样安排的",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                notes.take(4).forEach { note ->
                    Text(
                        text = "· $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: PlannedTask,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val subjectColor = task.subject?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
    val (tagBg, tagFg) = task.reason.tagColors()

    MeowCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectDot(color = subjectColor)
            Spacer(Modifier.size(6.dp))
            Text(
                text = task.subject?.name ?: "未分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.needsRepractice) {
                PillTag(
                    text = "再练一次",
                    containerColor = Color(0x1FE9A23B),
                    contentColor = Color(0xFFB8791F),
                )
                Spacer(Modifier.size(6.dp))
            }
            PillTag(
                text = task.reason.displayName(),
                containerColor = tagBg,
                contentColor = tagFg,
            )
            Spacer(Modifier.size(4.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = task.item.topic,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.item.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = task.item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillTag(text = task.state.phase.displayName())
            Spacer(Modifier.size(6.dp))
            PillTag(text = "${task.minutes} 分钟", leadingIcon = Icons.Rounded.Timer)
            Spacer(Modifier.weight(1f))
            Text(
                text = "开始背诵",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CompletedRow(task: PlannedTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LocalMeowColors.current.containerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = LocalMeowColors.current.success,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = task.item.topic,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alpha(0.8f),
        )
        Text(
            text = task.state.phase.displayName(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(top = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

/**
 * 「今天状态」三档。刻意做成三个同等大小的胶囊：
 * 这是每天一次的低成本表态，不该长得像设置项。
 */
@Composable
private fun TodayStateRow(active: TodayState, onSelect: (TodayState) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            TodayState.TIRED to "今天有点累",
            TodayState.NORMAL to "正常",
            TodayState.ENERGETIC to "状态不错",
        ).forEach { (state, label) ->
            val selected = active == state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface,
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline,
                        androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(state) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
