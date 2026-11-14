package com.meowmorize.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.sort.TaskSortKey
import com.meowmorize.core.domain.sort.TaskSorter
import com.meowmorize.core.domain.usecase.SaveItemUseCase
import com.meowmorize.core.model.ItemStatus
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.model.Subject
import com.meowmorize.core.model.TaskSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryItemUi(
    val item: MemorizeItem,
    val subject: Subject?,
    val state: ReviewState?,
)

data class LibraryUiState(
    val items: List<LibraryItemUi> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val query: String = "",
    val selectedSubjectId: Long? = null,
    val showArchived: Boolean = false,
    val sortOrder: TaskSortOrder = TaskSortOrder.RECENT_UPDATED,
    val message: String? = null,
) {
    val activeCount: Int get() = items.count { it.item.status == ItemStatus.ACTIVE }
}

private data class Filters(
    val query: String = "",
    val subjectId: Long? = null,
    val showArchived: Boolean = false,
    val sortOrder: TaskSortOrder = TaskSortOrder.RECENT_UPDATED,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val saveItem: SaveItemUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(Filters())
    private val message = MutableStateFlow<String?>(null)

    init {
        // 排序偏好跨重启保留：进页面先恢复上次的选择
        viewModelScope.launch {
            val saved = settingsRepository.current().librarySortOrder
            filters.value = filters.value.copy(sortOrder = saved)
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        studyRepository.observeAllItems(),
        studyRepository.observeSubjects(),
        planRepository.observeReviewStates(),
        filters,
        message,
    ) { items, subjects, states, filter, msg ->
        val subjectMap = subjects.associateBy { it.id }
        val filtered = items
            .asSequence()
            .filter { if (filter.showArchived) true else it.status == ItemStatus.ACTIVE }
            .filter { filter.subjectId == null || it.subjectId == filter.subjectId }
            .filter { item ->
                val q = filter.query.trim()
                q.isEmpty() ||
                    item.topic.contains(q, ignoreCase = true) ||
                    item.content.contains(q, ignoreCase = true) ||
                    item.source?.contains(q, ignoreCase = true) == true
            }
            .map {
                LibraryItemUi(
                    item = it,
                    subject = it.subjectId?.let(subjectMap::get),
                    state = states[it.id],
                )
            }
            .toList()

        // 排序放在过滤之后、渲染之前，切换排序不会触发数据库重查
        val uiById = filtered.associateBy { it.item.id }
        val sorted = TaskSorter
            .sort(filtered.map { TaskSortKey(it.item, it.state?.dueAt) }, filter.sortOrder)
            .mapNotNull { uiById[it.item.id] }

        LibraryUiState(
            items = sorted,
            subjects = subjects,
            query = filter.query,
            selectedSubjectId = filter.subjectId,
            showArchived = filter.showArchived,
            sortOrder = filter.sortOrder,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun selectSubject(subjectId: Long?) {
        filters.value = filters.value.copy(
            subjectId = if (filters.value.subjectId == subjectId) null else subjectId,
        )
    }

    fun setSortOrder(order: TaskSortOrder) {
        filters.value = filters.value.copy(sortOrder = order)
        viewModelScope.launch {
            settingsRepository.update { it.copy(librarySortOrder = order) }
        }
    }

    fun toggleArchived() {
        filters.value = filters.value.copy(showArchived = !filters.value.showArchived)
    }

    fun archive(itemId: Long) {
        viewModelScope.launch {
            runCatching { saveItem.archive(itemId) }
                .onSuccess { message.value = "已归档，不再出现在每日计划里" }
                .onFailure { message.value = "归档失败，请稍后再试" }
        }
    }

    fun restore(itemId: Long) {
        viewModelScope.launch {
            runCatching { saveItem.restore(itemId) }
                .onSuccess { message.value = "已恢复到任务库" }
                .onFailure { message.value = "恢复失败，请稍后再试" }
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
