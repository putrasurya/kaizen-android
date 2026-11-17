package com.putrasurya.kaizen.timers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.timers.data.TimerRepository
import com.putrasurya.kaizen.timers.data.TimerWithRun
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

class TimersViewModel(
    private val repository: TimerRepository,
    private val clock: Clock,
) : ViewModel() {

    private var lastSeenToday: LocalDate = Dates.today(clock)

    /** 1 = Monday … 7 = Sunday. */
    val today: Int get() = lastSeenToday.dayOfWeek.value

    private val selected = MutableStateFlow(today)
    val weekday: StateFlow<Int> = selected.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val timers: StateFlow<List<TimerWithRun>> = selected
        .flatMapLatest { repository.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun nowMs(): Long = clock.millis()

    fun selectDay(weekday: Int) {
        selected.value = weekday
    }

    /** The active day follows the calendar: after midnight, jump to the new today. */
    fun onResume() {
        val now = Dates.today(clock)
        if (now != lastSeenToday) {
            lastSeenToday = now
            selected.value = today
        }
    }

    fun add(title: String, durationSec: Int, weekdays: Set<Int>) =
        viewModelScope.launch { repository.add(title, durationSec, weekdays) }

    fun edit(id: Long, title: String, durationSec: Int) =
        viewModelScope.launch { repository.edit(id, title, durationSec) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun start(id: Long) = viewModelScope.launch { repository.start(id) }
    fun pause(id: Long) = viewModelScope.launch { repository.pause(id) }
    fun reset(id: Long) = viewModelScope.launch { repository.stop(id) }
}
