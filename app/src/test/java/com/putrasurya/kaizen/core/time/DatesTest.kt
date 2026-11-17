package com.putrasurya.kaizen.core.time

import com.putrasurya.kaizen.core.data.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DatesTest {

    @Test
    fun weekStartsOnMonday() {
        val monday = LocalDate.of(2026, 11, 16)
        assertEquals(monday, Dates.weekStart(monday))
        assertEquals(monday, Dates.weekStart(LocalDate.of(2026, 11, 18))) // Wednesday
        assertEquals(monday, Dates.weekStart(LocalDate.of(2026, 11, 22))) // Sunday
    }

    @Test
    fun weekCrossesMonthAndYear() {
        // Thursday 1 January 2026 belongs to the week of Monday 29 December 2025.
        assertEquals(LocalDate.of(2025, 12, 29), Dates.weekStart(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun weekDatesAreSevenDaysMondayFirst() {
        val dates = Dates.weekDates(LocalDate.of(2026, 11, 16))
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 11, 16), dates.first())
        assertEquals(LocalDate.of(2026, 11, 22), dates.last())
    }

    @Test
    fun todayUsesTheClockTimeZone() {
        // 18:30 UTC is already the next day in Jakarta (UTC+7).
        val instant = Instant.parse("2026-11-17T18:30:00Z")
        val jakarta = Clock.fixed(instant, ZoneId.of("Asia/Jakarta"))
        val utc = Clock.fixed(instant, ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 11, 18), Dates.today(jakarta))
        assertEquals(LocalDate.of(2026, 11, 17), Dates.today(utc))
    }

    @Test
    fun dateInSameWeekFindsTheWeekday() {
        val wednesday = LocalDate.of(2026, 11, 18)
        assertEquals(LocalDate.of(2026, 11, 16), Dates.dateInSameWeek(wednesday, 1)) // Monday
        assertEquals(wednesday, Dates.dateInSameWeek(wednesday, 3))
        assertEquals(LocalDate.of(2026, 11, 22), Dates.dateInSameWeek(wednesday, 7)) // Sunday
    }

    @Test
    fun dateOfUsesTheClockTimeZone() {
        val moment = Instant.parse("2026-11-17T18:30:00Z").toEpochMilli()
        val jakarta = Clock.fixed(Instant.EPOCH, ZoneId.of("Asia/Jakarta"))
        assertEquals(LocalDate.of(2026, 11, 18), Dates.dateOf(moment, jakarta))
    }

    @Test
    fun keysRoundTrip() {
        val date = LocalDate.of(2026, 3, 5)
        assertEquals("2026-03-05", Dates.toKey(date))
        assertEquals(date, Dates.fromKey("2026-03-05"))
        assertNull(Dates.fromKey("not a date"))
    }

    @Test
    fun roomConverterRoundTrips() {
        val converters = Converters()
        val date = LocalDate.of(2026, 11, 17)
        assertEquals(date, converters.keyToDate(converters.dateToKey(date)))
        assertNull(converters.dateToKey(null))
    }
}
