package com.putrasurya.kaizen.habits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitMathTest {

    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 11, 16)
    private val hour = 60 * 60_000L

    private fun at(date: LocalDate, hours: Int, minutes: Int = 0): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli() + hours * hour + minutes * 60_000L

    private val daily = Habit(1, HabitKind.DAILY, "Pray", target = 5, createdOn = monday)

    private fun routine(auto: AutoTap? = null) =
        Habit(2, HabitKind.ROUTINE, "No snacks", target = 1, createdOn = monday, auto = auto)

    @Test
    fun dailyCountNeverExceedsTarget() {
        assertEquals(5, HabitMath.dailyCount(daily, DayLog(count = 7)))
        assertEquals(0, HabitMath.dailyCount(daily, null))
    }

    @Test
    fun dailyWeekSumsTheWeek() {
        val logs = mapOf(monday to DayLog(count = 5), monday.plusDays(1) to DayLog(count = 3))
        val week = HabitMath.dailyWeek(daily, monday, logs)
        assertEquals(8, week.done)
        assertEquals(35, week.possible)
    }

    @Test
    fun balanceLabels() {
        assertEquals("Owe 3", HabitMath.balanceLabel(-3))
        assertEquals("+2 ahead", HabitMath.balanceLabel(2))
        assertEquals("Clear", HabitMath.balanceLabel(0))
    }

    @Test
    fun routineWeekBalancesSlipsAgainstGoodTaps() {
        val logs = mapOf(monday to DayLog(plus = 1, minus = 3), monday.plusDays(2) to DayLog(plus = 1))
        val week = HabitMath.routineWeek(routine(), monday, logs, now = at(monday.plusDays(3), 12), zone = zone)
        assertEquals(-1, week.balance)
    }

    // --- Auto tap ---

    private fun hourlyPlus(since: Long, max: Int = 8) =
        AutoTap(TapDirection.PLUS, everyMinutes = 60, maxPerDay = max, since = since, frozenThrough = null)

    @Test
    fun autoTapEarnsOnePerInterval() {
        val habit = routine(hourlyPlus(since = at(monday, 8)))
        val live = HabitMath.liveAuto(habit, monday, null, now = at(monday, 11, 30), zone = zone)!!
        assertEquals(3, live.count)
        assertEquals(at(monday, 12), live.next)
    }

    @Test
    fun autoTapStopsAtTheDailyMax() {
        val habit = routine(hourlyPlus(since = at(monday, 0), max = 2))
        val live = HabitMath.liveAuto(habit, monday, null, now = at(monday, 10), zone = zone)!!
        assertEquals(2, live.count)
        assertNull(live.next)
    }

    @Test
    fun oppositeTapRestartsTheClock() {
        val habit = routine(hourlyPlus(since = at(monday, 8)))
        // A slip at 9:30 restarts: 8→9 earns 1, then 9:30→11:00 earns 1 more.
        val log = DayLog(minus = 1, minusAt = listOf(at(monday, 9, 30)))
        val live = HabitMath.liveAuto(habit, monday, log, now = at(monday, 11), zone = zone)!!
        assertEquals(2, live.count)
        assertEquals(at(monday, 11, 30), live.next)
    }

    @Test
    fun keepsCountingOnDaysTheAppWasClosed() {
        val habit = routine(hourlyPlus(since = at(monday, 0), max = 8))
        val tuesday = monday.plusDays(1)
        val day = HabitMath.routineDay(habit, tuesday, null, now = at(tuesday.plusDays(1), 9), zone = zone)
        assertEquals(8, day.autoPlus) // a full day, capped at the max
    }

    @Test
    fun frozenDaysUseOnlyTheSavedLog() {
        val habit = routine(hourlyPlus(since = at(monday, 0)).copy(frozenThrough = monday))
        assertNull(HabitMath.liveAuto(habit, monday, DayLog(autoPlus = 4), now = at(monday, 20), zone = zone))
        val day = HabitMath.routineDay(habit, monday, DayLog(autoPlus = 4), now = at(monday, 20), zone = zone)
        assertEquals(4, day.autoPlus)
    }

    @Test
    fun freezeSavesEarnedTapsUpToNow() {
        val habit = routine(hourlyPlus(since = at(monday, 20)))
        val now = at(monday.plusDays(1), 2)
        val adds = HabitMath.freeze(habit, emptyMap(), now, zone)
        assertEquals(4, adds[monday]) // 20:00 → midnight
        assertEquals(2, adds[monday.plusDays(1)]) // midnight → 02:00
    }

    @Test
    fun newSettingsStartNowAndUnchangedSettingsStay() {
        val now = at(monday, 9)
        val current = hourlyPlus(since = at(monday, 0))
        val same = AutoTapInput(TapDirection.PLUS, 60, 8)
        assertEquals(current, HabitMath.newAuto(current, same, now, zone))

        val changed = HabitMath.newAuto(current, AutoTapInput(TapDirection.MINUS, 30, 4), now, zone)!!
        assertEquals(now, changed.since)
        assertEquals(monday.minusDays(1), changed.frozenThrough)
        assertNull(HabitMath.newAuto(current, null, now, zone))
    }

    @Test
    fun historySkipsWeeksBeforeTheHabitExisted() {
        val today = monday.plusDays(2)
        val weeks = HabitMath.recentWeekStarts(today, count = 8, since = monday.minusWeeks(2))
        assertEquals(listOf(monday.minusWeeks(2), monday.minusWeeks(1), monday), weeks)
    }

    @Test
    fun daySummaryFlagsOnlyWhenSlipsWin() {
        val logs = mapOf(1L to DayLog(count = 5), 2L to DayLog(plus = 1, minus = 2))
        val summary = HabitMath.daySummary(listOf(daily, routine()), monday, logs, at(monday, 22), zone)
        assertEquals("5/5", summary[0].text)
        assertTrue(summary[0].good)
        assertEquals("+1 −2", summary[1].text)
        assertTrue(summary[1].bad)
    }
}
