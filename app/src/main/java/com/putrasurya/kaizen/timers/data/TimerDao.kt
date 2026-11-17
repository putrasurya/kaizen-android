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
}

/** All-time reps of one timer. */
data class RepTotal(val timerId: Long, val total: Int)
