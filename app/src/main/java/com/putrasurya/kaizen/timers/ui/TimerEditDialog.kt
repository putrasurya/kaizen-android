package com.putrasurya.kaizen.timers.ui

import com.putrasurya.kaizen.core.ui.WeekdayRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.putrasurya.kaizen.timers.domain.Pomodoro
import com.putrasurya.kaizen.timers.domain.TimerMath

/**
 * Add a timer (pick one or more days) or edit one (title, length, Pomodoro,
 * or delete). [initialDays] null means editing an existing timer.
 */
@Composable
fun TimerEditDialog(
    initialTitle: String,
    initialMinutes: Int,
    initialPomodoro: Pomodoro?,
    initialDays: Set<Int>?,
    today: Int,
    onSave: (title: String, durationSec: Int, days: Set<Int>, pomodoro: Pomodoro?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val startPomodoro = initialPomodoro ?: Pomodoro()
    var title by remember { mutableStateOf(initialTitle) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    var days by remember { mutableStateOf(initialDays ?: emptySet()) }
    var pomodoroOn by remember { mutableStateOf(initialPomodoro != null) }
    var breakMin by remember { mutableStateOf((startPomodoro.breakSec / 60).toString()) }
    var longBreakMin by remember { mutableStateOf((startPomodoro.longBreakSec / 60).toString()) }
    var longEvery by remember { mutableStateOf(startPomodoro.longEvery.toString()) }
    var autoBreak by remember { mutableStateOf(startPomodoro.autoBreak) }
    var showErrors by remember { mutableStateOf(false) }

    val durationSec = TimerMath.parseMinutes(minutes)
    val pomodoro = if (pomodoroOn) Pomodoro.fromInput(breakMin, longBreakMin, longEvery, autoBreak) else null
    val titleOk = title.isNotBlank()
    val daysOk = initialDays == null || days.isNotEmpty()
    val pomodoroOk = !pomodoroOn || pomodoro != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDays == null) "Edit timer" else "New timer") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = showErrors && !titleOk,
                )
                NumberField(
                    value = minutes,
                    onChange = { minutes = it },
                    label = "Minutes",
                    hint = "${TimerMath.MIN_MINUTES}–${TimerMath.MAX_MINUTES} minutes",
                    isError = showErrors && durationSec == null,
                )
                if (initialDays != null) {
                    Text("Days", style = MaterialTheme.typography.labelLarge)
                    WeekdayRow(
                        selected = days,
                        today = today,
                        onClick = { day -> days = if (day in days) days - day else days + day },
                    )
                    if (showErrors && !daysOk) ErrorText("Pick at least one day")
                }

                SwitchRow("Pomodoro breaks", pomodoroOn) { pomodoroOn = it }
                if (pomodoroOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = breakMin,
                            onChange = { breakMin = it },
                            label = "Break min",
                            isError = showErrors && pomodoro == null,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = longBreakMin,
                            onChange = { longBreakMin = it },
                            label = "Long break",
                            isError = showErrors && pomodoro == null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    NumberField(
                        value = longEvery,
                        onChange = { longEvery = it },
                        label = "Long break every … rounds",
                        hint = "${Pomodoro.MIN_LONG_EVERY}–${Pomodoro.MAX_LONG_EVERY} rounds",
                        isError = showErrors && pomodoro == null,
                    )
                    SwitchRow("Start breaks by themselves", autoBreak) { autoBreak = it }
                    if (showErrors && pomodoro == null) {
                        ErrorText(
                            "Breaks: ${Pomodoro.MIN_BREAK_MIN}–${Pomodoro.MAX_BREAK_MIN} min, " +
                                "long break up to ${Pomodoro.MAX_LONG_BREAK_MIN} min"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (titleOk && durationSec != null && daysOk && pomodoroOk) {
                    onSave(title, durationSec, days, pomodoro)
                } else {
                    showErrors = true
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
        supportingText = hint?.let { { Text(it) } },
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
