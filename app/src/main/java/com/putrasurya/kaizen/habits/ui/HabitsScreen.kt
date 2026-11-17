package com.putrasurya.kaizen.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.core.ui.WeekdayRow
import com.putrasurya.kaizen.habits.data.HabitWithLogs
import com.putrasurya.kaizen.habits.domain.HabitKind
import com.putrasurya.kaizen.habits.domain.HabitMath
import com.putrasurya.kaizen.habits.domain.TapDirection
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

private sealed interface HabitDialog {
    data object New : HabitDialog
    data class Edit(val item: HabitWithLogs) : HabitDialog
    data class History(val item: HabitWithLogs) : HabitDialog
}

@Composable
fun HabitsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KaizenApp
    val viewModel: HabitsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HabitsViewModel(app.container.habitRepository, app.container.clock) }
        }
    )
    val today by viewModel.today.collectAsStateWithLifecycle()
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val lastTap by viewModel.lastTap.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HabitDialog?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // Auto taps are worked out from the time; refresh twice a minute while visible.
    var now by remember { mutableLongStateOf(viewModel.nowMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = viewModel.nowMs()
            delay(30_000)
        }
    }

    val daily = habits.filter { it.habit.kind == HabitKind.DAILY }
    val routines = habits.filter { it.habit.kind == HabitKind.ROUTINE }
    val weekStart = Dates.weekStart(today)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WeekdayRow(
                    selected = setOf(date.dayOfWeek.value),
                    today = today.dayOfWeek.value,
                    onClick = viewModel::selectWeekday,
                    enabled = { it <= today.dayOfWeek.value },
                )
            }
            if (habits.isEmpty()) {
                item {
                    Text(
                        "No habits yet. Tap + to add a daily habit (pray 5×, 8 glasses of water) " +
                            "or a routine (a +/− balance for resisting an urge).",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            if (daily.isNotEmpty()) {
                item { SectionTitle("Daily") }
                items(daily, key = { it.habit.id }) { item ->
                    DailyCard(
                        item = item,
                        date = date,
                        weekStart = weekStart,
                        onSetCount = { count -> viewModel.setDailyCount(item.habit.id, count) },
                        onEdit = { dialog = HabitDialog.Edit(item) },
                        onHistory = { dialog = HabitDialog.History(item) },
                    )
                }
            }
            if (routines.isNotEmpty()) {
                item { SectionTitle("Routines") }
                items(routines, key = { it.habit.id }) { item ->
                    val canUndo = lastTap?.let { it.habitId == item.habit.id && it.date == date } == true
                    RoutineCard(
                        item = item,
                        date = date,
                        weekStart = weekStart,
                        now = now,
                        zone = viewModel.zone,
                        canUndo = canUndo,
                        onTap = { direction -> viewModel.tap(item.habit.id, direction) },
                        onUndo = viewModel::undo,
                        onEdit = { dialog = HabitDialog.Edit(item) },
                        onHistory = { dialog = HabitDialog.History(item) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { dialog = HabitDialog.New },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add habit") }
    }

    when (val current = dialog) {
        null -> Unit
        HabitDialog.New -> HabitEditDialog(
            habit = null,
            onSave = { kind, name, target, auto ->
                viewModel.add(kind, name, target, auto)
                dialog = null
            },
            onDelete = null,
            onDismiss = { dialog = null },
        )
        is HabitDialog.Edit -> HabitEditDialog(
            habit = current.item.habit,
            onSave = { _, name, target, auto ->
                viewModel.update(current.item.habit.id, name, target, auto)
                dialog = null
            },
            onDelete = {
                viewModel.delete(current.item.habit.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is HabitDialog.History -> HabitHistoryDialog(
            item = current.item,
            today = today,
            now = now,
            zone = viewModel.zone,
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CardHeader(name: String, onEdit: () -> Unit, onHistory: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
        )
        TextButton(onClick = onHistory) { Text("History") }
    }
}

/** Circles to fill: tap one to fill up to it, tap the last filled one to empty it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyCard(
    item: HabitWithLogs,
    date: LocalDate,
    weekStart: LocalDate,
    onSetCount: (Int) -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
) {
    val habit = item.habit
    val count = HabitMath.dailyCount(habit, item.logs[date])
    val week = HabitMath.dailyWeek(habit, weekStart, item.logs)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(habit.name, onEdit, onHistory)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(habit.target) { index ->
                    val filled = index < count
                    val color = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, color, CircleShape)
                            .background(if (filled) color else MaterialTheme.colorScheme.surface)
                            .clickable { onSetCount(if (count == index + 1) index else index + 1) }
                    )
                }
            }
            Text(
                "$count/${habit.target} on this day · ${week.done}/${week.possible} this week",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** − on the left, + on the right, and a marker showing how far the week has drifted. */
@Composable
private fun RoutineCard(
    item: HabitWithLogs,
    date: LocalDate,
    weekStart: LocalDate,
    now: Long,
    zone: ZoneId,
    canUndo: Boolean,
    onTap: (TapDirection) -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
) {
    val habit = item.habit
    val week = HabitMath.routineWeek(habit, weekStart, item.logs, now, zone)
    val day = HabitMath.routineDay(habit, date, item.logs[date], now, zone)
    val live = HabitMath.liveAuto(habit, date, item.logs[date], now, zone)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(habit.name, onEdit, onHistory)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { onTap(TapDirection.MINUS) }) { Text("−") }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(HabitMath.balanceLabel(week.balance), style = MaterialTheme.typography.titleMedium)
                    BalanceBar(week.balance)
                }
                FilledTonalButton(onClick = { onTap(TapDirection.PLUS) }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val taps = listOfNotNull(
                    day.totalPlus.takeIf { it > 0 }?.let { "+$it" },
                    day.totalMinus.takeIf { it > 0 }?.let { "−$it" },
                ).joinToString(" ").ifEmpty { "no taps" }
                Text(
                    "This day: $taps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canUndo) TextButton(onClick = onUndo) { Text("Undo") }
            }
            habit.auto?.let { auto ->
                val sign = if (auto.direction == TapDirection.PLUS) "+1" else "−1"
                val next = live?.next?.let { " · next in ${((it - now + 59_999) / 60_000)} min" } ?: ""
                Text(
                    "Auto $sign every ${auto.everyMinutes} min (max ${auto.maxPerDay}/day)$next",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A track with a marker: centre = clear, left = owed, right = ahead. */
@Composable
private fun BalanceBar(balance: Int) {
    val range = HabitMath.BALANCE_RANGE
    val fraction = (balance.coerceIn(-range, range) + range) / (2f * range)
    val markerColor = when {
        balance < 0 -> MaterialTheme.colorScheme.error
        balance > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val markerSize = 12.dp
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(12.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - markerSize) * fraction)
                .size(markerSize)
                .clip(CircleShape)
                .background(markerColor)
        )
    }
}
