package com.putrasurya.kaizen.timers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.putrasurya.kaizen.KaizenApp
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.timers.data.TimerRepository
import com.putrasurya.kaizen.timers.domain.FocusDay
import com.putrasurya.kaizen.timers.domain.FocusMath
import com.putrasurya.kaizen.timers.domain.FocusStats
import com.putrasurya.kaizen.timers.domain.TimerMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Everything the history screen draws for one activity. */
data class FocusHistory(
    val title: String,
    val today: LocalDate,
    val planned: Set<Int>,
    val days: Map<LocalDate, FocusDay>,
    val stats: FocusStats,
)

class FocusHistoryViewModel(
    timerId: Long,
    repository: TimerRepository,
    clock: Clock,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<FocusHistory?> = flow { emit(repository.getTimer(timerId)) }
        .flatMapLatest { timer ->
            if (timer == null) return@flatMapLatest emptyFlow()
            val key = FocusMath.key(timer.title)
            combine(repository.observeFocus(key), repository.observeAllTimers()) { rows, timers ->
                val today = Dates.today(clock)
                // Weekdays this activity is planned on: where a timer with its name exists.
                val planned = timers.filter { FocusMath.key(it.title) == key }.map { it.weekday }.toSet()
                val days = rows.associate { it.date to FocusDay(it.seconds, it.rounds) }
                FocusHistory(timer.title, today, planned, days, FocusMath.stats(days, planned, today))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun FocusHistoryScreen(timerId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KaizenApp
    val viewModel: FocusHistoryViewModel = viewModel(
        key = "focus-$timerId",
        factory = viewModelFactory {
            initializer { FocusHistoryViewModel(timerId, app.container.timerRepository, app.container.clock) }
        },
    )
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        val current = history ?: return@Column
        Text(current.title, style = MaterialTheme.typography.headlineSmall)
        Text("Focus history", style = MaterialTheme.typography.labelLarge)

        val stats = current.stats
        StatLine("Streak", if (stats.streak == 1) "1 day" else "${stats.streak} days")
        StatLine(
            "This week",
            "${TimerMath.formatDuration(stats.thisWeekSec)} (last week ${TimerMath.formatDuration(stats.lastWeekSec)})",
        )
        StatLine("Planned days done", "${stats.doneThisWeek} of ${stats.plannedThisWeek} so far this week")

        Heatmap(current)
        Text(
            "Darker = more time focused. Faded squares are days this timer isn't planned " +
                "on, so they never count as missed. A day counts as done with one finished round.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(150.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val WEEKS = 12

/** 12 weeks, one column per week (oldest left), Monday at the top. */
@Composable
private fun Heatmap(history: FocusHistory) {
    val thisMonday = Dates.weekStart(history.today)
    val firstMonday = thisMonday.minusWeeks((WEEKS - 1).toLong())
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        // Day letters on the left.
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DayOfWeek.entries.forEach { day ->
                Box(Modifier.size(width = 28.dp, height = 18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        for (week in 0 until WEEKS) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (dayIndex in 0 until 7) {
                    val date = firstMonday.plusWeeks(week.toLong()).plusDays(dayIndex.toLong())
                    val level = FocusMath.level(history.days[date]?.seconds ?: 0)
                    val planned = date.dayOfWeek.value in history.planned
                    val color: Color = when {
                        date.isAfter(history.today) -> Color.Transparent
                        level == 0 -> empty.copy(alpha = if (planned) 1f else 0.35f)
                        else -> primary.copy(alpha = 0.25f + 0.1875f * level)
                    }
                    val shape = RoundedCornerShape(3.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(shape)
                            .background(color)
                            .then(
                                if (date == history.today) Modifier.border(1.dp, primary, shape)
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}
