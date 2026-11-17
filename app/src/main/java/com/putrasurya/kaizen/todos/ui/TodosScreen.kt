package com.putrasurya.kaizen.todos.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.putrasurya.kaizen.KaizenApp
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.core.ui.PermissionsBanner
import com.putrasurya.kaizen.core.ui.WeekdayRow
import com.putrasurya.kaizen.journal.data.JournalRepository
import com.putrasurya.kaizen.todos.data.TodoEntity
import com.putrasurya.kaizen.todos.data.TodoRepository
import com.putrasurya.kaizen.todos.domain.TodoMath
import com.putrasurya.kaizen.todos.reminder.ReminderSettings
import com.putrasurya.kaizen.todos.reminder.TodoReminder
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

class TodosViewModel(
    private val repository: TodoRepository,
    private val journal: JournalRepository,
    private val clock: Clock,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(Dates.today(clock))
    val today: StateFlow<LocalDate> = todayFlow.asStateFlow()

    private val selected = MutableStateFlow(todayFlow.value.dayOfWeek.value)
    val weekday: StateFlow<Int> = selected.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todos: StateFlow<List<TodoEntity>> = selected
        .flatMapLatest { repository.observeForWeekday(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(weekday: Int) {
        selected.value = weekday
    }

    /** After midnight, jump to the new today. */
    fun onResume() {
        val now = Dates.today(clock)
        if (now != todayFlow.value) {
            todayFlow.value = now
            selected.value = now.dayOfWeek.value
        }
    }

    fun add(text: String) = viewModelScope.launch { repository.add(text, setOf(selected.value)) }
    fun toggle(id: Long) = viewModelScope.launch { repository.toggle(id) }
    fun edit(id: Long, text: String) = viewModelScope.launch { repository.edit(id, text) }
    fun copyTo(id: Long, days: Set<Int>) = viewModelScope.launch { repository.copyTo(id, days) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    /** "Couldn't finish: …" in today's journal. The todo stays: it comes back next week. */
    fun noteInJournal(text: String) = viewModelScope.launch {
        journal.appendLine(Dates.today(clock), "Couldn't finish: ${text.trim()}")
    }
}

@Composable
fun TodosScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KaizenApp
    val viewModel: TodosViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TodosViewModel(app.container.todoRepository, app.container.journalRepository, app.container.clock)
            }
        }
    )
    val today by viewModel.today.collectAsStateWithLifecycle()
    val weekday by viewModel.weekday.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TodoEntity?>(null) }
    var noted by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            WeekdayRow(selected = setOf(weekday), today = today.dayOfWeek.value, onClick = viewModel::selectDay)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    placeholder = { Text("Add a todo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.add(newText)
                        newText = ""
                    }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    viewModel.add(newText)
                    newText = ""
                }) { Text("Add") }
            }
        }
        if (todos.isEmpty()) {
            item {
                Text(
                    "Nothing on this day yet. Todos come back every week; ticks only count for this week.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(todos, key = { it.id }) { todo ->
            val done = TodoMath.isDone(todo.doneOn, today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = { viewModel.toggle(todo.id) })
                Text(
                    todo.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editing = todo },
                )
            }
        }
        item { ReminderCard() }
    }

    editing?.let { todo ->
        TodoEditDialog(
            todo = todo,
            today = today.dayOfWeek.value,
            onSave = { text, copyDays ->
                viewModel.edit(todo.id, text)
                if (copyDays.isNotEmpty()) viewModel.copyTo(todo.id, copyDays)
                editing = null
            },
            onNoteInJournal = {
                viewModel.noteInJournal(todo.text)
                noted = todo.text
                editing = null
            },
            onDelete = {
                viewModel.delete(todo.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    noted?.let { text ->
        AlertDialog(
            onDismissRequest = { noted = null },
            text = { Text("Added to today's journal: \"Couldn't finish: $text\"") },
            confirmButton = { TextButton(onClick = { noted = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun TodoEditDialog(
    todo: TodoEntity,
    today: Int,
    onSave: (text: String, copyDays: Set<Int>) -> Unit,
    onNoteInJournal: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(todo.text) }
    var copyDays by remember { mutableStateOf(emptySet<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Todo") })
                Text("Copy to other days", style = MaterialTheme.typography.labelLarge)
                WeekdayRow(
                    selected = copyDays,
                    today = today,
                    onClick = { day -> copyDays = if (day in copyDays) copyDays - day else copyDays + day },
                    enabled = { it != todo.weekday },
                )
                TextButton(onClick = onNoteInJournal) { Text("Couldn't finish? Note it in today's journal") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, copyDays) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** The evening reminder: on/off and time. Speaks today's unfinished todos. */
@Composable
private fun ReminderCard() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(TodoReminder.settings(context)) }
    fun update(new: ReminderSettings) {
        settings = new
        TodoReminder.save(context, new)
    }

    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evening reminder", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Says today's unfinished todos out loud (quiet on silent or vibrate).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.enabled, onCheckedChange = { update(settings.copy(enabled = it)) })
            }
            if (settings.enabled) {
                Button(onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> update(settings.copy(hour = hour, minute = minute)) },
                        settings.hour,
                        settings.minute,
                        true,
                    ).show()
                }) { Text("At ${settings.label}") }
                PermissionsBanner()
            }
        }
    }
}
