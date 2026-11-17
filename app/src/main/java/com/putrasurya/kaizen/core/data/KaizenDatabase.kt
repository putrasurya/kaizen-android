package com.putrasurya.kaizen.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.putrasurya.kaizen.timers.data.FocusLogEntity
import com.putrasurya.kaizen.timers.data.TimerDao
import com.putrasurya.kaizen.timers.data.TimerEntity
import com.putrasurya.kaizen.timers.data.TimerRepsEntity
import com.putrasurya.kaizen.timers.data.TimerRunEntity

/**
 * The one database for the whole app. Each feature adds its own tables here
 * and its own DAO. Changing a table means bumping [version] and adding a
 * migration (ADR-004).
 */
@Database(
    entities = [
        TimerEntity::class,
        TimerRunEntity::class,
        TimerRepsEntity::class,
        FocusLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KaizenDatabase : RoomDatabase() {

    abstract fun timerDao(): TimerDao

    companion object {
        fun create(context: Context): KaizenDatabase =
            Room.databaseBuilder(context, KaizenDatabase::class.java, "kaizen.db").build()
    }
}
