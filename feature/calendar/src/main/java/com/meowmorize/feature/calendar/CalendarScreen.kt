package com.meowmorize.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.common.format.Formatters
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.designsystem.component.PillTag
import com.meowmorize.core.designsystem.component.SectionHeader
import com.meowmorize.core.designsystem.component.SubjectDot
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.model.CalendarDay
import com.meowmorize.core.model.CalendarTask
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)

@Composable
fun CalendarRoute(
    onOpenTask: (Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onGoToToday = viewModel::goToToday,
        onSelectDate = viewModel::selectDate,
        onOpenTask = onOpenTask,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    if (state.calendar == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val selectedDay = state.selectedDay

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "日历",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "本月 ${state.scheduledDays} 天有安排 · 共 ${state.monthTaskCount} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MeowTextButton(text = "回到今天", onClick = onGoToToday)
            }
        }

        item {
            MeowCard(contentPadding = PaddingValues(14.dp)) {
                MonthSwitcher(
                    month = state.month,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                )
                Spacer(Modifier.height(10.dp))
                WeekdayHeader()
                Spacer(Modifier.height(4.dp))
                MonthGrid(
                    month = state.month,
                    calendarDays = state.calendar.days,
                    selectedDate = state.selectedDate,
                    today = state.today,
                    onSelectDate = onSelectDate,
                )
                Spacer(Modifier.height(8.dp))
                LegendRow()
            }
        }

        item {
            SectionHeader(
                title = Formatters.dateLabel(state.selectedDate),
                subtitle = selectedDay?.let { day ->
                    when {
                        day.plannedCount > 0 && day.completedCount == day.plannedCount ->
                            "全部完成 · ${day.minutes} 分钟"
                        day.plannedCount > 0 ->
                            "${day.completedCount}/${day.plannedCount} 已完成 · 约 ${day.minutes} 分钟"
                        day.forecastCount > 0 ->
                            "预计 ${day.forecastCount} 条到期 · 约 ${day.forecastMinutes} 分钟"
                        else -> "这天没有安排"
                    }
                } ?: "这天没有安排",
            )
        }

        if (selectedDay == null || selectedDay.isEmpty) {
            item {
                MeowCard(containerColor = LocalMeowColors.current.containerLow, borderColor = Color.Transparent) {
                    Text(
                        text = if (state.selectedDate.isBefore(state.today)) {
                            "这天没有背书记录。过去的就过去了，不用补。"
                        } else {
                            "这天还没有安排。到期内容会自动出现在当天的计划里。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            selectedDay.tasks.forEach { task ->
                item(key = "plan-${state.selectedDate}-${task.itemId}") {
                    DayTaskRow(task = task, onClick = { onOpenTask(task.itemId) })
                }
            }
            if (selectedDay.forecastCount > 0 && selectedDay.tasks.isEmpty()) {
                item {
                    MeowCard(containerColor = LocalMeowColors.current.containerLow, borderColor = Color.Transparent) {
                        Text(
                            text = "这一天还没生成计划，上面的数字是按记忆曲线推算的。到时候会按当时的状态重新安排。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MeowIconButton(
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = "上个月",
            onClick = onPrevious,
            containerColor = Color.Transparent,
        )
        Text(
            text = month.format(monthFormatter),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        MeowIconButton(
            icon = Icons.Rounded.ChevronRight,
            contentDescription = "下个月",
            onClick = onNext,
            containerColor = Color.Transparent,
        )
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (index >= 5) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    calendarDays: Map<LocalDate, CalendarDay>,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = buildList<LocalDate?> {
        repeat(leading) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date == null) {
                            Spacer(Modifier.aspectRatio(0.92f))
                        } else {
                            DayCell(
                                date = date,
                                day = calendarDays[date],
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                onClick = { onSelectDate(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: CalendarDay?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val colors = LocalMeowColors.current
    val background = when {
        isSelected -> accent.copy(alpha = 0.12f)
        day != null && day.plannedCount > 0 -> colors.containerHigh.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (isToday) Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
            color = when {
                isToday -> accent
                day?.isEmpty != false -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(3.dp))
        when {
            day == null || day.isEmpty -> Spacer(Modifier.height(5.dp))
            day.plannedCount > 0 -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val dots = day.subjectColors.take(3)
                if (dots.isEmpty()) {
                    Dot(color = accent)
                } else {
                    dots.forEach { Dot(color = Color(it)) }
                }
                if (day.plannedCount > 3) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(minOf(day.forecastCount, 3)) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dot(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "已排计划",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
            )
            Text(
                text = "预计到期",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayTaskRow(task: CalendarTask, onClick: () -> Unit) {
    val subjectColor = task.subjectColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
    MeowCard(
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.alpha(if (task.completed) 0.7f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.completed) {
                    LocalMeowColors.current.success
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            SubjectDot(color = subjectColor)
            Spacer(Modifier.size(6.dp))
            Text(
                text = task.subjectName ?: "未分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (task.forecast) {
                PillTag(text = "预计")
            } else {
                PillTag(text = "${task.minutes} 分钟")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = task.topic,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val dueAt = task.dueAt
        if (dueAt != null && !task.forecast) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "计划时间：" + dueAt.atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
