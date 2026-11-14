package com.meowmorize.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.domain.repository.CalendarRepository
import com.meowmorize.core.model.MonthCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val month: YearMonth,
    val calendar: MonthCalendar? = null,
    val selectedDate: LocalDate,
    val today: LocalDate,
) {
    val selectedDay get() = calendar?.day(selectedDate)
    val scheduledDays: Int get() = calendar?.days?.count { it.value.plannedCount > 0 } ?: 0
    val monthTaskCount: Int get() = calendar?.days?.values?.sumOf { it.plannedCount } ?: 0
    val monthMinutes: Int get() = calendar?.days?.values?.sumOf { it.minutes } ?: 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val today = MutableStateFlow(timeProvider.today())
    private val month = MutableStateFlow(YearMonth.from(timeProvider.today()))
    private val selected = MutableStateFlow(timeProvider.today())

    init {
        // 时间快进之后，「今天」与展示月份都要跟着走，否则日历会指在过去
        viewModelScope.launch {
            timeProvider.clockChanges.collect {
                val newToday = timeProvider.today()
                today.value = newToday
                month.value = YearMonth.from(newToday)
                selected.value = newToday
            }
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        month.flatMapLatest { calendarRepository.observeMonth(it) },
        selected,
        today,
    ) { calendar, selectedDate, todayDate ->
        CalendarUiState(
            month = calendar.month,
            calendar = calendar,
            selectedDate = selectedDate,
            today = todayDate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(
            month = YearMonth.from(today.value),
            selectedDate = today.value,
            today = today.value,
        ),
    )

    fun selectDate(date: LocalDate) {
        selected.value = date
        if (YearMonth.from(date) != month.value) {
            month.value = YearMonth.from(date)
        }
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun goToToday() {
        val now = timeProvider.today()
        today.value = now
        month.value = YearMonth.from(now)
        selected.value = now
    }
}
