package com.putrasurya.kaizen.timers.ui

import com.putrasurya.kaizen.core.ui.WeekdayRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.putrasurya.kaizen.KaizenApp
import com.putrasurya.kaizen.core.ui.PermissionsBanner
import com.putrasurya.kaizen.timers.alarm.TimerNotifications
import com.putrasurya.kaizen.timers.data.TimerWithRun
import com.putrasurya.kaizen.timers.domain.TimerMath
import kotlinx.coroutines.delay
import java.time.format.TextStyle
import java.util.Locale

/** What the add/edit dialog is showing, if anything. */
private sealed interface DialogState {
    data object New : DialogState
    data class Edit(val item: TimerWithRun) : DialogState
}

@Composable
fun TimersScreen(onOpenHistory: (timerId: Long) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KaizenApp
    val viewModel: TimersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TimersViewModel(app.container.timerRepository, app.container.clock) }
        }
    )
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val weekday = selection.weekday
    val dayLabel = if (selection.isToday) "today"
    else "on " + selection.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // Redraw every second, but only while something is counting down.
    var now by remember { mutableLongStateOf(viewModel.nowMs()) }
    val anyRunning = timers.any { it.isRunning }
    LaunchedEffect(anyRunning) {
        now = viewModel.nowMs()
        while (anyRunning) {
            delay(1_000 - now % 1_000) // wake up right as the next second starts
            now = viewModel.nowMs()
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WeekdayRow(
                    selected = setOf(weekday),
                    today = viewModel.today,
                    onClick = viewModel::selectDay,
                )
            }
            item { PermissionsBanner() }
            if (timers.isEmpty()) {
                item {
                    Text(
                        "No timers on this day yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(timers, key = { it.timer.id }) { item ->
                TimerCard(
                    item = item,
                    now = now,
                    dayLabel = dayLabel,
                    onClick = { dialog = DialogState.Edit(item) },
                    onStart = { viewModel.start(item.timer.id) },
                    onPause = { viewModel.pause(item.timer.id) },
                    onReset = { viewModel.reset(item.timer.id) },
                    onSkipBreak = { viewModel.skipBreak(item.timer.id) },
                    onHistory = { onOpenHistory(item.timer.id) },
                )
            }
        }

        FloatingActionButton(
            onClick = { dialog = DialogState.New },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add timer") }
    }

    when (val current = dialog) {
        null -> Unit
        DialogState.New -> TimerEditDialog(
            initialTitle = "",
            initialMinutes = 25,
            initialPomodoro = null,
            initialDays = setOf(weekday),
            today = viewModel.today,
            onSave = { title, seconds, days, pomodoro ->
                viewModel.add(title, seconds, days, pomodoro)
                dialog = null
            },
            onDelete = null,
            onDismiss = { dialog = null },
        )
        is DialogState.Edit -> TimerEditDialog(
            initialTitle = current.item.timer.title,
            initialMinutes = current.item.timer.durationSec / 60,
            initialPomodoro = current.item.timer.pomodoro(),
            initialDays = null,
            today = viewModel.today,
            onSave = { title, seconds, _, pomodoro ->
                viewModel.edit(current.item.timer.id, title, seconds, pomodoro)
                dialog = null
            },
            onDelete = {
                viewModel.delete(current.item.timer.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun TimerCard(
    item: TimerWithRun,
    now: Long,
    dayLabel: String,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkipBreak: () -> Unit,
    onHistory: () -> Unit,
) {
    val seconds = TimerMath.displaySeconds(item.remainingMs(now))
    val pomodoro = item.timer.pomodoro()
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.timer.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (item.isBreak) {
                    Text(
                        TimerNotifications.phaseLabel(item.phase),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(TimerMath.formatClock(seconds), style = MaterialTheme.typography.displaySmall)

            if (pomodoro != null) {
                // Dots: how far today's rounds are from the long break.
                CycleDots(filled = pomodoro.cyclePosition(item.reps), total = pomodoro.longEvery)
            }
            if (item.repsTotal > 0) {
                // "3 reps today · 1 h 12 min focused · 42 all time", like the web app.
                val focused = TimerMath.formatDuration(item.focusedSec)
                Text(
                    "${TimerMath.repsLabel(item.reps)} $dayLabel · $focused focused · ${item.repsTotal} all time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    item.isRunning -> Button(onClick = onPause) { Text("Pause") }
                    item.isPaused && item.isBreak -> Button(onClick = onStart) { Text("Start break") }
                    item.isPaused -> Button(onClick = onStart) { Text("Resume") }
                    else -> Button(onClick = onStart) { Text("Start") }
                }
                when {
                    item.isBreak -> OutlinedButton(onClick = onSkipBreak) { Text("Skip break") }
                    item.isRunning || item.isPaused -> OutlinedButton(onClick = onReset) { Text("Reset") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onHistory) { Text("History") }
            }
        }
    }
}

@Composable
private fun CycleDots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val color = if (index < filled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
