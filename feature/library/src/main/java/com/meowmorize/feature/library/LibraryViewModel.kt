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

/** 待确认的删除请求。删除不可恢复，所以一律先经过确认。 */
sealed interface DeleteRequest {
    data class Item(val id: Long, val topic: String) : DeleteRequest
    data class Subject(val id: Long, val name: String, val itemCount: Int) : DeleteRequest
}

data class LibraryUiState(
    val items: List<LibraryItemUi> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val query: String = "",
    val selectedSubjectId: Long? = null,
    val showArchived: Boolean = false,
    val sortOrder: TaskSortOrder = TaskSortOrder.RECENT_UPDATED,
    val message: String? = null,
    /** 学科管理面板是否打开。 */
    val subjectManagerVisible: Boolean = false,
    /** 各学科下的任务数，管理面板用。 */
    val subjectItemCounts: Map<Long, Int> = emptyMap(),
    /** 待确认的删除。 */
    val pendingDelete: DeleteRequest? = null,
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

    private data class LocalState(
        val subjectManagerVisible: Boolean = false,
        val subjectItemCounts: Map<Long, Int> = emptyMap(),
        val pendingDelete: DeleteRequest? = null,
    )

    private val local = MutableStateFlow(LocalState())

    /** combine 的 lambda 重载最多 5 个流，消息与临时面板态先并成一个。 */
    private val messageAndLocal = combine(message, local) { msg, state -> msg to state }

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
        messageAndLocal,
    ) { items, subjects, states, filter, (msg, localState) ->
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
            subjectManagerVisible = localState.subjectManagerVisible,
            subjectItemCounts = localState.subjectItemCounts,
            pendingDelete = localState.pendingDelete,
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

    // ---------- 学科管理 ----------

    fun openSubjectManager() {
        viewModelScope.launch {
            val counts = studyRepository.subjects().associate { subject ->
                subject.id to studyRepository.subjectItemCount(subject.id)
            }
            local.value = local.value.copy(
                subjectManagerVisible = true,
                subjectItemCounts = counts,
            )
        }
    }

    fun dismissSubjectManager() {
        local.value = local.value.copy(subjectManagerVisible = false)
    }

    fun requestDeleteSubject(subject: Subject) {
        val count = local.value.subjectItemCounts[subject.id]
            ?: itemsOfSubject(subject.id)
        local.value = local.value.copy(
            subjectManagerVisible = false,
            pendingDelete = DeleteRequest.Subject(subject.id, subject.name, count),
        )
    }

    // ---------- 删除 ----------

    fun requestDeleteItem(itemId: Long, topic: String) {
        local.value = local.value.copy(pendingDelete = DeleteRequest.Item(itemId, topic))
    }

    fun cancelDelete() {
        local.value = local.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        when (val request = local.value.pendingDelete) {
            null -> Unit
            is DeleteRequest.Item -> viewModelScope.launch {
                runCatching { saveItem.delete(request.id) }
                    .onSuccess { message.value = "已删除「${request.topic}」" }
                    .onFailure { message.value = "删除失败，请稍后再试" }
                local.value = local.value.copy(pendingDelete = null)
            }
            is DeleteRequest.Subject -> viewModelScope.launch {
                runCatching { saveItem.deleteSubject(request.id) }
                    .onSuccess {
                        message.value = if (request.itemCount > 0) {
                            "已删除学科「${request.name}」，${request.itemCount} 条任务已转为未分类"
                        } else {
                            "已删除学科「${request.name}」"
                        }
                        if (filters.value.subjectId == request.id) {
                            filters.value = filters.value.copy(subjectId = null)
                        }
                    }
                    .onFailure { message.value = "删除失败，请稍后再试" }
                local.value = local.value.copy(pendingDelete = null)
            }
        }
    }

    private fun itemsOfSubject(subjectId: Long): Int =
        uiState.value.items.count { it.item.subjectId == subjectId }

    fun dismissMessage() {
        message.value = null
    }
}
