package com.putrasurya.kaizen.timers.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.putrasurya.kaizen.timers.domain.Pomodoro
import java.time.LocalDate

// Tables for the timers feature, as designed in ADR-004 and ADR-005.
// The screens and the DAO that use them arrive in Phase 2.

/** A timer, belonging to one weekday (1 = Monday … 7 = Sunday). */
@Entity(tableName = "timers", indices = [Index("weekday")])
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val weekday: Int,
    val durationSec: Int,
    val sortOrder: Int = 0,
    // Pomodoro settings; all null means Pomodoro is off.
    val pomodoroBreakSec: Int? = null,
    val pomodoroLongBreakSec: Int? = null,
    val pomodoroLongEvery: Int? = null,
    val pomodoroAutoBreak: Boolean? = null,
) {
    /** Pomodoro settings, or null when Pomodoro is off. */
    fun pomodoro(): Pomodoro? {
        val breakSec = pomodoroBreakSec ?: return null
        return Pomodoro(
            breakSec = breakSec,
            longBreakSec = pomodoroLongBreakSec ?: Pomodoro.DEFAULT_LONG_BREAK_SEC,
            longEvery = pomodoroLongEvery ?: Pomodoro.DEFAULT_LONG_EVERY,
            autoBreak = pomodoroAutoBreak ?: true,
        )
    }

    fun withPomodoro(pomodoro: Pomodoro?): TimerEntity = copy(
        pomodoroBreakSec = pomodoro?.breakSec,
        pomodoroLongBreakSec = pomodoro?.longBreakSec,
        pomodoroLongEvery = pomodoro?.longEvery,
        pomodoroAutoBreak = pomodoro?.autoBreak,
    )
}

enum class TimerPhase { FOCUS, BREAK, LONG_BREAK }

/**
 * A timer that has been started and not finished or stopped yet.
 * Running: [endsAt] is set. Paused: [remainingMs] is set. (ADR-005)
 * [segmentStartedAt]: when the current running stretch began, so the focus
 * log can record time focused even when a round is paused or reset.
 */
@Entity(
    tableName = "timer_runs",
    foreignKeys = [ForeignKey(
        entity = TimerEntity::class,
        parentColumns = ["id"],
        childColumns = ["timerId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class TimerRunEntity(
    @PrimaryKey val timerId: Long,
    val phase: TimerPhase,
    val endsAt: Long? = null,
    val remainingMs: Long? = null,
    val segmentStartedAt: Long? = null,
)

/** Finished rounds of one timer on one date. */
@Entity(
    tableName = "timer_reps",
    primaryKeys = ["timerId", "date"],
    foreignKeys = [ForeignKey(
        entity = TimerEntity::class,
        parentColumns = ["id"],
        childColumns = ["timerId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class TimerRepsEntity(
    val timerId: Long,
    val date: LocalDate,
    val count: Int,
)

/**
 * Time focused per activity per date. Kept apart from [TimerEntity] (no foreign
 * key), so renaming or deleting a timer never changes the past.
 * [activityKey] is the timer title, trimmed and lowercased.
 */
@Entity(tableName = "focus_log", primaryKeys = ["date", "activityKey"])
data class FocusLogEntity(
    val date: LocalDate,
    val activityKey: String,
    val title: String,
    val seconds: Long,
    val rounds: Int,
)
