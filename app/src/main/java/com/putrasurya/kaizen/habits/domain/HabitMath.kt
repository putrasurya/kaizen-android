package com.putrasurya.kaizen.habits.domain

import com.putrasurya.kaizen.core.time.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** Auto taps on one date not saved in the log yet, as of now. */
data class LiveAuto(
    val direction: TapDirection,
    val count: Int,
    val total: Int,
    val max: Int,
    /** When the next auto tap lands today (epoch ms), or null. */
    val next: Long?,
)

/** One routine day's taps, manual and auto. */
data class RoutineDay(val plus: Int, val minus: Int, val autoPlus: Int, val autoMinus: Int) {
    val totalPlus: Int get() = plus + autoPlus
    val totalMinus: Int get() = minus + autoMinus
}

data class RoutineWeek(val plus: Int, val minus: Int, val auto: Int) {
    val balance: Int get() = plus - minus
}

data class DailyWeek(val counts: List<Int>, val done: Int, val possible: Int)

/** How a habit went on one date, for showing beside a journal entry. */
data class HabitSummary(val name: String, val text: String, val good: Boolean, val bad: Boolean)

/**
 * The habit rules, ported from the web app's habit-helper.js and auto-tap.js.
 * Pure functions of the log and the time: nothing runs in the background, and
 * auto taps keep counting while the app is closed because they're worked out
 * whenever they're needed.
 */
object HabitMath {

    const val HISTORY_WEEKS = 8

    /** How far the balance marker travels from the centre before it pins at the edge. */
    const val BALANCE_RANGE = 10

    private const val MINUTE = 60_000L

    fun toTarget(value: Int): Int = value.coerceIn(1, Habit.MAX_TARGET)

    /** Slots filled on one day, never more than the target (lowering a target shouldn't show 7/5). */
    fun dailyCount(habit: Habit, log: DayLog?): Int = min(log?.count ?: 0, habit.target)

    fun dailyWeek(habit: Habit, weekStart: LocalDate, logs: Map<LocalDate, DayLog>): DailyWeek {
        val counts = Dates.weekDates(weekStart).map { dailyCount(habit, logs[it]) }
        return DailyWeek(counts, counts.sum(), habit.target * 7)
    }

    fun balanceLabel(balance: Int): String = when {
        balance < 0 -> "Owe ${-balance}"
        balance > 0 -> "+$balance ahead"
        else -> "Clear"
    }

    /** [midnight, next midnight) of a calendar date, in ms. */
    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dateOf(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** Whole intervals between [from] and [to], restarting at each opposite manual tap. */
    private fun earned(auto: AutoTap, log: DayLog?, from: Long, to: Long): Pair<Int, Long> {
        val interval = auto.everyMinutes * MINUTE
        val restarts = (log?.tapTimes(auto.direction.opposite) ?: emptyList())
            .filter { it > from && it <= to }
            .sorted()
        var start = from
        var count = 0L
        for (at in restarts) {
            count += (at - start) / interval
            start = at
        }
        count += (to - start) / interval
        return count.toInt() to start
    }

    /** Auto taps on [date] not saved in the log yet, as of [now]. Null when off, frozen or in the future. */
    fun liveAuto(habit: Habit, date: LocalDate, log: DayLog?, now: Long, zone: ZoneId): LiveAuto? {
        val auto = habit.auto ?: return null
        if (auto.frozenThrough != null && !date.isAfter(auto.frozenThrough)) return null
        val today = dateOf(now, zone)
        if (date.isAfter(today)) return null

        val (dayStart, dayEnd) = dayBounds(date, zone)
        val from = max(dayStart, auto.since)
        val to = min(dayEnd, now)
        val saved = log?.savedAuto(auto.direction) ?: 0
        val room = max(0, auto.maxPerDay - saved)
        val interval = auto.everyMinutes * MINUTE
        if (to <= from) {
            // Just switched on (or not started yet today): the first tap is one interval away.
            val next = if (date == today && room > 0 && from + interval < dayEnd) from + interval else null
            return LiveAuto(auto.direction, 0, saved, auto.maxPerDay, next)
        }

        val (count, stretchStart) = earned(auto, log, from, to)
        val added = min(room, count)
        var next: Long? = null
        if (date == today && added < room) {
            val candidate = stretchStart + ((now - stretchStart) / interval + 1) * interval
            next = if (candidate >= dayEnd) null else candidate
        }
        return LiveAuto(auto.direction, added, saved + added, auto.maxPerDay, next)
    }

    fun routineDay(habit: Habit, date: LocalDate, log: DayLog?, now: Long, zone: ZoneId): RoutineDay {
        var day = RoutineDay(log?.plus ?: 0, log?.minus ?: 0, log?.autoPlus ?: 0, log?.autoMinus ?: 0)
        val live = liveAuto(habit, date, log, now, zone)
        if (live != null) {
            day = if (live.direction == TapDirection.PLUS) day.copy(autoPlus = day.autoPlus + live.count)
            else day.copy(autoMinus = day.autoMinus + live.count)
        }
        return day
    }

    fun routineWeek(
        habit: Habit,
        weekStart: LocalDate,
        logs: Map<LocalDate, DayLog>,
        now: Long,
        zone: ZoneId,
    ): RoutineWeek {
        var plus = 0
        var minus = 0
        var auto = 0
        for (date in Dates.weekDates(weekStart)) {
            val day = routineDay(habit, date, logs[date], now, zone)
            plus += day.totalPlus
            minus += day.totalMinus
            auto += day.autoPlus + day.autoMinus
        }
        return RoutineWeek(plus, minus, auto)
    }

    /**
     * Auto taps earned on every not-yet-frozen day up to and including today,
     * to save into the log before the settings change. Earlier days (and the
     * part of today already earned) then keep what the old settings gave them.
     */
    fun freeze(habit: Habit, logs: Map<LocalDate, DayLog>, now: Long, zone: ZoneId): Map<LocalDate, Int> {
        val auto = habit.auto ?: return emptyMap()
        val today = dateOf(now, zone)
        val sinceDate = dateOf(auto.since, zone)
        var date = if (auto.frozenThrough != null && !auto.frozenThrough.isBefore(sinceDate)) {
            auto.frozenThrough.plusDays(1)
        } else {
            sinceDate
        }
        val adds = mutableMapOf<LocalDate, Int>()
        while (!date.isAfter(today)) {
            val live = liveAuto(habit, date, logs[date], now, zone)
            if (live != null && live.count > 0) adds[date] = live.count
            date = date.plusDays(1)
        }
        return adds
    }

    /**
     * The auto-tap settings after a change: null turns it off. Unchanged
     * settings return the habit's current ones. The caller saves [freeze]'s
     * result first (unless unchanged).
     */
    fun newAuto(current: AutoTap?, input: AutoTapInput?, now: Long, zone: ZoneId): AutoTap? {
        if (input == null) return null
        if (current != null && current.sameSettingsAs(input)) return current
        return AutoTap(
            direction = input.direction,
            everyMinutes = input.everyMinutes,
            maxPerDay = input.maxPerDay,
            since = now,
            frozenThrough = dateOf(now, zone).minusDays(1),
        )
    }

    /** The last [count] week starts up to this week, oldest first, skipping weeks before [since]. */
    fun recentWeekStarts(today: LocalDate, count: Int = HISTORY_WEEKS, since: LocalDate? = null): List<LocalDate> {
        val current = Dates.weekStart(today)
        val first = since?.let(Dates::weekStart)
        return (0 until count)
            .map { current.minusWeeks((count - 1 - it).toLong()) }
            .filter { first == null || !it.isBefore(first) || it == current }
    }

    /** How each habit went on [date]; only habits that existed by then. */
    fun daySummary(
        habits: List<Habit>,
        date: LocalDate,
        logs: Map<Long, DayLog>,
        now: Long,
        zone: ZoneId,
    ): List<HabitSummary> = habits
        .filter { !it.createdOn.isAfter(date) }
        .map { habit ->
            val log = logs[habit.id]
            if (habit.kind == HabitKind.DAILY) {
                val count = dailyCount(habit, log)
                HabitSummary(habit.name, "$count/${habit.target}", good = count >= habit.target, bad = false)
            } else {
                val day = routineDay(habit, date, log, now, zone)
                val parts = listOfNotNull(
                    day.totalPlus.takeIf { it > 0 }?.let { "+$it" },
                    day.totalMinus.takeIf { it > 0 }?.let { "−$it" },
                )
                // Flagged only when slips outweighed good taps: one slip on a good
                // day shouldn't colour the whole day as a failure.
                HabitSummary(
                    habit.name,
                    if (parts.isEmpty()) "no taps" else parts.joinToString(" "),
                    good = false,
                    bad = day.totalMinus > day.totalPlus,
                )
            }
        }
}
