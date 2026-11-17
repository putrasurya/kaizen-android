package com.putrasurya.kaizen.habits.data

import com.putrasurya.kaizen.core.time.Dates
import com.putrasurya.kaizen.habits.domain.AutoTapInput
import com.putrasurya.kaizen.habits.domain.DayLog
import com.putrasurya.kaizen.habits.domain.Habit
import com.putrasurya.kaizen.habits.domain.HabitKind
import com.putrasurya.kaizen.habits.domain.HabitMath
import com.putrasurya.kaizen.habits.domain.TapDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

/** A habit with its log for a range of dates. */
data class HabitWithLogs(val habit: Habit, val logs: Map<LocalDate, DayLog>)

class HabitRepository(
    private val dao: HabitDao,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Every habit with its log between [from] and [to]. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<HabitWithLogs>> =
        combine(dao.observeHabits(), dao.observeLogs(from, to), dao.observeTaps(from, to)) { habits, logs, taps ->
            val logsByHabit = logs.groupBy { it.habitId }
            val tapsByHabit = taps.groupBy { it.habitId }
            habits.map { entity ->
                HabitWithLogs(
                    entity.toDomain(),
                    toDayLogs(logsByHabit[entity.id].orEmpty(), tapsByHabit[entity.id].orEmpty()),
                )
            }
        }

    suspend fun add(kind: HabitKind, name: String, target: Int, auto: AutoTapInput?): Unit = mutex.withLock {
        val now = clock.millis()
        val entity = HabitEntity(
            kind = kind,
            name = name.trim(),
            target = if (kind == HabitKind.DAILY) HabitMath.toTarget(target) else 1,
            createdOn = Dates.today(clock),
        )
        val withAuto = if (kind == HabitKind.ROUTINE) {
            entity.withAuto(HabitMath.newAuto(null, auto, now, clock.zone))
        } else {
            entity
        }
        dao.insertHabit(withAuto)
    }

    /**
     * Name and target always; for routines also the auto-tap settings. Auto taps
     * earned so far are saved first, so new settings never rewrite them.
     */
    suspend fun update(id: Long, name: String, target: Int, auto: AutoTapInput?): Unit = mutex.withLock {
        val entity = dao.getHabit(id) ?: return@withLock
        val newName = name.trim().ifEmpty { entity.name }
        var updated = entity.copy(
            name = newName,
            target = if (entity.kind == HabitKind.DAILY) HabitMath.toTarget(target) else entity.target,
        )
        if (entity.kind == HabitKind.ROUTINE) {
            val habit = entity.toDomain()
            val unchanged = habit.auto != null && auto != null && habit.auto.sameSettingsAs(auto)
            if (!unchanged) {
                saveFrozenAutoTaps(habit)
                updated = updated.withAuto(HabitMath.newAuto(habit.auto, auto, clock.millis(), clock.zone))
            }
        }
        dao.updateHabit(updated)
    }

    suspend fun delete(id: Long): Unit = mutex.withLock { dao.deleteHabit(id) }

    /** Slots filled on [date] (daily habits). Later days are locked. */
    suspend fun setDailyCount(id: Long, date: LocalDate, count: Int): Unit = mutex.withLock {
        if (date.isAfter(Dates.today(clock))) return@withLock
        val habit = dao.getHabit(id) ?: return@withLock
        if (habit.kind != HabitKind.DAILY) return@withLock
        val log = dao.getLog(id, date) ?: HabitLogEntity(habitId = id, date = date)
        dao.upsertLog(log.copy(count = count.coerceIn(0, habit.target)))
    }

    /** A manual +1 or −1 on a routine. Later days are locked. */
    suspend fun tap(id: Long, date: LocalDate, direction: TapDirection): Unit = mutex.withLock {
        if (date.isAfter(Dates.today(clock))) return@withLock
        val habit = dao.getHabit(id) ?: return@withLock
        if (habit.kind != HabitKind.ROUTINE) return@withLock
        val log = dao.getLog(id, date) ?: HabitLogEntity(habitId = id, date = date)
        dao.upsertLog(
            if (direction == TapDirection.PLUS) log.copy(plus = log.plus + 1) else log.copy(minus = log.minus + 1)
        )
        dao.insertTap(HabitTapEntity(habitId = id, date = date, direction = direction, at = clock.millis()))
    }

    /** Takes back the latest manual tap in [direction] on [date]. */
    suspend fun undoTap(id: Long, date: LocalDate, direction: TapDirection): Unit = mutex.withLock {
        val log = dao.getLog(id, date) ?: return@withLock
        val updated = if (direction == TapDirection.PLUS) log.copy(plus = maxOf(0, log.plus - 1))
        else log.copy(minus = maxOf(0, log.minus - 1))
        dao.upsertLog(updated)
        dao.getLatestTap(id, date, direction.name)?.let { dao.deleteTap(it.id) }
    }

    private suspend fun saveFrozenAutoTaps(habit: Habit) {
        val auto = habit.auto ?: return
        val today = Dates.today(clock)
        val sinceDate = Dates.dateOf(auto.since, clock)
        val afterFrozen = auto.frozenThrough?.plusDays(1)
        val from: LocalDate = if (afterFrozen != null && afterFrozen.isAfter(sinceDate)) afterFrozen else sinceDate
        if (from.isAfter(today)) return
        val logs = toDayLogs(dao.getLogs(habit.id, from, today), dao.getTaps(habit.id, from, today))
        val adds = HabitMath.freeze(habit, logs, clock.millis(), clock.zone)
        for ((date, count) in adds) {
            val log = dao.getLog(habit.id, date) ?: HabitLogEntity(habitId = habit.id, date = date)
            dao.upsertLog(
                if (auto.direction == TapDirection.PLUS) log.copy(autoPlus = log.autoPlus + count)
                else log.copy(autoMinus = log.autoMinus + count)
            )
        }
    }

    private fun toDayLogs(logs: List<HabitLogEntity>, taps: List<HabitTapEntity>): Map<LocalDate, DayLog> {
        val tapsByDate = taps.groupBy { it.date }
        val dates = logs.map { it.date }.toSet() + tapsByDate.keys
        val logsByDate = logs.associateBy { it.date }
        return dates.associateWith { date ->
            val log = logsByDate[date]
            val dayTaps = tapsByDate[date].orEmpty()
            DayLog(
                count = log?.count ?: 0,
                plus = log?.plus ?: 0,
                minus = log?.minus ?: 0,
                autoPlus = log?.autoPlus ?: 0,
                autoMinus = log?.autoMinus ?: 0,
                plusAt = dayTaps.filter { it.direction == TapDirection.PLUS }.map { it.at },
                minusAt = dayTaps.filter { it.direction == TapDirection.MINUS }.map { it.at },
            )
        }
    }
}
