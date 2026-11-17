package com.putrasurya.kaizen.core.time

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Calendar helpers shared by every feature. Weeks run Monday–Sunday (ADR-004).
 * Plain Kotlin, no Android imports, so it is covered by unit tests.
 */
object Dates {

    /** Today in the phone's time zone, according to [clock]. */
    fun today(clock: Clock): LocalDate = LocalDate.now(clock)

    /** The Monday of the week that contains [date]. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The 7 dates of the week that starts on [weekStart], Monday first. */
    fun weekDates(weekStart: LocalDate): List<LocalDate> =
        (0L until 7L).map { weekStart.plusDays(it) }

    /** "YYYY-MM-DD", the format used in the database. */
    fun toKey(date: LocalDate): String = date.toString()

    /** Parses "YYYY-MM-DD"; null when the text isn't a valid date. */
    fun fromKey(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()
}
