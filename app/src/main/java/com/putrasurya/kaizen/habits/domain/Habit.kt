package com.putrasurya.kaizen.habits.domain

import java.time.LocalDate

/**
 * Two kinds of habit (same as the web app):
 * - DAILY: a fixed number of slots to fill each day (pray 5×, 8 glasses of water).
 * - ROUTINE: a +/− balance (resisting an urge). A slip (−) is a debt that good
 *   taps (+) pay back. The balance covers Monday–Sunday and starts clear each week.
 * Both keep their whole log, so nothing has to run at midnight or on Monday:
 * "this week" and the history are different views over the same dates.
 */
enum class HabitKind { DAILY, ROUTINE }

enum class TapDirection {
    PLUS, MINUS;

    val opposite: TapDirection get() = if (this == PLUS) MINUS else PLUS
}

/**
 * Auto tap on a routine: one tap in [direction] every [everyMinutes], at most
 * [maxPerDay] per calendar day. A manual tap the other way restarts the clock.
 * [since]: when these settings started (epoch ms). Days up to [frozenThrough]
 * have their auto taps saved in the log; later days are computed live.
 */
data class AutoTap(
    val direction: TapDirection,
    val everyMinutes: Int,
    val maxPerDay: Int,
    val since: Long,
    val frozenThrough: LocalDate?,
) {
    fun sameSettingsAs(input: AutoTapInput): Boolean =
        direction == input.direction && everyMinutes == input.everyMinutes && maxPerDay == input.maxPerDay
}

/** Auto-tap settings as chosen in the form. */
data class AutoTapInput(val direction: TapDirection, val everyMinutes: Int, val maxPerDay: Int) {
    companion object {
        const val MIN_EVERY = 5
        const val MAX_EVERY = 24 * 60
        const val MIN_MAX = 1
        const val MAX_MAX = 100

        /** Null when a field is unusable. */
        fun fromInput(direction: TapDirection, everyMinutes: String, maxPerDay: String): AutoTapInput? {
            val every = everyMinutes.trim().toIntOrNull()?.takeIf { it in MIN_EVERY..MAX_EVERY } ?: return null
            val max = maxPerDay.trim().toIntOrNull()?.takeIf { it in MIN_MAX..MAX_MAX } ?: return null
            return AutoTapInput(direction, every, max)
        }
    }
}

data class Habit(
    val id: Long,
    val kind: HabitKind,
    val name: String,
    /** Slots per day (daily habits). */
    val target: Int,
    val createdOn: LocalDate,
    val auto: AutoTap? = null,
) {
    companion object {
        const val MAX_TARGET = 20
    }
}

/** One habit's log for one date: slots filled (daily) or taps (routine). */
data class DayLog(
    val count: Int = 0,
    val plus: Int = 0,
    val minus: Int = 0,
    val autoPlus: Int = 0,
    val autoMinus: Int = 0,
    /** When each manual tap happened (epoch ms), for auto-tap restarts. */
    val plusAt: List<Long> = emptyList(),
    val minusAt: List<Long> = emptyList(),
) {
    fun savedAuto(direction: TapDirection): Int = if (direction == TapDirection.PLUS) autoPlus else autoMinus
    fun tapTimes(direction: TapDirection): List<Long> = if (direction == TapDirection.PLUS) plusAt else minusAt
}
