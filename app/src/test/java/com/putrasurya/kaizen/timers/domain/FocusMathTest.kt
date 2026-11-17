package com.putrasurya.kaizen.timers.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FocusMathTest {

    // Wednesday 18 November 2026
    private val today = LocalDate.of(2026, 11, 18)
    private val done = FocusDay(seconds = 1500, rounds = 1)
    private val everyDay = (1..7).toSet()

    @Test
    fun keyIgnoresCaseAndSpaces() {
        assertEquals("deep work", FocusMath.key("  Deep Work "))
    }

    @Test
    fun levelsByTimeFocused() {
        assertEquals(0, FocusMath.level(0))
        assertEquals(1, FocusMath.level(29 * 60))
        assertEquals(2, FocusMath.level(30 * 60))
        assertEquals(3, FocusMath.level(60 * 60))
        assertEquals(4, FocusMath.level(2 * 60 * 60))
    }

    @Test
    fun streakCountsBackFromToday() {
        val days = mapOf(
            today to done,
            today.minusDays(1) to done,
            today.minusDays(2) to done,
            today.minusDays(4) to done,
        )
        assertEquals(3, FocusMath.streak(days, everyDay, today))
    }

    @Test
    fun todayNotDoneYetDoesntBreakTheStreak() {
        val days = mapOf(today.minusDays(1) to done, today.minusDays(2) to done)
        assertEquals(2, FocusMath.streak(days, everyDay, today))
    }

    @Test
    fun unplannedDaysAreSkipped() {
        // Planned Monday and Wednesday only; Tuesday empty doesn't break it.
        val planned = setOf(1, 3)
        val days = mapOf(today to done, today.minusDays(2) to done) // Wed, Mon
        assertEquals(2, FocusMath.streak(days, planned, today))
    }

    @Test
    fun timeOnlyRoundsDontCountAsDone() {
        val days = mapOf(today.minusDays(1) to FocusDay(seconds = 600, rounds = 0))
        assertEquals(0, FocusMath.streak(days, everyDay, today))
    }

    @Test
    fun statsCompareThisWeekWithLastWeek() {
        val days = mapOf(
            today to FocusDay(3000, 2), // Wed
            today.minusDays(2) to FocusDay(1500, 1), // Mon
            today.minusDays(7) to FocusDay(600, 1), // last Wed
        )
        val stats = FocusMath.stats(days, planned = setOf(1, 2, 3), today = today)
        assertEquals(4500, stats.thisWeekSec)
        assertEquals(600, stats.lastWeekSec)
        assertEquals(3, stats.plannedThisWeek)
        assertEquals(2, stats.doneThisWeek)
    }
}
