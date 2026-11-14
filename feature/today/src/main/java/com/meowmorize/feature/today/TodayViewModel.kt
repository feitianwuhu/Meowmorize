package com.meowmorize.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.CalendarRepository
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.srs.FsrsScheduler
import com.meowmorize.core.domain.usecase.GradeReviewUseCase
import com.meowmorize.core.domain.usecase.PrepareTodayPlanUseCase
import com.meowmorize.core.model.CalendarDay
import com.meowmorize.core.model.DailyPlan
import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/** 近 7 天负载中的一天（终端风格柱状图使用）。 */
data class DayLoadPoint(
    val label: String,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val isToday: Boolean,
)

/** 首页状态。[plan] 为 null 表示还在生成中。 */
data class TodayUiState(
    val date: LocalDate,
    val plan: DailyPlan? = null,
    val settings: UserSettings = UserSettings(),
    val isBusy: Boolean = false,
    val message: String? = null,
    /** 未来 8 天的平均回忆概率，供「终端」风格绘制保持曲线。 */
    val retentionCurve: List<Float> = emptyList(),
    val loadPoints: List<DayLoadPoint> = emptyList(),
) {
    val isLoading: Boolean get() = plan == null

    /** 当前平均可回忆概率，作为规格条里的一个读数。 */
    val currentRetention: Float? get() = retentionCurve.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val prepareTodayPlan: PrepareTodayPlanUseCase,
    private val gradeReview: GradeReviewUseCase,
    private val calendarRepository: CalendarRepository,
    private val scheduler: FsrsScheduler,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val currentDate = MutableStateFlow(timeProvider.today())
    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    /** 记忆保持曲线 + 近 7 天负载：只服务终端风格，但数据本身与风格无关。 */
    private val metricsFlow = currentDate.flatMapLatest { date ->
        combine(
            planRepository.observeReviewStates(),
            calendarRepository.observeDays(date.minusDays(6), date),
        ) { states, days ->
            buildMetrics(date, states, days)
        }
    }

    val uiState: StateFlow<TodayUiState> = currentDate
        .flatMapLatest { date ->
            combine(
                planRepository.observePlan(date),
                settingsRepository.settings,
                message,
                busy,
                metricsFlow,
            ) { plan, settings, msg, isBusy, metrics ->
                TodayUiState(
                    date = date,
                    plan = plan,
                    settings = settings,
                    isBusy = isBusy,
                    message = msg,
                    retentionCurve = metrics.first,
                    loadPoints = metrics.second,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(date = timeProvider.today()),
        )

    init {
        ensurePlan()
        // 时间快进后，「今天」可能已经不是同一天，需要整体刷新
        viewModelScope.launch {
            timeProvider.clockChanges.collect {
                currentDate.value = timeProvider.today()
                ensurePlan(force = true)
            }
        }
    }

    /** 回到前台时调用：既处理跨天，也顺手补齐上次可能失败的生成。 */
    fun onResume() {
        val today = timeProvider.today()
        if (today != currentDate.value) {
            currentDate.value = today
        }
        ensurePlan()
    }

    fun reload() = ensurePlan(force = false)

    /** 用户拖动「今天能做多久」。以用户数字为准重新排程。 */
    fun adjustLoad(capacityMinutes: Int) {
        if (capacityMinutes !in UserSettings.MIN_CAPACITY_MINUTES..UserSettings.MAX_CAPACITY_MINUTES) return
        viewModelScope.launch {
            busy.value = true
            runCatching { prepareTodayPlan(force = true, forcedCapacityMinutes = capacityMinutes) }
                .onSuccess { plan ->
                    message.value = "已重新安排：${plan.capacityMinutes} 分钟 / ${plan.totalCount} 条"
                }
                .onFailure { message.value = "调整失败，请稍后再试" }
            busy.value = false
        }
    }

    /** 恢复算法推荐容量（会重新应用弹性规则）。 */
    fun resetToRecommended() {
        viewModelScope.launch {
            busy.value = true
            runCatching { prepareTodayPlan(force = true) }
                .onSuccess { message.value = "已恢复算法推荐" }
                .onFailure { message.value = "恢复失败，请稍后再试" }
            busy.value = false
        }
    }

    fun grade(itemId: Long, grade: RecallGrade) {
        viewModelScope.launch {
            runCatching { gradeReview(itemId, grade) }
                .onSuccess { outcome ->
                    message.value = if (outcome.nextIntervalDays <= 0) {
                        "没关系，10 分钟后再来一次"
                    } else {
                        "记下了，${outcome.nextIntervalDays} 天后再复习"
                    }
                }
                .onFailure { message.value = "保存失败，请稍后再试" }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /**
     * 两条曲线都来自真实数据：
     * - 保持曲线：对所有「已开始」的任务按 FSRS 公式在未来 0~7 天取样后取均值；
     * - 负载柱：近 7 天的计划分钟数与实际完成分钟数。
     */
    private fun buildMetrics(
        date: LocalDate,
        states: Map<Long, ReviewState>,
        days: List<CalendarDay>,
    ): Pair<List<Float>, List<DayLoadPoint>> {
        val now = timeProvider.now()
        val active = states.values.filter { it.phase != LearningPhase.NEW }
        val curve = (0..7).map { offset ->
            if (active.isEmpty()) {
                0f
            } else {
                val at = now.plus(Duration.ofDays(offset.toLong()))
                active.map { scheduler.retrievability(it, at) }.average().toFloat()
            }
        }

        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        val points = days.map { day ->
            DayLoadPoint(
                label = labels[day.date.dayOfWeek.value - 1],
                plannedMinutes = day.minutes + day.forecastMinutes,
                completedMinutes = day.tasks.filter { it.completed }.sumOf { it.minutes },
                isToday = day.date == date,
            )
        }
        return curve to points
    }

    private fun ensurePlan(force: Boolean = false) {
        viewModelScope.launch {
            busy.value = true
            runCatching { prepareTodayPlan(force = force) }
                .onFailure { message.value = "今日计划生成失败：${it.message ?: "未知错误"}" }
            busy.value = false
        }
    }
}
