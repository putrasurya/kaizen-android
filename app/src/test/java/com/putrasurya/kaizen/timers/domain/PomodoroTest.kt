package com.putrasurya.kaizen.timers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroTest {

    private val pomodoro = Pomodoro() // 5 min break, 15 min long break every 4 rounds

    @Test
    fun shortBreakAfterMostRounds() {
        val next = pomodoro.breakAfter(roundsToday = 1)
        assertFalse(next.long)
        assertEquals(5 * 60, next.seconds)
    }

    @Test
    fun longBreakEveryFourthRound() {
        assertTrue(pomodoro.breakAfter(4).long)
        assertEquals(15 * 60, pomodoro.breakAfter(8).seconds)
        assertFalse(pomodoro.breakAfter(5).long)
    }

    @Test
    fun cycleDotsFillAndWrap() {
        assertEquals(0, pomodoro.cyclePosition(0))
        assertEquals(1, pomodoro.cyclePosition(1))
        assertEquals(4, pomodoro.cyclePosition(4))
        assertEquals(1, pomodoro.cyclePosition(5))
    }

    @Test
    fun parsesValidInput() {
        val parsed = Pomodoro.fromInput("5", "20", "3", autoBreak = false)
        assertEquals(Pomodoro(300, 1200, 3, false), parsed)
    }

    @Test
    fun rejectsOutOfRangeInput() {
        assertNull(Pomodoro.fromInput("0", "15", "4", true))
        assertNull(Pomodoro.fromInput("5", "15", "1", true))
        assertNull(Pomodoro.fromInput("5", "abc", "4", true))
    }
}
