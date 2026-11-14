package com.meowmorize.feature.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.common.format.Formatters
import com.meowmorize.core.designsystem.component.EmptyState
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.designsystem.component.MeowTextField
import com.meowmorize.core.designsystem.component.PillTag
import com.meowmorize.core.designsystem.component.SubjectDot
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.designsystem.theme.displayName
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ItemStatus
import com.meowmorize.core.model.TaskSortOrder
import java.time.Instant

@Composable
fun LibraryRoute(
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onEditTask = onEditTask,
        onCreateTask = onCreateTask,
        onQueryChange = viewModel::setQuery,
        onSelectSubject = viewModel::selectSubject,
        onSelectSort = viewModel::setSortOrder,
        onToggleArchived = viewModel::toggleArchived,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSubject: (Long?) -> Unit,
    onSelectSort: (TaskSortOrder) -> Unit,
    onToggleArchived: () -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Instant.now()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "任务库",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${state.activeCount} 条在学 · ${state.items.size} 条当前显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MeowIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "新增任务",
                    onClick = onCreateTask,
                    containerColor = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        item {
            MeowTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "搜索",
                placeholder = "主题、内容或来源",
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        text = "全部",
                        selected = state.selectedSubjectId == null,
                        onClick = { onSelectSubject(null) },
                    )
                }
                items(state.subjects, key = { it.id }) { subject ->
                    FilterChip(
                        text = subject.name,
                        selected = state.selectedSubjectId == subject.id,
                        color = Color(subject.colorArgb),
                        onClick = { onSelectSubject(subject.id) },
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    text = "排序",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TaskSortOrder.entries.toList(), key = { it.name }) { order ->
                        FilterChip(
                            text = order.displayName(),
                            selected = state.sortOrder == order,
                            color = MaterialTheme.colorScheme.secondary,
                            onClick = { onSelectSort(order) },
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.showArchived) "含已归档" else "仅显示在学",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MeowTextButton(
                    text = if (state.showArchived) "隐藏归档" else "显示归档",
                    onClick = onToggleArchived,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Inventory2,
                    title = if (state.query.isBlank()) "这里还没有内容" else "没有匹配的内容",
                    description = if (state.query.isBlank()) {
                        "添加第一条背诵任务，之后每天由算法安排复习。"
                    } else {
                        "换个关键词，或者清除筛选条件试试。"
                    },
                    actionLabel = if (state.query.isBlank()) "新增背诵任务" else null,
                    onAction = if (state.query.isBlank()) onCreateTask else null,
                )
            }
        } else {
            items(state.items, key = { it.item.id }) { ui ->
                LibraryItemCard(
                    ui = ui,
                    now = now,
                    onClick = { onEditTask(ui.item.id) },
                    onArchive = { onArchive(ui.item.id) },
                    onRestore = { onRestore(ui.item.id) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val container = if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
    val content = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (LocalAppStyle.current == AppStyle.TERMINAL) {
        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    } else {
        CircleShape
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(container)
            .border(1.dp, if (selected) color.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun LibraryItemCard(
    ui: LibraryItemUi,
    now: Instant,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    val archived = ui.item.status == ItemStatus.ARCHIVED
    val subjectColor = ui.subject?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline

    MeowCard(
        onClick = onClick,
        modifier = Modifier.alpha(if (archived) 0.65f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectDot(color = subjectColor)
            Spacer(Modifier.size(6.dp))
            Text(
                text = ui.subject?.name ?: "未分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (archived) {
                PillTag(text = "已归档")
            } else {
                PillTag(text = ui.state?.phase?.displayName() ?: "新学")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = ui.item.topic,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = ui.item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dueLabel = ui.state?.let { Formatters.relativeToNow(it.dueAt, now) } ?: "未开始"
            PillTag(text = "下次：$dueLabel")
            Spacer(Modifier.size(6.dp))
            PillTag(text = "${ui.item.estimatedMinutes} 分钟")
            Spacer(Modifier.weight(1f))
            if (archived) {
                MeowTextButton(text = "恢复", onClick = onRestore)
            } else {
                MeowTextButton(text = "归档", onClick = onArchive, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
