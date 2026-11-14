package com.meowmorize.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.domain.repository.OcrAvailability
import com.meowmorize.core.domain.repository.OcrError
import com.meowmorize.core.domain.repository.OcrRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.text.DurationEstimator
import com.meowmorize.core.domain.usecase.SaveItemUseCase
import com.meowmorize.core.model.ContentFormat
import com.meowmorize.core.model.ItemDraft
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.ScheduleUpdatePolicy
import com.meowmorize.core.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val subjects: List<Subject> = emptyList(),
    val subjectId: Long? = null,
    val topic: String = "",
    val content: String = "",
    val source: String = "",
    /** 背诵提示：复习页点「显示提示」才展示。 */
    val hint: String = "",
    val contentFormat: ContentFormat = ContentFormat.PLAIN,
    val priority: Priority = Priority.NORMAL,
    /** 编辑已有任务时，是否据此重置排程。 */
    val resetSchedule: Boolean = false,
    val estimatedMinutes: Int = DurationEstimator.MIN,
    /** 输入框里的原始文本，允许用户暂时清空再输入。 */
    val durationInput: String = DurationEstimator.MIN.toString(),
    /** 用户是否手动改过背诵用时。 */
    val durationIsCustom: Boolean = false,
    val revision: Int = 1,
    val createdAtMillis: Long = 0L,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val archived: Boolean = false,
    val showNewSubjectDialog: Boolean = false,
    val newSubjectName: String = "",
    /** OCR（测试功能）状态。 */
    val ocrAvailability: OcrAvailability = OcrAvailability.READY,
    val ocrBusy: Boolean = false,
    val ocrMessage: String? = null,
) {
    val canSave: Boolean
        get() = topic.isNotBlank() && content.isNotBlank() && !isSaving
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studyRepository: StudyRepository,
    private val saveItem: SaveItemUseCase,
    private val durationEstimator: DurationEstimator,
    private val ocrRepository: OcrRepository,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<String>("itemId")?.toLongOrNull() ?: NEW_ITEM
    private val original = MutableStateFlow<MemorizeItem?>(null)

    private val _uiState = MutableStateFlow(
        EditorUiState(
            isEditing = itemId != NEW_ITEM,
            isLoading = itemId != NEW_ITEM,
            ocrAvailability = ocrRepository.availability(),
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 学科列表持续订阅：在弹窗里新建学科后，chip 会立刻出现
            studyRepository.observeSubjects().collect { subjects ->
                _uiState.update { current ->
                    current.copy(
                        subjects = subjects,
                        // 新建任务时默认选中第一个学科；编辑已有任务时不要擅自改动它的学科
                        subjectId = current.subjectId
                            ?: if (current.isEditing) null else subjects.firstOrNull()?.id,
                    )
                }
            }
        }
        if (itemId != NEW_ITEM) {
            viewModelScope.launch { loadItem(itemId) }
        }
    }

    private suspend fun loadItem(id: Long) {
        val item = studyRepository.getItem(id)
        if (item == null) {
            _uiState.update { it.copy(isLoading = false, error = "任务不存在或已被删除") }
            return
        }
        original.value = item
        _uiState.update {
            it.copy(
                isLoading = false,
                subjectId = item.subjectId,
                topic = item.topic,
                content = item.content,
                source = item.source.orEmpty(),
                hint = item.hint.orEmpty(),
                contentFormat = item.contentFormat,
                priority = item.priority,
                estimatedMinutes = item.estimatedMinutes,
                durationInput = item.estimatedMinutes.toString(),
                durationIsCustom = item.durationIsCustom,
                revision = item.revision,
                createdAtMillis = item.createdAt.toEpochMilli(),
            )
        }
    }

    fun setTopic(value: String) {
        _uiState.update { it.copy(topic = value, error = null) }
    }

    fun setContent(value: String) {
        _uiState.update {
            val minutes = if (it.durationIsCustom) it.estimatedMinutes else durationEstimator.estimate(value)
            it.copy(
                content = value,
                // 手动设过时长就不再覆盖，避免用户改一个字时长被重置
                estimatedMinutes = minutes,
                durationInput = if (it.durationIsCustom) it.durationInput else minutes.toString(),
            )
        }
    }

    fun setHint(value: String) {
        _uiState.update { it.copy(hint = value) }
    }

    fun setContentFormat(format: ContentFormat) {
        _uiState.update { it.copy(contentFormat = format) }
    }

    /** 用户点按 +/-：从此以用户数字为准。 */
    fun setDuration(minutes: Int) {
        val clamped = minutes.coerceIn(DurationEstimator.MIN, DurationEstimator.MAX)
        _uiState.update {
            it.copy(
                estimatedMinutes = clamped,
                durationInput = clamped.toString(),
                durationIsCustom = true,
            )
        }
    }

    /** 用户在输入框直接键入分钟数；空值允许，保存时回退到上一次有效值。 */
    fun setDurationInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(3)
        val parsed = digits.toIntOrNull()
        // 超出 3~60 时立刻把输入框也纠正，避免「显示 999、实际存 60」的错位
        val clamped = parsed?.coerceIn(DurationEstimator.MIN, DurationEstimator.MAX)
        _uiState.update {
            it.copy(
                durationInput = clamped?.toString() ?: digits,
                estimatedMinutes = clamped ?: it.estimatedMinutes,
                durationIsCustom = true,
            )
        }
    }

    /** 失焦时把输入框整理成合法值，避免出现空白或超范围。 */
    fun commitDurationInput() {
        _uiState.update {
            val parsed = it.durationInput.toIntOrNull()
                ?.coerceIn(DurationEstimator.MIN, DurationEstimator.MAX)
                ?: it.estimatedMinutes
            it.copy(estimatedMinutes = parsed, durationInput = parsed.toString())
        }
    }

    /** 回到按内容自动估算。 */
    fun useAutoDuration() {
        _uiState.update {
            val minutes = durationEstimator.estimate(it.content)
            it.copy(
                estimatedMinutes = minutes,
                durationInput = minutes.toString(),
                durationIsCustom = false,
            )
        }
    }

    fun setSource(value: String) {
        _uiState.update { it.copy(source = value) }
    }

    fun setSubject(subjectId: Long) {
        _uiState.update { it.copy(subjectId = subjectId) }
    }

    fun setPriority(priority: Priority) {
        _uiState.update { it.copy(priority = priority) }
    }

    /** 关键交互：编辑内容后，由用户决定是否让算法重新排程。 */
    fun setResetSchedule(reset: Boolean) {
        _uiState.update { it.copy(resetSchedule = reset) }
    }

    fun openNewSubjectDialog() {
        _uiState.update { it.copy(showNewSubjectDialog = true, newSubjectName = "") }
    }

    fun dismissNewSubjectDialog() {
        _uiState.update { it.copy(showNewSubjectDialog = false) }
    }

    fun setNewSubjectName(name: String) {
        _uiState.update { it.copy(newSubjectName = name) }
    }

    fun confirmNewSubject() {
        val name = _uiState.value.newSubjectName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            runCatching { studyRepository.ensureSubject(name) }
                .onSuccess { subject ->
                    _uiState.update {
                        it.copy(showNewSubjectDialog = false, newSubjectName = "", subjectId = subject.id)
                    }
                }
                .onFailure { _uiState.update { state -> state.copy(error = "创建学科失败") } }
        }
    }

    // ---------- OCR（测试功能） ----------

    fun recognizeImage(uriString: String) {
        if (_uiState.value.ocrBusy) return
        _uiState.update { it.copy(ocrBusy = true, ocrMessage = null) }
        viewModelScope.launch {
            val result = runCatching { ocrRepository.recognize(uriString) }.getOrNull()
            val text = result?.text
            _uiState.update { state ->
                when {
                    text != null -> state.copy(
                        ocrBusy = false,
                        // 识别结果追加到末尾，避免覆盖用户已经写好的内容
                        content = if (state.content.isBlank()) text else state.content + "\n\n" + text,
                        ocrMessage = "识别完成，请核对错别字（测试功能）",
                    )
                    result?.error == OcrError.NO_TEXT -> state.copy(
                        ocrBusy = false,
                        ocrMessage = "没有识别到文字，试试更清晰、正对镜头的图片",
                    )
                    result?.error == OcrError.UNAVAILABLE -> state.copy(
                        ocrBusy = false,
                        ocrMessage = "这台设备不支持端侧 OCR，请用「粘贴文本」或手动输入",
                    )
                    else -> state.copy(ocrBusy = false, ocrMessage = "识别失败，可换一张图或手动输入")
                }
            }
        }
    }

    fun dismissOcrMessage() {
        _uiState.update { it.copy(ocrMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            runCatching {
                val current = original.value
                if (current == null) {
                    saveItem.create(
                        ItemDraft(
                            subjectId = state.subjectId,
                            topic = state.topic,
                            content = state.content,
                            source = state.source.takeIf { it.isNotBlank() },
                            priority = state.priority,
                            estimatedMinutes = state.estimatedMinutes,
                            durationIsCustom = state.durationIsCustom,
                            hint = state.hint.takeIf { it.isNotBlank() },
                            contentFormat = state.contentFormat,
                        ),
                    )
                } else {
                    saveItem.update(
                        item = current.copy(
                            subjectId = state.subjectId,
                            topic = state.topic.trim(),
                            content = state.content.trim(),
                            source = state.source.trim().takeIf { it.isNotBlank() },
                            priority = state.priority,
                            estimatedMinutes = state.estimatedMinutes,
                            durationIsCustom = state.durationIsCustom,
                            hint = state.hint.trim().takeIf { it.isNotBlank() },
                            contentFormat = state.contentFormat,
                        ),
                        policy = if (state.resetSchedule) {
                            ScheduleUpdatePolicy.RESET_SCHEDULE
                        } else {
                            ScheduleUpdatePolicy.KEEP_SCHEDULE
                        },
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { state2 ->
                    state2.copy(isSaving = false, error = "保存失败：${it.message ?: "未知错误"}")
                }
            }
        }
    }

    fun archive() {
        val current = original.value ?: return
        viewModelScope.launch {
            runCatching { saveItem.archive(current.id) }
                .onSuccess { _uiState.update { it.copy(archived = true) } }
                .onFailure { _uiState.update { state -> state.copy(error = "归档失败") } }
        }
    }

    companion object {
        const val NEW_ITEM = -1L
    }
}
