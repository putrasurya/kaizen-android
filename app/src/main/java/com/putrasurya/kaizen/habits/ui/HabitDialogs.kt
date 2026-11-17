package com.putrasurya.kaizen.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.putrasurya.kaizen.habits.data.HabitWithLogs
import com.putrasurya.kaizen.habits.domain.AutoTapInput
import com.putrasurya.kaizen.habits.domain.Habit
import com.putrasurya.kaizen.habits.domain.HabitKind
import com.putrasurya.kaizen.habits.domain.HabitMath
import com.putrasurya.kaizen.habits.domain.TapDirection
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/** Add a habit ([habit] null) or edit one. The kind can't change after creating. */
@Composable
fun HabitEditDialog(
    habit: Habit?,
    onSave: (kind: HabitKind, name: String, target: Int, auto: AutoTapInput?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(habit?.kind ?: HabitKind.DAILY) }
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var target by remember { mutableStateOf((habit?.target ?: 5).toString()) }
    // Auto tap: null = off.
    var autoDirection by remember { mutableStateOf(habit?.auto?.direction) }
    var every by remember { mutableStateOf((habit?.auto?.everyMinutes ?: 60).toString()) }
    var maxPerDay by remember { mutableStateOf((habit?.auto?.maxPerDay ?: 8).toString()) }
    var showErrors by remember { mutableStateOf(false) }

    val targetValue = target.toIntOrNull()?.takeIf { it in 1..Habit.MAX_TARGET }
    val auto = autoDirection?.let { AutoTapInput.fromInput(it, every, maxPerDay) }
    val nameOk = name.isNotBlank()
    val targetOk = kind != HabitKind.DAILY || targetValue != null
    val autoOk = kind != HabitKind.ROUTINE || autoDirection == null || auto != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit == null) "New habit" else "Edit habit") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (habit == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == HabitKind.DAILY,
                            onClick = { kind = HabitKind.DAILY },
                            label = { Text("Daily") },
                        )
                        FilterChip(
                            selected = kind == HabitKind.ROUTINE,
                            onClick = { kind = HabitKind.ROUTINE },
                            label = { Text("Routine (+/−)") },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = showErrors && !nameOk,
                )
                if (kind == HabitKind.DAILY) {
                    NumberField(target, { target = it }, "Times per day", "1–${Habit.MAX_TARGET}", showErrors && !targetOk)
                } else {
                    Text("Auto tap", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(autoDirection == null, { autoDirection = null }, { Text("Off") })
                        FilterChip(
                            autoDirection == TapDirection.PLUS,
                            { autoDirection = TapDirection.PLUS },
                            { Text("+1 credit") },
                        )
                        FilterChip(
                            autoDirection == TapDirection.MINUS,
                            { autoDirection = TapDirection.MINUS },
                            { Text("−1 debt") },
                        )
                    }
                    if (autoDirection != null) {
                        Text(
                            if (autoDirection == TapDirection.PLUS) "For something you resist: +1 for every stretch without a slip."
                            else "For something you should do regularly: −1 until you tap + again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        NumberField(
                            every, { every = it }, "Every … minutes",
                            "${AutoTapInput.MIN_EVERY}–${AutoTapInput.MAX_EVERY}", showErrors && auto == null,
                        )
                        NumberField(
                            maxPerDay, { maxPerDay = it }, "Max per day",
                            "${AutoTapInput.MIN_MAX}–${AutoTapInput.MAX_MAX}", showErrors && auto == null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nameOk && targetOk && autoOk) {
                    onSave(kind, name, targetValue ?: 1, if (kind == HabitKind.ROUTINE) auto else null)
                } else {
                    showErrors = true
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, hint: String, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
        supportingText = { Text(hint) },
    )
}

/**
 * The last 8 weeks (from the week the habit was created): done/possible for
 * daily habits, owed/ahead for routines.
 */
@Composable
fun HabitHistoryDialog(
    item: HabitWithLogs,
    today: LocalDate,
    now: Long,
    zone: ZoneId,
    onDismiss: () -> Unit,
) {
    val habit = item.habit
    val weeks = HabitMath.recentWeekStarts(today, since = habit.createdOn).reversed() // newest first
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                weeks.forEach { start ->
                    val (label, fraction, negative) = if (habit.kind == HabitKind.DAILY) {
                        val week = HabitMath.dailyWeek(habit, start, item.logs)
                        Triple("${week.done}/${week.possible}", week.done.toFloat() / week.possible, false)
                    } else {
                        val balance = HabitMath.routineWeek(habit, start, item.logs, now, zone).balance
                        Triple(
                            HabitMath.balanceLabel(balance),
                            minOf(1f, abs(balance).toFloat() / HabitMath.BALANCE_RANGE),
                            balance < 0,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(start.format(format), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                    .background(
                                        if (negative) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .width(80.dp)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
