package com.putrasurya.kaizen.timers.domain

import kotlin.math.max

/**
 * The rules of a countdown, in plain Kotlin (ADR-003, ADR-005).
 * A running timer is only an end time; everything else is computed from it.
 */
object TimerMath {

    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 600

    /** How early an alarm may fire and still count as "time's up". */
    const val DUE_TOLERANCE_MS = 1_000L

    /**
     * Milliseconds left on a timer.
     * Running: [endsAt] is set. Paused: [pausedRemainingMs] is set. Neither: full length.
     */
    fun remainingMs(endsAt: Long?, pausedRemainingMs: Long?, durationMs: Long, now: Long): Long = when {
        endsAt != null -> max(0L, endsAt - now)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> durationMs
    }

    /** The end time when starting (or resuming) with [remainingMs] left. */
    fun endsAt(remainingMs: Long, now: Long): Long = now + remainingMs

    /** What is left when pausing a timer that would end at [endsAt]. */
    fun remainingAtPause(endsAt: Long, now: Long): Long = max(0L, endsAt - now)

    /** Whether a running timer has reached zero. */
    fun isDue(endsAt: Long, now: Long): Boolean = endsAt - now <= DUE_TOLERANCE_MS

    /** Whole seconds to show: 1.2 s left shows as 0:02, like a kitchen timer. */
    fun displaySeconds(remainingMs: Long): Long = (remainingMs + 999) / 1000

    /** "24:13", "1:05:00", "0:09" (same format as the web app). */
    fun formatClock(totalSeconds: Long): String {
        val s = max(0L, totalSeconds)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = (s % 60).toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
        else "$minutes:$seconds"
    }

    /** Time spent, for the reps line: "1 h 30 min", "1 h", "25 min", "0 min". */
    fun formatDuration(totalSeconds: Long): String {
        val minutes = max(0L, totalSeconds) / 60
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> "$rest min"
            rest == 0L -> "$hours h"
            else -> "$hours h $rest min"
        }
    }

    /** "1 rep", "3 reps". */
    fun repsLabel(count: Int): String = if (count == 1) "1 rep" else "$count reps"

    /** Minutes typed by the user, as seconds; null when empty or out of range. */
    fun parseMinutes(text: String): Int? {
        val minutes = text.trim().toIntOrNull() ?: return null
        return if (minutes in MIN_MINUTES..MAX_MINUTES) minutes * 60 else null
    }
}
