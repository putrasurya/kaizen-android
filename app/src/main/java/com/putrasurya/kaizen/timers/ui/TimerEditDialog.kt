package com.putrasurya.kaizen.timers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.putrasurya.kaizen.timers.domain.TimerMath

/**
 * Add a timer (pick one or more days) or edit one (title and length, or delete).
 * [initialDays] null means editing an existing timer.
 */
@Composable
fun TimerEditDialog(
    initialTitle: String,
    initialMinutes: Int,
    initialDays: Set<Int>?,
    today: Int,
    onSave: (title: String, durationSec: Int, days: Set<Int>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var minutes by remember { mutableStateOf(initialMinutes.toString()) }
    var days by remember { mutableStateOf(initialDays ?: emptySet()) }
    var showErrors by remember { mutableStateOf(false) }

    val durationSec = TimerMath.parseMinutes(minutes)
    val titleOk = title.isNotBlank()
    val daysOk = initialDays == null || days.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDays == null) "Edit timer" else "New timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = showErrors && !titleOk,
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = showErrors && durationSec == null,
                    supportingText = {
                        Text("${TimerMath.MIN_MINUTES}–${TimerMath.MAX_MINUTES} minutes")
                    },
                )
                if (initialDays != null) {
                    Text("Days", style = MaterialTheme.typography.labelLarge)
                    WeekdayRow(
                        selected = days,
                        today = today,
                        onClick = { day -> days = if (day in days) days - day else days + day },
                    )
                    if (showErrors && !daysOk) {
                        Text(
                            "Pick at least one day",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (titleOk && durationSec != null && daysOk) onSave(title, durationSec, days)
                else showErrors = true
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
