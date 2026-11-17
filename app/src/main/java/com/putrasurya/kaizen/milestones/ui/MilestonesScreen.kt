package com.putrasurya.kaizen.milestones.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.putrasurya.kaizen.milestones.data.MilestoneRepository
import com.putrasurya.kaizen.milestones.domain.Milestone
import com.putrasurya.kaizen.milestones.domain.MilestoneMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MilestonesViewModel(private val repository: MilestoneRepository, private val clock: Clock) : ViewModel() {
    val all: StateFlow<List<Milestone>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun today(): LocalDate = Dates.today(clock)

    fun add(title: String, note: String, achievedOn: LocalDate?, year: Int) =
        viewModelScope.launch { repository.add(title, note, achievedOn, year) }

    fun update(id: Long, title: String, note: String, achievedOn: LocalDate?) =
        viewModelScope.launch { repository.update(id, title, note, achievedOn) }

    fun markAchieved(id: Long) {
        viewModelScope.launch { repository.markAchieved(id, today()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun settle(carry: Boolean) {
        viewModelScope.launch { repository.settleOldGoals(today().year, carry) }
    }
}

/** What the add/edit dialog is showing: null milestone = adding. */
private data class EditState(val milestone: Milestone?)

/** Your own record of the year: what you reached, and the goals still to come. */
@Composable
fun MilestonesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KaizenApp
    val viewModel: MilestonesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MilestonesViewModel(app.container.milestoneRepository, app.container.clock) }
        }
    )
    val all by viewModel.all.collectAsStateWithLifecycle()
    val currentYear = viewModel.today().year
    var year by remember { mutableIntStateOf(currentYear) }
    var editing by remember { mutableStateOf<EditState?>(null) }
    val view = MilestoneMath.yearView(all, year, currentYear)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    TextButton(onClick = onBack) { Text("← Back") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Milestones", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { year-- }) { Text("‹") }
                        Text("$year", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { year++ }, enabled = year < currentYear) { Text("›") }
                    }
                }
            }
            if (view.unsettled.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val n = view.unsettled.size
                            Text(
                                if (n == 1) "1 goal from an earlier year wasn't reached."
                                else "$n goals from earlier years weren't reached.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.settle(carry = true) }) { Text("Bring into $currentYear") }
                                TextButton(onClick = { viewModel.settle(carry = false) }) { Text("Leave them") }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Achieved") }
            if (view.achieved.isEmpty()) item { EmptyLine("Nothing yet in $year. Anything counts: a certification, a first 10K…") }
            items(view.achieved, key = { it.id }) { milestone ->
                MilestoneRow(
                    title = milestone.title,
                    subtitle = milestone.achievedOn?.format(dateFormat).orEmpty(),
                    note = milestone.note,
                    onClick = { editing = EditState(milestone) },
                )
            }

            item { SectionTitle("Goals") }
            if (view.goals.isEmpty()) item { EmptyLine("No goals for $year.") }
            items(view.goals, key = { it.id }) { goal ->
                MilestoneRow(
                    title = goal.title,
                    subtitle = if (goal.kept) "Not reached" else "",
                    note = goal.note,
                    onClick = { editing = EditState(goal) },
                    action = if (goal.kept) null else ({ viewModel.markAchieved(goal.id) }),
                )
            }
        }

        FloatingActionButton(
            onClick = { editing = EditState(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add milestone") }
    }

    editing?.let { state ->
        MilestoneDialog(
            milestone = state.milestone,
            today = viewModel.today(),
            onSave = { title, note, achievedOn ->
                val existing = state.milestone
                if (existing == null) viewModel.add(title, note, achievedOn, year)
                else viewModel.update(existing.id, title, note, achievedOn)
                editing = null
            },
            onDelete = state.milestone?.let { m -> { viewModel.delete(m.id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MilestoneRow(
    title: String,
    subtitle: String,
    note: String,
    onClick: () -> Unit,
    action: (() -> Unit)? = null,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (note.isNotEmpty()) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (action != null) TextButton(onClick = action) { Text("Achieved") }
        }
    }
}

@Composable
private fun MilestoneDialog(
    milestone: Milestone?,
    today: LocalDate,
    onSave: (title: String, note: String, achievedOn: LocalDate?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(milestone?.title ?: "") }
    var note by remember { mutableStateOf(milestone?.note ?: "") }
    var achieved by remember { mutableStateOf(milestone?.achievedOn != null) }
    var date by remember { mutableStateOf(milestone?.achievedOn ?: today) }
    var showErrors by remember { mutableStateOf(false) }
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (milestone == null) "New milestone" else "Edit milestone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MilestoneMath.TITLE_MAX) },
                    label = { Text("What") },
                    isError = showErrors && title.isBlank(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MilestoneMath.NOTE_MAX) },
                    label = { Text("Note (optional)") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Already achieved", Modifier.weight(1f))
                    Switch(checked = achieved, onCheckedChange = { achieved = it })
                }
                if (achieved) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }) { Text("On ${date.format(format)}") }
                } else {
                    Text(
                        "Saved as a goal. Mark it achieved when you reach it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) showErrors = true
                else onSave(title, note, if (achieved) date else null)
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
