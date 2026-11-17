package com.putrasurya.kaizen.journal.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** A diary entry per date (as on the web app). */
@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey val date: LocalDate,
    val text: String,
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal WHERE date = :date")
    fun observe(date: LocalDate): Flow<JournalEntity?>

    @Query("SELECT * FROM journal WHERE date = :date")
    suspend fun get(date: LocalDate): JournalEntity?

    /** Newest first, for "Past entries". */
    @Query("SELECT * FROM journal ORDER BY date DESC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Upsert
    suspend fun upsert(entry: JournalEntity)

    @Query("DELETE FROM journal WHERE date = :date")
    suspend fun delete(date: LocalDate)
}

class JournalRepository(private val dao: JournalDao) {

    private val mutex = Mutex()

    fun observe(date: LocalDate): Flow<JournalEntity?> = dao.observe(date)

    fun observeAll(): Flow<List<JournalEntity>> = dao.observeAll()

    suspend fun get(date: LocalDate): String = dao.get(date)?.text ?: ""

    /** Saves as typed; an empty entry is removed. */
    suspend fun save(date: LocalDate, text: String): Unit = mutex.withLock {
        val clipped = text.take(MAX_LENGTH)
        if (clipped.isBlank()) dao.delete(date) else dao.upsert(JournalEntity(date, clipped))
    }

    /** Adds a line at the end of the entry for [date]. */
    suspend fun appendLine(date: LocalDate, line: String): Unit = mutex.withLock {
        val current = dao.get(date)?.text.orEmpty().trimEnd()
        val next = if (current.isEmpty()) line else "$current\n$line"
        dao.upsert(JournalEntity(date, next.take(MAX_LENGTH)))
    }

    companion object {
        const val MAX_LENGTH = 10_000
    }
}
