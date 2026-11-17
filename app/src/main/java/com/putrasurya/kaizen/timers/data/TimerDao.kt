package com.putrasurya.kaizen.timers.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
}
