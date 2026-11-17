package com.putrasurya.kaizen.timers.data

import com.putrasurya.kaizen.timers.alarm.TimerAlarms
import com.putrasurya.kaizen.timers.alarm.TimerNotifications
import com.putrasurya.kaizen.timers.domain.TimerMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** A timer together with its run, if it has been started. */
data class TimerWithRun(val timer: TimerEntity, val run: TimerRunEntity?) {
    val isRunning: Boolean get() = run?.endsAt != null
    val isPaused: Boolean get() = run?.remainingMs != null

    fun remainingMs(now: Long): Long =
        TimerMath.remainingMs(run?.endsAt, run?.remainingMs, timer.durationSec * 1000L, now)
}

/**
 * The only way screens and receivers change timers. Every change keeps three
 * things in step: the database row, the exact alarm and the notification (ADR-005).
 * A mutex makes changes happen one at a time, so a notification button and the
 * screen can't trip over each other.
 */
class TimerRepository(
    private val dao: TimerDao,
    private val alarms: TimerAlarms,
    private val notifications: TimerNotifications,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    fun observeDay(weekday: Int): Flow<List<TimerWithRun>> =
        combine(dao.observeTimers(weekday), dao.observeRuns()) { timers, runs ->
            val runsById = runs.associateBy { it.timerId }
            timers.map { TimerWithRun(it, runsById[it.id]) }
        }

    /** Adds the same timer to each of [weekdays] (1 = Monday … 7 = Sunday). */
    suspend fun add(title: String, durationSec: Int, weekdays: Set<Int>): Unit = mutex.withLock {
        weekdays.filter { it in 1..7 }.sorted().forEach { day ->
            dao.insertTimer(TimerEntity(title = title.trim(), weekday = day, durationSec = durationSec))
        }
    }

    /** A new length starts the round over, as on the web app. */
    suspend fun edit(id: Long, title: String, durationSec: Int): Unit = mutex.withLock {
        val timer = dao.getTimer(id) ?: return@withLock
        val updated = timer.copy(title = title.trim(), durationSec = durationSec)
        dao.updateTimer(updated)
        if (timer.durationSec != durationSec) {
            stopLocked(id)
        } else {
            // Keep the notification's title up to date.
            val run = dao.getRun(id)
            run?.endsAt?.let { notifications.showRunning(updated, it) }
            run?.remainingMs?.let { notifications.showPaused(updated, it) }
        }
    }

    suspend fun delete(id: Long): Unit = mutex.withLock {
        stopLocked(id)
        dao.deleteTimer(id)
    }

    /** Starts, or resumes from where it was paused. */
    suspend fun start(id: Long): Unit = mutex.withLock {
        val timer = dao.getTimer(id) ?: return@withLock
        val run = dao.getRun(id)
        if (run?.endsAt != null) return@withLock // already running
        val remaining = run?.remainingMs ?: (timer.durationSec * 1000L)
        val endsAt = TimerMath.endsAt(remaining, clock.millis())
        dao.upsertRun(
            TimerRunEntity(timerId = id, phase = run?.phase ?: TimerPhase.FOCUS, endsAt = endsAt)
        )
        alarms.schedule(id, endsAt)
        notifications.showRunning(timer, endsAt)
    }

    suspend fun pause(id: Long): Unit = mutex.withLock {
        val timer = dao.getTimer(id) ?: return@withLock
        val run = dao.getRun(id) ?: return@withLock
        val endsAt = run.endsAt ?: return@withLock // already paused
        val left = TimerMath.remainingAtPause(endsAt, clock.millis())
        if (left <= 0) {
            finishLocked(id, announce = true)
            return@withLock
        }
        alarms.cancel(id)
        dao.upsertRun(run.copy(endsAt = null, remainingMs = left))
        notifications.showPaused(timer, left)
    }

    /** Back to full length, not running. */
    suspend fun stop(id: Long): Unit = mutex.withLock { stopLocked(id) }

    /** Called when the alarm fires. */
    suspend fun finish(id: Long): Unit = mutex.withLock { finishLocked(id, announce = true) }

    /**
     * Puts alarms back after a reboot (Android clears them) and tidies up on app
     * start. Timers overdue by more than [graceMs] are finished quietly; ones due
     * within the grace are left for their own alarm, which may be about to fire.
     */
    suspend fun restore(graceMs: Long): Unit = mutex.withLock {
        val now = clock.millis()
        for (run in dao.getRunning()) {
            val endsAt = run.endsAt ?: continue
            when {
                now - endsAt > graceMs -> finishLocked(run.timerId, announce = false)
                graceMs > 0 && TimerMath.isDue(endsAt, now) -> Unit
                else -> {
                    val timer = dao.getTimer(run.timerId) ?: continue
                    alarms.schedule(run.timerId, endsAt)
                    notifications.showRunning(timer, endsAt)
                }
            }
        }
    }

    private suspend fun stopLocked(id: Long) {
        alarms.cancel(id)
        dao.deleteRun(id)
        notifications.cancel(id)
    }

    private suspend fun finishLocked(id: Long, announce: Boolean) {
        val run = dao.getRun(id) ?: return // stopped in the meantime
        val endsAt = run.endsAt ?: return // paused in the meantime
        if (!TimerMath.isDue(endsAt, clock.millis())) {
            alarms.schedule(id, endsAt) // an old alarm; the real end is later
            return
        }
        val timer = dao.getTimer(id)
        // Phase 2a: the round is over, back to full length.
        // Reps (2b), Pomodoro breaks (2c) and the focus log (2d) hook in here.
        dao.deleteRun(id)
        if (timer != null && announce) notifications.showDone(timer) else notifications.cancel(id)
    }
}
