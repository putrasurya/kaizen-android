package com.putrasurya.kaizen.timers.data

import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.timers.alarm.TimerAlarms
import com.putrasurya.kaizen.timers.alarm.TimerNotifications
import com.putrasurya.kaizen.timers.domain.FocusMath
import com.putrasurya.kaizen.timers.domain.Pomodoro
import com.putrasurya.kaizen.timers.domain.TimerMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * A timer together with its run (if started), its reps and focused time on the
 * date being shown, and its all-time reps.
 */
data class TimerWithRun(
    val timer: TimerEntity,
    val run: TimerRunEntity?,
    val reps: Int = 0,
    val repsTotal: Int = 0,
    val focusedSec: Long = 0,
) {
    val isRunning: Boolean get() = run?.endsAt != null
    val isPaused: Boolean get() = run?.remainingMs != null
    val phase: TimerPhase get() = run?.phase ?: TimerPhase.FOCUS
    val isBreak: Boolean get() = phase != TimerPhase.FOCUS

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

    /** The timers of [weekday], with their reps and focus on [date]. */
    fun observeDay(weekday: Int, date: LocalDate): Flow<List<TimerWithRun>> =
        combine(
            dao.observeTimers(weekday),
            dao.observeRuns(),
            dao.observeRepsOn(date),
            dao.observeRepTotals(),
            dao.observeFocusOn(date),
        ) { timers, runs, reps, totals, focus ->
            val runsById = runs.associateBy { it.timerId }
            val repsById = reps.associate { it.timerId to it.count }
            val totalsById = totals.associate { it.timerId to it.total }
            val focusByKey = focus.associate { it.activityKey to it.seconds }
            timers.map {
                TimerWithRun(
                    timer = it,
                    run = runsById[it.id],
                    reps = repsById[it.id] ?: 0,
                    repsTotal = totalsById[it.id] ?: 0,
                    focusedSec = focusByKey[FocusMath.key(it.title)] ?: 0,
                )
            }
        }

    /** Every timer, for the focus history (which weekdays an activity is planned on). */
    fun observeAllTimers(): Flow<List<TimerEntity>> = dao.observeAllTimers()

    fun observeFocus(activityKey: String): Flow<List<FocusLogEntity>> = dao.observeFocus(activityKey)

    suspend fun getTimer(id: Long): TimerEntity? = dao.getTimer(id)

    /** Adds the same timer to each of [weekdays] (1 = Monday … 7 = Sunday). */
    suspend fun add(
        title: String,
        durationSec: Int,
        weekdays: Set<Int>,
        pomodoro: Pomodoro?,
    ): Unit = mutex.withLock {
        weekdays.filter { it in 1..7 }.sorted().forEach { day ->
            val timer = TimerEntity(title = title.trim(), weekday = day, durationSec = durationSec)
            dao.insertTimer(timer.withPomodoro(pomodoro))
        }
    }

    /**
     * A new length starts the round over, as on the web app. A rename takes the
     * activity's focus history along, unless another timer (another weekday's
     * copy) still goes by the old name.
     */
    suspend fun edit(
        id: Long,
        title: String,
        durationSec: Int,
        pomodoro: Pomodoro?,
    ): Unit = mutex.withLock {
        val timer = dao.getTimer(id) ?: return@withLock
        val newTitle = title.trim()
        val updated = timer.copy(title = newTitle, durationSec = durationSec).withPomodoro(pomodoro)
        if (timer.durationSec != durationSec) stopLocked(id)
        dao.updateTimer(updated)

        val oldKey = FocusMath.key(timer.title)
        val newKey = FocusMath.key(newTitle)
        if (oldKey == newKey) {
            dao.relabelFocus(newKey, newTitle)
        } else if (dao.getAllTimers().none { FocusMath.key(it.title) == oldKey }) {
            dao.renameFocus(oldKey, newKey, newTitle)
        }

        // Keep the notification's title up to date.
        val run = dao.getRun(id) ?: return@withLock
        run.endsAt?.let { notifications.showRunning(updated, run.phase, it) }
        run.remainingMs?.let { notifications.showPaused(updated, run.phase, it) }
    }

    /** The focus history stays (it isn't tied to the timer), as on the web app. */
    suspend fun delete(id: Long): Unit = mutex.withLock {
        stopLocked(id)
        dao.deleteTimer(id)
    }

    /** Starts a focus round, resumes a paused one, or starts a waiting break. */
    suspend fun start(id: Long): Unit = mutex.withLock {
        val timer = dao.getTimer(id) ?: return@withLock
        val run = dao.getRun(id)
        if (run?.endsAt != null) return@withLock // already running
        val now = clock.millis()
        val remaining = run?.remainingMs ?: (timer.durationSec * 1000L)
        val phase = run?.phase ?: TimerPhase.FOCUS
        val endsAt = TimerMath.endsAt(remaining, now)
        dao.upsertRun(
            TimerRunEntity(timerId = id, phase = phase, endsAt = endsAt, segmentStartedAt = now)
        )
        alarms.schedule(id, endsAt)
        notifications.showRunning(timer, phase, endsAt)
    }

    suspend fun pause(id: Long): Unit = mutex.withLock {
        val timer = dao.getTimer(id) ?: return@withLock
        val run = dao.getRun(id) ?: return@withLock
        val endsAt = run.endsAt ?: return@withLock // already paused
        val now = clock.millis()
        val left = TimerMath.remainingAtPause(endsAt, now)
        if (left <= 0) {
            finishLocked(id, announce = true)
            return@withLock
        }
        alarms.cancel(id)
        logFocus(timer, run, until = now, round = false)
        dao.upsertRun(run.copy(endsAt = null, remainingMs = left, segmentStartedAt = null))
        notifications.showPaused(timer, run.phase, left)
    }

    /** Back to full length, not running. Focus time so far is kept in the log. */
    suspend fun stop(id: Long): Unit = mutex.withLock { stopLocked(id) }

    /** Ends a break early; the next focus round waits for Start. */
    suspend fun skipBreak(id: Long): Unit = mutex.withLock {
        val run = dao.getRun(id) ?: return@withLock
        if (run.phase != TimerPhase.FOCUS) stopLocked(id)
    }

    /** Called when the alarm fires. */
    suspend fun finish(id: Long): Unit = mutex.withLock { finishLocked(id, announce = true) }

    /**
     * Puts alarms back after a reboot (Android clears them) and tidies up on app
     * start. Rounds and breaks overdue by more than [graceMs] are finished
     * quietly (a round can lead to a break that is overdue too, hence the loop);
     * ones due within the grace are left for their own alarm, which may be
     * about to fire.
     */
    suspend fun restore(graceMs: Long): Unit = mutex.withLock {
        val now = clock.millis()
        for (running in dao.getRunning()) {
            val id = running.timerId
            while (true) {
                val endsAt = dao.getRun(id)?.endsAt ?: break
                if (now - endsAt > graceMs) {
                    finishLocked(id, announce = false)
                    continue
                }
                if (graceMs > 0 && TimerMath.isDue(endsAt, now)) break
                val run = dao.getRun(id) ?: break
                val timer = dao.getTimer(id) ?: break
                alarms.schedule(id, endsAt)
                notifications.showRunning(timer, run.phase, endsAt)
                break
            }
        }
    }

    private suspend fun stopLocked(id: Long) {
        val run = dao.getRun(id)
        val timer = dao.getTimer(id)
        if (run != null && timer != null && run.endsAt != null) {
            logFocus(timer, run, until = clock.millis(), round = false)
        }
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
        if (timer == null) {
            dao.deleteRun(id)
            notifications.cancel(id)
            return
        }

        if (run.phase != TimerPhase.FOCUS) {
            // A break is over: the next focus round waits for Start.
            dao.deleteRun(id)
            if (announce) notifications.showBreakOver(timer) else notifications.cancel(id)
            return
        }

        // A focus round is over: +1 rep and focus time on the date it finished
        // (even if nobody heard it ring, e.g. the phone was off).
        val finishedOn = Dates.dateOf(endsAt, clock)
        dao.addRep(id, finishedOn)
        logFocus(timer, run, until = endsAt, round = true)
        val repsToday = dao.getReps(id, finishedOn) ?: 1

        val pomodoro = timer.pomodoro()
        if (pomodoro == null) {
            dao.deleteRun(id) // back to full length
            if (announce) notifications.showRoundDone(timer, repsToday) else notifications.cancel(id)
            return
        }

        val next = pomodoro.breakAfter(repsToday)
        val phase = if (next.long) TimerPhase.LONG_BREAK else TimerPhase.BREAK
        val breakMs = next.seconds * 1000L
        if (pomodoro.autoBreak) {
            // The break starts when the round ended, even if the alarm came late.
            val breakEndsAt = endsAt + breakMs
            dao.upsertRun(TimerRunEntity(timerId = id, phase = phase, endsAt = breakEndsAt, segmentStartedAt = endsAt))
            alarms.schedule(id, breakEndsAt)
            if (announce) notifications.showRoundDone(timer, repsToday, phase, breakEndsAt)
            else notifications.showRunning(timer, phase, breakEndsAt)
        } else {
            dao.upsertRun(TimerRunEntity(timerId = id, phase = phase, remainingMs = breakMs))
            if (announce) notifications.showRoundDone(timer, repsToday, phase)
            else notifications.showPaused(timer, phase, breakMs)
        }
    }

    /**
     * Adds the focused time of the current running stretch (and a round when it
     * finished) to the focus log. Breaks don't count.
     */
    private suspend fun logFocus(timer: TimerEntity, run: TimerRunEntity, until: Long, round: Boolean) {
        if (run.phase != TimerPhase.FOCUS) return
        val started = run.segmentStartedAt
        val end = min(until, run.endsAt ?: until)
        val seconds = if (started == null) 0L else max(0L, (end - started) / 1000)
        dao.addFocus(
            date = Dates.dateOf(end, clock),
            activityKey = FocusMath.key(timer.title),
            title = timer.title.trim(),
            seconds = seconds,
            rounds = if (round) 1 else 0,
        )
    }
}
