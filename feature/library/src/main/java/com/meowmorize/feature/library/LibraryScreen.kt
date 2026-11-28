package com.meowmorize.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.meowTween
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.meowmorize.core.model.Subject
import com.meowmorize.core.model.TaskSortOrder
import java.time.Instant

@Composable
fun LibraryRoute(
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        selection = selection,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAll,
        onArchiveSelected = viewModel::archiveSelected,
        onRestoreSelected = viewModel::restoreSelected,
        onDeleteSelected = viewModel::deleteSelected,
        onMoveSelected = viewModel::moveSelectedToSubject,
        onSetSelectedPriority = viewModel::setSelectedPriority,
        onEditTask = onEditTask,
        onCreateTask = onCreateTask,
        onQueryChange = viewModel::setQuery,
        onSelectSubject = viewModel::selectSubject,
        onSelectSort = viewModel::setSortOrder,
        onToggleArchived = viewModel::toggleArchived,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onManageSubjects = viewModel::openSubjectManager,
        onDismissSubjectManager = viewModel::dismissSubjectManager,
        onDeleteSubject = viewModel::requestDeleteSubject,
        onDeleteItem = viewModel::requestDeleteItem,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    selection: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onArchiveSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: (Long?) -> Unit,
    onSetSelectedPriority: (com.meowmorize.core.model.Priority) -> Unit,
    onEditTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSubject: (Long?) -> Unit,
    onSelectSort: (TaskSortOrder) -> Unit,
    onToggleArchived: () -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onManageSubjects: () -> Unit,
    onDismissSubjectManager: () -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onDeleteItem: (Long, String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Instant.now()
    val selecting = selection.isNotEmpty()
    var showMoveDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 折叠状态跨重组保留；默认收起，否则筛选区会把列表挤得只剩几行
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    // 进入多选时自动收起筛选面板：两块常驻区叠起来，列表就没地方了
    LaunchedEffect(selecting) {
        if (selecting) filtersExpanded = false
    }

    // 折叠时也要看得出「现在筛了什么」，否则用户会以为列表莫名其妙变少了
    val activeSubjectName = state.subjects
        .firstOrNull { it.id == state.selectedSubjectId }?.name
    val filtersSummary = listOfNotNull(
        activeSubjectName,
        state.sortOrder.takeIf { it != TaskSortOrder.RECENT_UPDATED }?.displayName(),
        "含归档".takeIf { state.showArchived },
    ).joinToString(" · ")

    Column(modifier = modifier.fillMaxSize()) {
        // ===== 常驻区：搜索、筛选、批量操作都不随列表滚动 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selecting) {
                // 多选时用选择头替换标题行：位置不跳、还能少占一行高度
                SelectionHeader(
                    count = selection.size,
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                )
            } else {
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

            MeowTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "搜索",
                placeholder = "主题、内容或来源",
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            )

            FilterToggle(
                expanded = filtersExpanded,
                summary = filtersSummary.ifBlank { null },
                onClick = { filtersExpanded = !filtersExpanded },
            )

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = fadeIn(meowTween(MeowMotion.FAST)) +
                    expandVertically(animationSpec = meowTween(MeowMotion.ENTER, MeowMotion.easeOut)),
                exit = fadeOut(meowTween(MeowMotion.PRESS)) +
                    shrinkVertically(animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        item {
                            FilterChip(
                                text = "管理学科",
                                selected = false,
                                onClick = onManageSubjects,
                            )
                        }
                    }

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
            }

            if (selecting) {
                BatchActionRow(
                    onArchive = onArchiveSelected,
                    onRestore = onRestoreSelected,
                    onMove = { showMoveDialog = true },
                    onPriority = { showPriorityDialog = true },
                    onDelete = { showDeleteDialog = true },
                )
            } else if (state.items.isNotEmpty()) {
                // 提示贴着列表（常驻区最后一行）：它说的就是下面这块内容
                LongPressHint()
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                        selected = ui.item.id in selection,
                        onClick = {
                            if (selecting) onToggleSelection(ui.item.id) else onEditTask(ui.item.id)
                        },
                        onLongClick = { onToggleSelection(ui.item.id) },
                        onArchive = { onArchive(ui.item.id) },
                        onRestore = { onRestore(ui.item.id) },
                        onDelete = { onDeleteItem(ui.item.id, ui.item.topic) },
                        // 删除/排序后让行位移有动画，避免列表「跳一下」
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动到学科") },
            text = {
                Column {
                    Text(
                        text = "未分类",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMoveSelected(null)
                                showMoveDialog = false
                            }
                            .padding(vertical = 12.dp),
                    )
                    state.subjects.forEach { subject ->
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveSelected(subject.id)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("取消") }
            },
        )
    }

    if (showPriorityDialog) {
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text("设置优先级") },
            text = {
                Column {
                    listOf(
                        com.meowmorize.core.model.Priority.LOW to "低",
                        com.meowmorize.core.model.Priority.NORMAL to "普通",
                        com.meowmorize.core.model.Priority.HIGH to "高",
                    ).forEach { (priority, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSelectedPriority(priority)
                                    showPriorityDialog = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPriorityDialog = false }) { Text("取消") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除 ${selection.size} 条任务？") },
            text = {
                Text("内容、记忆进度与复习记录会一并清除，无法恢复。只想暂时收起的话请用「归档」。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSelected()
                        showDeleteDialog = false
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }

    if (state.subjectManagerVisible) {
        AlertDialog(
            onDismissRequest = onDismissSubjectManager,
            title = { Text("管理学科") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.subjects.isEmpty()) {
                        Text(
                            text = "还没有学科。新增任务时可以顺手创建。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.subjects.forEach { subject ->
                        val count = state.subjectItemCounts[subject.id] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectDot(color = Color(subject.colorArgb))
                            Spacer(Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (count > 0) "$count 条任务" else "暂无任务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MeowTextButton(
                                text = "删除",
                                onClick = { onDeleteSubject(subject) },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissSubjectManager) { Text("完成") }
            },
            shape = RoundedCornerShape(26.dp),
        )
    }

    val pending = state.pendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = {
                Text(
                    when (pending) {
                        is DeleteRequest.Item -> "删除这条任务？"
                        is DeleteRequest.Subject -> "删除学科「${pending.name}」？"
                    },
                )
            },
            text = {
                Text(
                    when (pending) {
                        is DeleteRequest.Item ->
                            "内容、背诵记录与复习日志会一起删除，无法恢复。只是暂时不想背的话，用「归档」更合适。"
                        is DeleteRequest.Subject ->
                            if (pending.itemCount > 0) {
                                "该学科下的 ${pending.itemCount} 条任务不会被删除，会变成「未分类」。"
                            } else {
                                "这个学科下没有任务，可以放心删除。"
                            }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("取消") }
            },
            shape = RoundedCornerShape(26.dp),
        )
    }
}

/** 多选时的头部：替换标题行，高度基本不变，列表不会跳。 */
@Composable
private fun SelectionHeader(
    count: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "已选 $count 条",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MeowTextButton(text = "全选", onClick = onSelectAll)
        MeowTextButton(text = "取消", onClick = onClearSelection)
    }
}

/**
 * 批量操作行。
 *
 * 常驻在筛选区下方、列表上方：选了任务就一直看得见，不会因为滑动而消失。
 * 五个按钮在窄屏放不下，所以允许横向滚动，而不是换行把头部撑高。
 */
@Composable
private fun BatchActionRow(
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onMove: () -> Unit,
    onPriority: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MeowOutlinedButton(text = "归档", onClick = onArchive)
        MeowOutlinedButton(text = "恢复", onClick = onRestore)
        MeowOutlinedButton(text = "移动", onClick = onMove)
        MeowOutlinedButton(text = "优先级", onClick = onPriority)
        MeowOutlinedButton(
            text = "删除",
            onClick = onDelete,
            contentColor = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 「筛选与排序」开关。
 *
 * 做成整条：点哪儿都能展开，也留得住右侧的箭头做状态指示；
 * 收起时把当前条件摘要写在中间，避免用户忘了筛选还开着。
 */
@Composable
private fun FilterToggle(
    expanded: Boolean,
    summary: String?,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
        label = "filterChevron",
    )
    // 展开或有筛选条件时高亮：让「列表被筛过」这件事一直可见
    val highlighted = expanded || summary != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (highlighted) accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (highlighted) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "筛选与排序",
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) accent else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "收起筛选" else "展开筛选",
            modifier = Modifier
                .size(18.dp)
                .rotate(chevron),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 长按多选的提示。放在筛选区与列表之间，说的就是下面这块内容。 */
@Composable
private fun LongPressHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "长按任意任务可多选，批量归档、移动或删除",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archived = ui.item.status == ItemStatus.ARCHIVED
    val subjectColor = ui.subject?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline

    MeowCard(
        modifier = modifier.alpha(if (archived) 0.65f else 1f),
        onClick = onClick,
        onLongClick = onLongClick,
        borderColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
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
            MeowTextButton(
                text = "删除",
                onClick = onDelete,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
