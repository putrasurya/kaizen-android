package com.putrasurya.kaizen.timers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerMathTest {

    private val minute = 60_000L

    @Test
    fun idleTimerShowsFullLength() {
        assertEquals(25 * minute, TimerMath.remainingMs(null, null, 25 * minute, now = 0))
    }

    @Test
    fun runningTimerCountsDownFromItsEndTime() {
        val endsAt = TimerMath.endsAt(25 * minute, now = 1_000)
        assertEquals(25 * minute - 4_000, TimerMath.remainingMs(endsAt, null, 25 * minute, now = 5_000))
    }

    @Test
    fun runningTimerNeverGoesBelowZero() {
        assertEquals(0L, TimerMath.remainingMs(endsAt = 10_000, null, minute, now = 50_000))
    }

    @Test
    fun pauseThenResumeKeepsTheTimeLeft() {
        val endsAt = TimerMath.endsAt(10 * minute, now = 0)
        val left = TimerMath.remainingAtPause(endsAt, now = 3 * minute)
        assertEquals(7 * minute, left)
        // While paused, the clock moving on changes nothing.
        assertEquals(7 * minute, TimerMath.remainingMs(null, left, 10 * minute, now = 99 * minute))
        // Resuming an hour later ends 7 minutes after that.
        assertEquals(67 * minute, TimerMath.endsAt(left, now = 60 * minute))
    }

    @Test
    fun dueAtTheEndOrSlightlyBefore() {
        assertTrue(TimerMath.isDue(endsAt = 10_000, now = 10_000))
        assertTrue(TimerMath.isDue(endsAt = 10_000, now = 9_500)) // alarm a bit early
        assertTrue(TimerMath.isDue(endsAt = 10_000, now = 20_000))
        assertFalse(TimerMath.isDue(endsAt = 10_000, now = 5_000))
    }

    @Test
    fun displayRoundsUp() {
        assertEquals(2L, TimerMath.displaySeconds(1_200))
        assertEquals(1L, TimerMath.displaySeconds(1_000))
        assertEquals(0L, TimerMath.displaySeconds(0))
    }

    @Test
    fun formatsLikeTheWebApp() {
        assertEquals("24:13", TimerMath.formatClock(24 * 60 + 13))
        assertEquals("1:05:00", TimerMath.formatClock(3600 + 5 * 60))
        assertEquals("0:09", TimerMath.formatClock(9))
        assertEquals("0:00", TimerMath.formatClock(-5))
    }

    @Test
    fun parsesMinutesWithinRange() {
        assertEquals(25 * 60, TimerMath.parseMinutes(" 25 "))
        assertNull(TimerMath.parseMinutes("0"))
        assertNull(TimerMath.parseMinutes("601"))
        assertNull(TimerMath.parseMinutes("abc"))
        assertNull(TimerMath.parseMinutes(""))
    }
}
