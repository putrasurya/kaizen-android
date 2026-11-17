package com.putrasurya.kaizen.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.putrasurya.kaizen.habits.data.HabitRepository
import com.putrasurya.kaizen.habits.domain.HabitMath
import com.putrasurya.kaizen.habits.domain.HabitSummary
import com.putrasurya.kaizen.journal.data.JournalEntity
import com.putrasurya.kaizen.journal.data.JournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class JournalViewModel(
    private val journal: JournalRepository,
    habits: HabitRepository,
    private val clock: Clock,
    /** Outlives the screen, so a save that's still waiting is never lost. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val dateFlow = MutableStateFlow(Dates.today(clock))
    val date: StateFlow<LocalDate> = dateFlow.asStateFlow()

    /** What's in the text box. Saved half a second after typing stops. */
    private val textFlow = MutableStateFlow("")
    val text: StateFlow<String> = textFlow.asStateFlow()
    private var pendingSave: Job? = null

    val today: LocalDate get() = Dates.today(clock)

    @OptIn(ExperimentalCoroutinesApi::class)
    val habitSummary: StateFlow<List<HabitSummary>> = dateFlow
        .flatMapLatest { date ->
            habits.observeRange(date, date).map { list ->
                HabitMath.daySummary(
                    habits = list.map { it.habit },
                    date = date,
                    logs = list.mapNotNull { item -> item.logs[date]?.let { item.habit.id to it } }.toMap(),
                    now = clock.millis(),
                    zone = clock.zone,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pastEntries: StateFlow<List<JournalEntity>> = journal.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load(dateFlow.value)
    }

    fun onTextChange(value: String) {
        textFlow.value = value.take(JournalRepository.MAX_LENGTH)
        val date = dateFlow.value
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(500)
            journal.save(date, textFlow.value)
        }
    }

    fun open(date: LocalDate) {
        if (date.isAfter(today) || date == dateFlow.value) return
        flush()
        dateFlow.value = date
        load(date)
    }

    /** Coming back to the screen: pick up lines added elsewhere (e.g. from a todo). */
    fun onResume() {
        if (pendingSave?.isActive != true) load(dateFlow.value)
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    private fun flush() {
        if (pendingSave?.isActive == true) {
            pendingSave?.cancel()
            val date = dateFlow.value
            val value = textFlow.value
            appScope.launch { journal.save(date, value) }
        }
        pendingSave = null
    }

    private fun load(date: LocalDate) {
        viewModelScope.launch { textFlow.value = journal.get(date) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KaizenApp
    val viewModel: JournalViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                JournalViewModel(
                    app.container.journalRepository,
                    app.container.habitRepository,
                    app.container.clock,
                    app.container.applicationScope,
                )
            }
        }
    )
    val date by viewModel.date.collectAsStateWithLifecycle()
    val text by viewModel.text.collectAsStateWithLifecycle()
    val summary by viewModel.habitSummary.collectAsStateWithLifecycle()
    val past by viewModel.pastEntries.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val today = viewModel.today
    val longFormat = DateTimeFormatter.ofPattern(
        if (date.year == today.year) "EEEE, d MMM" else "EEEE, d MMM yyyy"
    )
    val shortFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.open(date.minusDays(1)) }) { Text("‹") }
                Text(
                    if (date == today) "Today · ${date.format(longFormat)}" else date.format(longFormat),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.open(date.plusDays(1)) }, enabled = date.isBefore(today)) { Text("›") }
            }
            if (date != today) {
                TextButton(onClick = { viewModel.open(today) }) { Text("Back to today") }
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text("How did today go?") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (summary.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Habits this day", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary.forEach { habit ->
                            val mark = when {
                                habit.good -> " ✓"
                                habit.bad -> " !"
                                else -> ""
                            }
                            AssistChip(onClick = {}, label = { Text("${habit.name}: ${habit.text}$mark") })
                        }
                    }
                }
            }
        }
        val older = past.filter { it.date != date }
        if (older.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("Past entries", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            items(older, key = { it.date.toEpochDay() }) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.open(entry.date) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(entry.date.format(shortFormat), style = MaterialTheme.typography.labelLarge)
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
