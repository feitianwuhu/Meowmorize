package com.meowmorize.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowmorize.core.designsystem.component.ChartLegend
import com.meowmorize.core.designsystem.component.LoadBars
import com.meowmorize.core.designsystem.component.LoadPoint
import com.meowmorize.core.designsystem.component.RetentionCurve
import com.meowmorize.core.designsystem.component.SectionLabel
import com.meowmorize.core.designsystem.component.SpecItem
import com.meowmorize.core.designsystem.component.SpecStrip
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.model.PlanReason
import com.meowmorize.core.model.PlannedTask
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val terminalDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US)
private val terminalWeekdayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.US)

/**
 * 「终端 / 仪表盘」风格的今日页。
 *
 * 与 HyperOS 版的差别不只是配色：
 * - 没有环形进度，改成规格条 + 曲线 + 柱状图三段读数；
 * - 任务用紧凑行（序号 / 主题 / 学科 · 时长）而非圆角卡片；
 * - 装饰只保留分栏线与分区编号，任何线条都对应一条信息边界。
 */
@Composable
fun TerminalTodayContent(
    state: TodayUiState,
    onOpenReview: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onAdjustLoad: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val plan = state.plan

    // 与圆润风格共用的「一次性提示」行为：3 秒后自动收起
    androidx.compose.runtime.LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2_600)
            onDismissMessage()
        }
    }
    val chart = LocalMeowColors.current.chart
    val pending = plan?.remainingTasks.orEmpty()
    val completed = plan?.tasks?.filter { it.completed }.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
    ) {
        item {
            TerminalHeader(
                dateLine = state.date.format(terminalDateFormatter) + "  " +
                    state.date.format(terminalWeekdayFormatter),
                onCreateTask = onCreateTask,
            )
        }

        if (state.message != null) {
            item {
                Text(
                    text = "> ${state.message}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = chart.positive,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        item { Hairline(top = 16.dp) }

        item {
            SpecStrip(
                items = listOf(
                    SpecItem("计划", "${plan?.totalCount ?: 0}", "条"),
                    SpecItem("完成", "${plan?.completedCount ?: 0}", "条", accent = chart.positive),
                    SpecItem(
                        "剩余",
                        "${pending.sumOf { it.minutes }}",
                        "min",
                        accent = if (pending.isEmpty()) chart.positive else null,
                    ),
                    SpecItem("容量", "${plan?.capacityMinutes ?: state.settings.dailyCapacityMinutes}", "min"),
                    SpecItem(
                        "保持率",
                        state.currentRetention?.let { "%.2f".format(Locale.US, it) } ?: "—",
                        "R",
                    ),
                ),
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        item { Hairline() }

        item {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                SectionLabel(
                    index = "01",
                    english = "RETENTION / 未来 8 天",
                    title = "记忆保持",
                )
                Spacer(Modifier.height(10.dp))
                if (state.retentionCurve.any { it > 0f }) {
                    RetentionCurve(
                        points = state.retentionCurve,
                        xLabels = listOf("D0", "D1", "D2", "D3", "D4", "D5", "D6", "D7"),
                        target = state.settings.targetRetention.toFloat(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "目标 ${(state.settings.targetRetention * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = chart.positive,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "低于目标线即进入复习窗口",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = "还没有复习记录。开始背第一条后，这里会显示记忆衰减曲线。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                SectionLabel(
                    index = "02",
                    english = "LOAD / 近 7 天",
                    title = "每日负载",
                    trailing = "单位 min",
                )
                Spacer(Modifier.height(10.dp))
                LoadBars(
                    points = state.loadPoints.map {
                        LoadPoint(
                            label = it.label,
                            plannedMinutes = it.plannedMinutes,
                            completedMinutes = it.completedMinutes,
                            isToday = it.isToday,
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                ChartLegend(
                    items = listOf(
                        "计划" to chart.neutral.copy(alpha = 0.45f),
                        "完成" to chart.seriesA,
                    ),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                SectionLabel(
                    index = "03",
                    english = "QUEUE",
                    title = "待办队列",
                    trailing = "${pending.size} 项 / ${pending.sumOf { it.minutes }} min",
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        if (pending.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "NO PENDING ITEMS",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (plan == null) "正在生成今日计划…" else "今日任务已清空，或还没有添加内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TerminalButton(text = "新增背诵任务", onClick = onCreateTask)
                }
            }
        } else {
            itemsIndexed(pending, key = { _, task -> task.item.id }) { index, task ->
                TerminalTaskRow(
                    index = index + 1,
                    task = task,
                    onClick = { onOpenReview(task.item.id) },
                    onEdit = { onEditTask(task.item.id) },
                )
            }
        }

        if (completed.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    SectionLabel(index = "04", english = "DONE", title = "已完成", trailing = "${completed.size} 项")
                }
            }
            items(completed, key = { "done-${it.item.id}" }) { task ->
                TerminalDoneRow(task)
            }
        }

        if (plan != null && plan.notes.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    SectionLabel(index = "05", english = "LOG", title = "调度日志")
                    Spacer(Modifier.height(8.dp))
                    plan.notes.take(5).forEach { note ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp, end = 8.dp)
                                    .size(4.dp)
                                    .background(MaterialTheme.colorScheme.outline),
                            )
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Hairline()
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CAPACITY ${plan?.capacityMinutes ?: state.settings.dailyCapacityMinutes} MIN",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "调整容量",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onAdjustLoad),
                    )
                }
                Hairline()
            }
        }
    }
}

@Composable
private fun TerminalHeader(dateLine: String, onCreateTask: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MEOWMORIZE / TERMINAL",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新增背诵任务",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onCreateTask),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = dateLine,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "今日负载",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun TerminalTaskRow(
    index: Int,
    task: PlannedTask,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val chart = LocalMeowColors.current.chart
    val subjectColor = task.subject?.let { Color(it.colorArgb) } ?: chart.neutral

    Column(modifier = Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%02d".format(index),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(30.dp)
                    .background(subjectColor),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.item.topic,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(task.subject?.name ?: "未分类")
                        append(" · ")
                        append(task.minutes)
                        append(" min")
                        if (task.needsRepractice) append(" · 再练")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (task.reason == PlanReason.OVERDUE) {
                Text(
                    text = "OVERDUE",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = chart.seriesB,
                    modifier = Modifier
                        .background(chart.seriesB.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = onEdit)
                    .padding(4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TerminalDoneRow(task: PlannedTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "OK",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = LocalMeowColors.current.chart.positive,
            modifier = Modifier.width(26.dp),
        )
        Text(
            text = task.item.topic,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${task.minutes} min",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TerminalButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** 1px 分栏线，终端风格里所有层级都靠它建立。 */
@Composable
private fun Hairline(top: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        modifier = Modifier
            .padding(top = top)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}
