package com.putrasurya.kaizen.todos.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.todos.domain.TodoMath
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

/** A todo on one weekday (1 = Monday … 7 = Sunday); [doneOn] is when it was last ticked. */
@Entity(tableName = "todos", indices = [Index("weekday")])
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekday: Int,
    val text: String,
    val doneOn: LocalDate? = null,
)

@Dao
interface TodoDao {
    // Oldest first: a checklist reads top to bottom in the order it was written.
    @Query("SELECT * FROM todos WHERE weekday = :weekday ORDER BY id")
    fun observeForWeekday(weekday: Int): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE weekday = :weekday ORDER BY id")
    suspend fun getForWeekday(weekday: Int): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): TodoEntity?

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long)
}

class TodoRepository(private val dao: TodoDao, private val clock: Clock) {

    fun observeForWeekday(weekday: Int): Flow<List<TodoEntity>> = dao.observeForWeekday(weekday)

    /** Adds the todo to each of [weekdays]. */
    suspend fun add(text: String, weekdays: Set<Int>) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        weekdays.filter { it in 1..7 }.sorted().forEach { dao.insert(TodoEntity(weekday = it, text = clean)) }
    }

    /** Blank text would leave an invisible item, so it keeps the old text. */
    suspend fun edit(id: Long, text: String) {
        val todo = dao.get(id) ?: return
        val clean = text.trim()
        if (clean.isNotEmpty()) dao.update(todo.copy(text = clean))
    }

    /** Copies start not done; days that already have the same todo are skipped. */
    suspend fun copyTo(id: Long, weekdays: Set<Int>) {
        val source = dao.get(id) ?: return
        for (day in weekdays.filter { it in 1..7 && it != source.weekday }.sorted()) {
            val exists = dao.getForWeekday(day).any { it.text.equals(source.text, ignoreCase = true) }
            if (!exists) dao.insert(TodoEntity(weekday = day, text = source.text))
        }
    }

    suspend fun toggle(id: Long) {
        val todo = dao.get(id) ?: return
        val today = Dates.today(clock)
        val done = TodoMath.isDone(todo.doneOn, today)
        dao.update(todo.copy(doneOn = if (done) null else today))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Today's todos that aren't ticked yet this week, for the spoken reminder. */
    suspend fun unfinishedToday(): List<String> {
        val today = Dates.today(clock)
        return dao.getForWeekday(today.dayOfWeek.value)
            .filter { !TodoMath.isDone(it.doneOn, today) }
            .map { it.text }
    }
}
