package com.putrasurya.kaizen.habits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("SELECT * FROM habit_log WHERE date BETWEEN :from AND :to")
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_taps WHERE date BETWEEN :from AND :to")
    fun observeTaps(from: LocalDate, to: LocalDate): Flow<List<HabitTapEntity>>

    @Query("SELECT * FROM habit_log WHERE habitId = :habitId AND date BETWEEN :from AND :to")
    suspend fun getLogs(habitId: Long, from: LocalDate, to: LocalDate): List<HabitLogEntity>

    @Query("SELECT * FROM habit_taps WHERE habitId = :habitId AND date BETWEEN :from AND :to")
    suspend fun getTaps(habitId: Long, from: LocalDate, to: LocalDate): List<HabitTapEntity>

    @Query("SELECT * FROM habit_log WHERE habitId = :habitId AND date = :date")
    suspend fun getLog(habitId: Long, date: LocalDate): HabitLogEntity?

    @Upsert
    suspend fun upsertLog(log: HabitLogEntity)

    @Insert
    suspend fun insertTap(tap: HabitTapEntity)

    @Query(
        "SELECT * FROM habit_taps WHERE habitId = :habitId AND date = :date AND direction = :direction " +
            "ORDER BY at DESC, id DESC LIMIT 1"
    )
    /** [direction] is a [TapDirection] name; Room stores enums by name. */
    suspend fun getLatestTap(habitId: Long, date: LocalDate, direction: String): HabitTapEntity?

    @Query("DELETE FROM habit_taps WHERE id = :id")
    suspend fun deleteTap(id: Long)
}
