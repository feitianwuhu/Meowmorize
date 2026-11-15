package com.meowmorize.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.srs.FsrsScheduler
import com.meowmorize.core.domain.srs.RetentionResolver
import com.meowmorize.core.domain.usecase.GradeReviewUseCase
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewOutcome
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val item: MemorizeItem? = null,
    val subject: Subject? = null,
    val state: ReviewState? = null,
    val previews: Map<RecallGrade, Int> = emptyMap(),
    /** 是否已展开答案。 */
    val revealed: Boolean = false,
    /** 提示是否正在显示。 */
    val hintVisible: Boolean = false,
    /** 本次复习是否用过提示——用过就不允许再选「轻松」。 */
    val hintUsed: Boolean = false,
    /** 用户已选的熟练度：选中后该按钮变成「下一步」。 */
    val selectedGrade: RecallGrade? = null,
    val isSaving: Boolean = false,
    val outcome: ReviewOutcome? = null,
    val message: String? = null,
    /** 生效中的截止时间（任务优先、学科兜底）。 */
    val deadlineAt: java.time.Instant? = null,
    /** 生效中的目标保持率：临近截止时会被临时提高。 */
    val targetRetention: Double = 0.9,
) {
    /**
     * 评分已提交后锁定按钮。
     *
     * 旧版只锁「保存中」，结果提示出现后仍能继续点档位并再次确认，
     * 于是同一条内容被连续评分，间隔被推得很远——这是真实的 bug，不是用户手滑。
     */
    val isLocked: Boolean get() = isSaving || outcome != null
}

/**
 * 复习页状态机。
 *
 * 两段式评分：
 * 1. 点熟练度 → 立刻展开答案（先看到答案，再确认）；
 * 2. 该熟练度按钮变成「下一步」，可改选，点「下一步」才真正写入日志。
 *
 * 这样做的目的：让用户在看到答案后仍能纠正误判，而不是点错就把间隔算错。
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studyRepository: StudyRepository,
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val gradeReview: GradeReviewUseCase,
    private val scheduler: FsrsScheduler,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<String>("itemId")?.toLongOrNull() ?: -1L

    private data class LocalState(
        val revealed: Boolean = false,
        val hintVisible: Boolean = false,
        val hintUsed: Boolean = false,
        val selectedGrade: RecallGrade? = null,
        val saving: Boolean = false,
        val outcome: ReviewOutcome? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(LocalState())

    /** 当日已评次数：影响同日步进，因此预览必须用它，否则按钮显示与实际落库不一致。 */
    private val todayCount = MutableStateFlow(0)

    // combine 的 lambda 重载最多 5 个流，这里把「界面态 + 当日次数」先合成一个
    private val mutationAndCount = combine(local, todayCount) { mutation, count -> mutation to count }

    val uiState: StateFlow<ReviewUiState> = combine(
        studyRepository.observeItem(itemId),
        studyRepository.observeSubjects(),
        planRepository.observeReviewState(itemId),
        settingsRepository.settings,
        mutationAndCount,
    ) { item, subjects, state, settings, (mutation, reviewedToday) ->
        val subject = item?.subjectId?.let { id -> subjects.firstOrNull { it.id == id } }
        val now = timeProvider.now()
        // 与落库共用同一份截止日期/保持率解析，保证按钮上的天数就是存进去的天数
        val resolved = RetentionResolver.resolve(settings = settings, item = item, subject = subject, now = now)
        ReviewUiState(
            item = item,
            subject = subject,
            state = state,
            deadlineAt = resolved.deadline,
            targetRetention = resolved.targetRetention,
            previews = scheduler.previewIntervals(
                state = state,
                now = now,
                targetRetention = resolved.targetRetention,
                todayReviewCount = reviewedToday,
                config = resolved.config,
            ),
            revealed = mutation.revealed,
            hintVisible = mutation.hintVisible,
            hintUsed = mutation.hintUsed,
            selectedGrade = mutation.selectedGrade,
            isSaving = mutation.saving,
            outcome = mutation.outcome,
            message = mutation.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState(),
    )

    init {
        viewModelScope.launch {
            todayCount.value = planRepository.todayReviewCount(itemId, timeProvider.now())
        }
    }

    /** 主动展开答案（不看评分）。 */
    fun reveal() {
        local.value = local.value.copy(revealed = true)
    }

    /**
     * 点熟练度：展开答案并进入「待确认」状态。
     * 用过提示后「轻松」不再可选择——这是提示的代价，避免提示被当成免费加成。
     */
    fun selectGrade(grade: RecallGrade) {
        // 已出结果就彻底锁定：包括「下一次见」提示出现的这段时间
        if (local.value.saving || local.value.outcome != null) return
        if (grade == RecallGrade.EASY && local.value.hintUsed) return
        local.value = local.value.copy(revealed = true, selectedGrade = grade)
    }

    /** 点「下一步」：提交本次评分。 */
    fun confirm() {
        val grade = local.value.selectedGrade ?: return
        if (local.value.saving || local.value.outcome != null) return
        local.value = local.value.copy(saving = true)
        viewModelScope.launch {
            runCatching { gradeReview(itemId, grade, hintUsed = local.value.hintUsed) }
                .onSuccess { outcome ->
                    local.value = local.value.copy(saving = false, outcome = outcome)
                    // 评完一次，预览要按新的次数重算
                    todayCount.value = planRepository.todayReviewCount(itemId, timeProvider.now())
                }
                .onFailure {
                    local.value = local.value.copy(saving = false, message = "保存失败，请稍后再试")
                }
        }
    }

    fun showHint() {
        val current = local.value
        local.value = current.copy(
            hintVisible = true,
            hintUsed = true,
            // 已经选了「轻松」再点提示，必须重新评估
            selectedGrade = current.selectedGrade?.takeUnless { it == RecallGrade.EASY },
        )
    }

    fun hideHint() {
        local.value = local.value.copy(hintVisible = false)
    }

    fun dismissMessage() {
        local.value = local.value.copy(message = null)
    }
}
