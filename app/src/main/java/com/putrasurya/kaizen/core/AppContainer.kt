package com.putrasurya.kaizen.core

import android.content.Context
import com.putrasurya.kaizen.core.data.KaizenDatabase
import com.putrasurya.kaizen.habits.data.HabitRepository
import com.putrasurya.kaizen.journal.data.JournalRepository
import com.putrasurya.kaizen.milestones.data.MilestoneRepository
import com.putrasurya.kaizen.notes.NoteRepository
import com.putrasurya.kaizen.timers.alarm.TimerAlarms
import com.putrasurya.kaizen.timers.alarm.TimerNotifications
import com.putrasurya.kaizen.timers.data.TimerRepository
import com.putrasurya.kaizen.todos.data.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * Creates the app's shared objects by hand, in one place (ADR-003).
 * Screens get what they need from here instead of creating it themselves,
 * so tests can pass in fakes (for example a fixed [clock]).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** "Now" for the whole app. Tests use Clock.fixed(...) instead. */
    val clock: Clock = Clock.systemDefaultZone()

    /** For work that should outlive a single screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: KaizenDatabase by lazy { KaizenDatabase.create(appContext) }

    val timerRepository: TimerRepository by lazy {
        TimerRepository(
            dao = database.timerDao(),
            alarms = TimerAlarms(appContext),
            notifications = TimerNotifications(appContext),
            clock = clock,
        )
    }

    val habitRepository: HabitRepository by lazy { HabitRepository(database.habitDao(), clock) }

    val todoRepository: TodoRepository by lazy { TodoRepository(database.todoDao(), clock) }

    val journalRepository: JournalRepository by lazy { JournalRepository(database.journalDao()) }

    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }

    val milestoneRepository: MilestoneRepository by lazy { MilestoneRepository(database.milestoneDao()) }
}
