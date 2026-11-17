package com.putrasurya.kaizen.timers.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TimerDao {

    @Query("SELECT * FROM timers WHERE weekday = :weekday ORDER BY sortOrder, id")
    fun observeTimers(weekday: Int): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timer_runs")
    fun observeRuns(): Flow<List<TimerRunEntity>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getTimer(id: Long): TimerEntity?

    @Query("SELECT * FROM timer_runs WHERE timerId = :timerId")
    suspend fun getRun(timerId: Long): TimerRunEntity?

    @Query("SELECT * FROM timer_runs WHERE endsAt IS NOT NULL")
    suspend fun getRunning(): List<TimerRunEntity>

    @Insert
    suspend fun insertTimer(timer: TimerEntity): Long

    @Update
    suspend fun updateTimer(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteTimer(id: Long)

    @Upsert
    suspend fun upsertRun(run: TimerRunEntity)

    @Query("DELETE FROM timer_runs WHERE timerId = :timerId")
    suspend fun deleteRun(timerId: Long)

    // --- Reps (Phase 2b). Reps only ever go up: one per finished round. ---

    @Query("SELECT * FROM timer_reps WHERE date = :date")
    fun observeRepsOn(date: LocalDate): Flow<List<TimerRepsEntity>>

    @Query("SELECT timerId, SUM(count) AS total FROM timer_reps GROUP BY timerId")
    fun observeRepTotals(): Flow<List<RepTotal>>

    @Query("SELECT count FROM timer_reps WHERE timerId = :timerId AND date = :date")
    suspend fun getReps(timerId: Long, date: LocalDate): Int?

    @Query("INSERT OR IGNORE INTO timer_reps (timerId, date, count) VALUES (:timerId, :date, 0)")
    suspend fun ensureRepsRow(timerId: Long, date: LocalDate)

    @Query("UPDATE timer_reps SET count = count + 1 WHERE timerId = :timerId AND date = :date")
    suspend fun bumpReps(timerId: Long, date: LocalDate)

    /** +1 rep on [date]. Two steps, because Android 8's SQLite has no "insert or update". */
    @Transaction
    suspend fun addRep(timerId: Long, date: LocalDate) {
        ensureRepsRow(timerId, date)
        bumpReps(timerId, date)
    }

    // --- All timers (for the focus history's planned weekdays and renames) ---

    @Query("SELECT * FROM timers")
    fun observeAllTimers(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers")
    suspend fun getAllTimers(): List<TimerEntity>

    // --- Focus log (Phase 2d). Kept apart from timers, so it outlives them. ---

    @Query("SELECT * FROM focus_log WHERE date = :date")
    fun observeFocusOn(date: LocalDate): Flow<List<FocusLogEntity>>

    @Query("SELECT * FROM focus_log WHERE activityKey = :activityKey")
    fun observeFocus(activityKey: String): Flow<List<FocusLogEntity>>

    @Query("SELECT * FROM focus_log WHERE activityKey = :activityKey")
    suspend fun getFocus(activityKey: String): List<FocusLogEntity>

    @Query(
        "INSERT OR IGNORE INTO focus_log (date, activityKey, title, seconds, rounds) " +
            "VALUES (:date, :activityKey, :title, 0, 0)"
    )
    suspend fun ensureFocusRow(date: LocalDate, activityKey: String, title: String)

    @Query(
        "UPDATE focus_log SET seconds = seconds + :seconds, rounds = rounds + :rounds, " +
            "title = :title WHERE date = :date AND activityKey = :activityKey"
    )
    suspend fun bumpFocus(date: LocalDate, activityKey: String, title: String, seconds: Long, rounds: Int)

    @Query("DELETE FROM focus_log WHERE activityKey = :activityKey")
    suspend fun deleteFocus(activityKey: String)

    @Query("UPDATE focus_log SET title = :title WHERE activityKey = :activityKey")
    suspend fun relabelFocus(activityKey: String, title: String)

    /** Adds focused time and/or finished rounds for an activity on a date. */
    @Transaction
    suspend fun addFocus(date: LocalDate, activityKey: String, title: String, seconds: Long, rounds: Int) {
        if (activityKey.isEmpty() || (seconds <= 0 && rounds <= 0)) return
        ensureFocusRow(date, activityKey, title)
        bumpFocus(date, activityKey, title, seconds, rounds)
    }

    /** Moves an activity's history to a new name, merging into any history it already has. */
    @Transaction
    suspend fun renameFocus(fromKey: String, toKey: String, title: String) {
        val rows = getFocus(fromKey)
        deleteFocus(fromKey)
        rows.forEach { addFocus(it.date, toKey, title, it.seconds, it.rounds) }
        relabelFocus(toKey, title)
    }
}

/** All-time reps of one timer. */
data class RepTotal(val timerId: Long, val total: Int)
