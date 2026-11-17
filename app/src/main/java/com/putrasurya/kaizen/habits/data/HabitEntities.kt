package com.putrasurya.kaizen.habits.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.putrasurya.kaizen.habits.domain.AutoTap
import com.putrasurya.kaizen.habits.domain.Habit
import com.putrasurya.kaizen.habits.domain.HabitKind
import com.putrasurya.kaizen.habits.domain.TapDirection
import java.time.LocalDate

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: HabitKind,
    val name: String,
    val target: Int = 1,
    val createdOn: LocalDate,
    val sortOrder: Int = 0,
    // Auto tap (routines); all null means off.
    val autoDirection: TapDirection? = null,
    val autoEveryMinutes: Int? = null,
    val autoMaxPerDay: Int? = null,
    val autoSince: Long? = null,
    val autoFrozenThrough: LocalDate? = null,
) {
    fun toDomain(): Habit {
        val direction = autoDirection
        val auto = if (direction != null && autoEveryMinutes != null && autoMaxPerDay != null && autoSince != null) {
            AutoTap(direction, autoEveryMinutes, autoMaxPerDay, autoSince, autoFrozenThrough)
        } else {
            null
        }
        return Habit(id, kind, name, target, createdOn, auto)
    }

    fun withAuto(auto: AutoTap?): HabitEntity = copy(
        autoDirection = auto?.direction,
        autoEveryMinutes = auto?.everyMinutes,
        autoMaxPerDay = auto?.maxPerDay,
        autoSince = auto?.since,
        autoFrozenThrough = auto?.frozenThrough,
    )
}

/** One habit on one date: slots filled (daily) or taps (routine). */
@Entity(
    tableName = "habit_log",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class HabitLogEntity(
    val habitId: Long,
    val date: LocalDate,
    val count: Int = 0,
    val plus: Int = 0,
    val minus: Int = 0,
    val autoPlus: Int = 0,
    val autoMinus: Int = 0,
)

/** When a manual routine tap happened: auto tap restarts from it, undo removes the latest. */
@Entity(
    tableName = "habit_taps",
    indices = [Index("habitId", "date")],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class HabitTapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val direction: TapDirection,
    val at: Long,
)
