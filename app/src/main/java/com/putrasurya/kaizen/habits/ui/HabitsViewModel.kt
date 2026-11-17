package com.putrasurya.kaizen.habits.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.habits.data.HabitRepository
import com.putrasurya.kaizen.habits.data.HabitWithLogs
import com.putrasurya.kaizen.habits.domain.AutoTapInput
import com.putrasurya.kaizen.habits.domain.HabitKind
import com.putrasurya.kaizen.habits.domain.HabitMath
import com.putrasurya.kaizen.habits.domain.TapDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class HabitsViewModel(
    private val repository: HabitRepository,
    private val clock: Clock,
) : ViewModel() {

    /** The last manual routine tap, for its Undo button. */
    data class LastTap(val habitId: Long, val date: LocalDate, val direction: TapDirection)

    private val todayFlow = MutableStateFlow(Dates.today(clock))
    val today: StateFlow<LocalDate> = todayFlow.asStateFlow()

    /** The date being filled in: a day of the current week, never later than today. */
    private val selected = MutableStateFlow(todayFlow.value)
    val selectedDate: StateFlow<LocalDate> = selected.asStateFlow()

    private val lastTapFlow = MutableStateFlow<LastTap?>(null)
    val lastTap: StateFlow<LastTap?> = lastTapFlow.asStateFlow()

    val zone: ZoneId get() = clock.zone

    /** Habits with their logs for the history window (8 weeks) through the end of this week. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val habits: StateFlow<List<HabitWithLogs>> = todayFlow
        .flatMapLatest { today ->
            val weekStart = Dates.weekStart(today)
            repository.observeRange(
                from = weekStart.minusWeeks((HabitMath.HISTORY_WEEKS - 1).toLong()),
                to = weekStart.plusDays(6),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun nowMs(): Long = clock.millis()

    fun selectWeekday(weekday: Int) {
        val date = Dates.dateInSameWeek(todayFlow.value, weekday)
        if (!date.isAfter(todayFlow.value)) selected.value = date
    }

    /** After midnight, jump to the new today. */
    fun onResume() {
        val now = Dates.today(clock)
        if (now != todayFlow.value) {
            todayFlow.value = now
            selected.value = now
            lastTapFlow.value = null
        }
    }

    fun add(kind: HabitKind, name: String, target: Int, auto: AutoTapInput?) =
        viewModelScope.launch { repository.add(kind, name, target, auto) }

    fun update(id: Long, name: String, target: Int, auto: AutoTapInput?) =
        viewModelScope.launch { repository.update(id, name, target, auto) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun setDailyCount(id: Long, count: Int) =
        viewModelScope.launch { repository.setDailyCount(id, selected.value, count) }

    fun tap(id: Long, direction: TapDirection) {
        val date = selected.value
        lastTapFlow.value = LastTap(id, date, direction)
        viewModelScope.launch { repository.tap(id, date, direction) }
    }

    fun undo() {
        val last = lastTapFlow.value ?: return
        lastTapFlow.value = null
        viewModelScope.launch { repository.undoTap(last.habitId, last.date, last.direction) }
    }
}
