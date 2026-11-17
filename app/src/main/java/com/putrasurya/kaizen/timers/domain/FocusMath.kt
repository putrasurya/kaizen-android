package com.putrasurya.kaizen.timers.domain

import com.putrasurya.kaizen.core.time.Dates
import java.time.LocalDate

/** One day of an activity in the focus log. */
data class FocusDay(val seconds: Long, val rounds: Int) {
    /** A day counts as done with at least one finished round. */
    val isDone: Boolean get() = rounds > 0
}

/** What the focus history screen shows for one activity. */
data class FocusStats(
    val streak: Int,
    val thisWeekSec: Long,
    val lastWeekSec: Long,
    val plannedThisWeek: Int,
    val doneThisWeek: Int,
)

/**
 * The focus log's rules (same as the web app's focus.js). An activity is a
 * timer name: every weekday copy with the same name (ignoring case and spaces
 * at the ends) shares one history.
 */
object FocusMath {

    fun key(title: String): String = title.trim().lowercase()

    /** Heatmap shade 0..4: none, under 30 min, under 1 h, under 2 h, 2 h+. */
    fun level(seconds: Long): Int = when {
        seconds <= 0 -> 0
        seconds < 30 * 60 -> 1
        seconds < 60 * 60 -> 2
        seconds < 2 * 60 * 60 -> 3
        else -> 4
    }

    /**
     * Days done in a row, counting back from [today]. Days the activity isn't
     * planned on (weekdays 1–7 in [planned]) are skipped unless done, which
     * counts; today doesn't break the streak while it's still going.
     */
    fun streak(days: Map<LocalDate, FocusDay>, planned: Set<Int>, today: LocalDate): Int {
        val first = days.keys.minOrNull() ?: return 0
        var count = 0
        var date = today
        while (!date.isBefore(first)) {
            val done = days[date]?.isDone == true
            if (done) count++
            else if (date.dayOfWeek.value in planned && date != today) break
            date = date.minusDays(1)
        }
        return count
    }

    fun secondsBetween(days: Map<LocalDate, FocusDay>, from: LocalDate, to: LocalDate): Long {
        var total = 0L
        var date = from
        while (!date.isAfter(to)) {
            total += days[date]?.seconds ?: 0
            date = date.plusDays(1)
        }
        return total
    }

    fun stats(days: Map<LocalDate, FocusDay>, planned: Set<Int>, today: LocalDate): FocusStats {
        val monday = Dates.weekStart(today)
        val lastMonday = monday.minusWeeks(1)
        val thisWeekSoFar = Dates.weekDates(monday).filter { !it.isAfter(today) }
        val plannedSoFar = thisWeekSoFar.filter { it.dayOfWeek.value in planned }
        return FocusStats(
            streak = streak(days, planned, today),
            thisWeekSec = secondsBetween(days, monday, today),
            lastWeekSec = secondsBetween(days, lastMonday, lastMonday.plusDays(6)),
            plannedThisWeek = plannedSoFar.size,
            doneThisWeek = plannedSoFar.count { days[it]?.isDone == true },
        )
    }
}
