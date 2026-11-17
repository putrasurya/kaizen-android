package com.putrasurya.kaizen.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.putrasurya.kaizen.KaizenApp
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.core.ui.WeekdayRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

// Notes: small enough to keep data and screen in one file.

/** A note for one weekday (1–7), or for every day ([EVERY_DAY]). */
@Entity(tableName = "notes", indices = [Index("weekday")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekday: Int,
    val content: String,
) {
    companion object {
        const val EVERY_DAY = 0
    }
}

@Dao
interface NoteDao {
    /** "Every day" notes first, then the weekday's; newest first within each. */
    @Query(
        "SELECT * FROM notes WHERE weekday = :weekday OR weekday = 0 " +
            "ORDER BY CASE WHEN weekday = 0 THEN 0 ELSE 1 END, id DESC"
    )
    fun observeFor(weekday: Int): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

class NoteRepository(private val dao: NoteDao) {
    fun observeFor(weekday: Int): Flow<List<NoteEntity>> = dao.observeFor(weekday)

    suspend fun add(content: String, weekday: Int) {
        val clean = content.trim()
        if (clean.isNotEmpty()) dao.insert(NoteEntity(weekday = weekday, content = clean))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

class NotesViewModel(private val repository: NoteRepository, clock: Clock) : ViewModel() {
    val today: Int = Dates.today(clock).dayOfWeek.value
    private val selected = MutableStateFlow(today)
    val weekday: StateFlow<Int> = selected.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteEntity>> = selected
        .flatMapLatest { repository.observeFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(weekday: Int) {
        selected.value = weekday
    }

    fun add(content: String, everyDay: Boolean) = viewModelScope.launch {
        repository.add(content, if (everyDay) NoteEntity.EVERY_DAY else selected.value)
    }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
}

@Composable
fun NotesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KaizenApp
    val viewModel: NotesViewModel = viewModel(
        factory = viewModelFactory { initializer { NotesViewModel(app.container.noteRepository, app.container.clock) } }
    )
    val weekday by viewModel.weekday.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var everyDay by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                TextButton(onClick = onBack) { Text("← Back") }
                Text("Notes", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Quick notes for a weekday that come back every week on that day, or for every day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { WeekdayRow(selected = setOf(weekday), today = viewModel.today, onClick = viewModel::selectDay) }
        item {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Add a note") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = everyDay, onCheckedChange = { everyDay = it })
                    Text("Every day", Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.add(text, everyDay)
                        text = ""
                    }) { Text("Add") }
                }
            }
        }
        items(notes, key = { it.id }) { note ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (note.weekday == NoteEntity.EVERY_DAY) {
                            Text("Every day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(note.content, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = { viewModel.delete(note.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                    }
                }
            }
        }
    }
}
