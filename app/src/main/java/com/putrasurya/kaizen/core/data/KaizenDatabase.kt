package com.putrasurya.kaizen.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.putrasurya.kaizen.habits.data.HabitDao
import com.putrasurya.kaizen.habits.data.HabitEntity
import com.putrasurya.kaizen.habits.data.HabitLogEntity
import com.putrasurya.kaizen.habits.data.HabitTapEntity
import com.putrasurya.kaizen.journal.data.JournalDao
import com.putrasurya.kaizen.journal.data.JournalEntity
import com.putrasurya.kaizen.timers.data.FocusLogEntity
import com.putrasurya.kaizen.timers.data.TimerDao
import com.putrasurya.kaizen.timers.data.TimerEntity
import com.putrasurya.kaizen.timers.data.TimerRepsEntity
import com.putrasurya.kaizen.timers.data.TimerRunEntity
import com.putrasurya.kaizen.todos.data.TodoDao
import com.putrasurya.kaizen.todos.data.TodoEntity

/**
 * The one database for the whole app. Each feature adds its own tables here
 * and its own DAO. Changing a table means bumping [version] and adding a
 * migration (ADR-004).
 *
 * Version history:
 *  1. Timers (Phase 1–2b)
 *  2. Pomodoro run segments, habits, todos, journal, notes, milestones
 */
@Database(
    entities = [
        TimerEntity::class,
        TimerRunEntity::class,
        TimerRepsEntity::class,
        FocusLogEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        HabitTapEntity::class,
        TodoEntity::class,
        JournalEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KaizenDatabase : RoomDatabase() {

    abstract fun timerDao(): TimerDao
    abstract fun habitDao(): HabitDao
    abstract fun todoDao(): TodoDao
    abstract fun journalDao(): JournalDao

    companion object {
        fun create(context: Context): KaizenDatabase =
            Room.databaseBuilder(context, KaizenDatabase::class.java, "kaizen.db")
                // Version 1 only ever held test data and its schema file wasn't
                // committed, so it is replaced once instead of migrated. From
                // version 2 on, every change gets a real migration (ADR-004).
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
    }
}
