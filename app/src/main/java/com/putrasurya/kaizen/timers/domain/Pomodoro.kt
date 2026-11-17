package com.putrasurya.kaizen.timers.domain

/**
 * Pomodoro settings on a timer (same rules as the web app's pomodoro.js).
 * The timer's own length is the focus round; after each finished round comes a
 * break, and every [longEvery]-th round a long one. [autoBreak] starts the
 * break by itself; off, it waits for Start.
 */
data class Pomodoro(
    val breakSec: Int = DEFAULT_BREAK_SEC,
    val longBreakSec: Int = DEFAULT_LONG_BREAK_SEC,
    val longEvery: Int = DEFAULT_LONG_EVERY,
    val autoBreak: Boolean = true,
) {
    /** The break after a round, given how many rounds are now done today. */
    fun breakAfter(roundsToday: Int): Break {
        val long = roundsToday > 0 && roundsToday % longEvery == 0
        return Break(long = long, seconds = if (long) longBreakSec else breakSec)
    }

    /** Where today's rounds sit in the current cycle: 0..longEvery dots filled. */
    fun cyclePosition(roundsToday: Int): Int =
        if (roundsToday <= 0) 0 else ((roundsToday - 1) % longEvery) + 1

    data class Break(val long: Boolean, val seconds: Int)

    companion object {
        const val DEFAULT_BREAK_SEC = 5 * 60
        const val DEFAULT_LONG_BREAK_SEC = 15 * 60
        const val DEFAULT_LONG_EVERY = 4

        const val MIN_BREAK_MIN = 1
        const val MAX_BREAK_MIN = 60
        const val MAX_LONG_BREAK_MIN = 120
        const val MIN_LONG_EVERY = 2
        const val MAX_LONG_EVERY = 12

        /** Settings typed by the user, made safe; null when a field is unusable. */
        fun fromInput(
            breakMinutes: String,
            longBreakMinutes: String,
            longEvery: String,
            autoBreak: Boolean,
        ): Pomodoro? {
            val shortMin = breakMinutes.trim().toIntOrNull()
                ?.takeIf { it in MIN_BREAK_MIN..MAX_BREAK_MIN } ?: return null
            val longMin = longBreakMinutes.trim().toIntOrNull()
                ?.takeIf { it in MIN_BREAK_MIN..MAX_LONG_BREAK_MIN } ?: return null
            val every = longEvery.trim().toIntOrNull()
                ?.takeIf { it in MIN_LONG_EVERY..MAX_LONG_EVERY } ?: return null
            return Pomodoro(shortMin * 60, longMin * 60, every, autoBreak)
        }
    }
}
