# ADR-004: Store data in Room, as logs keyed by date

## Status
Accepted (2026-11-17)

## Context
All data is local (ADR-002). Screens need live updates ("today's reps",
"this week's balance"), and reminders need queries like "unfinished todos
today". The web app kept one big JSON object; on Android we can do better.

## Options considered
| Option | Good | Bad |
|---|---|---|
| **A. Room (SQLite)** | Official, typed queries, `Flow` for live UI, migrations | Schema and migrations to maintain |
| B. One JSON file (DataStore) | Same shape as the web, little setup | Whole file rewritten on each change; queries by hand |
| C. SQLDelight | Plain SQL, multiplatform | Less common in Android guides, a second tool to learn |

## Decision
Option A, Room, following the web app's best idea: **keep logs, never reset
counters**. A "week" or "today" is a query over dated rows, so nothing has to
run at midnight or on Monday.

Conventions:
- Dates are local calendar dates stored as `"YYYY-MM-DD"` text
  (`java.time.LocalDate` through a type converter); weeks run Monday–Sunday.
- Moments are epoch milliseconds (`Long`).
- Each feature owns its tables. First tables (Phase 2, timers):

```
timers        id, title, weekday (1–7), durationSec, sortOrder,
              pomodoroBreakSec?, pomodoroLongBreakSec?, pomodoroLongEvery?, pomodoroAutoBreak?
timer_runs    timerId (PK), phase (FOCUS | BREAK | LONG_BREAK),
              endsAt? (running), remainingMs? (paused)          ← see ADR-005
timer_reps    timerId, date, count                 (PK timerId + date)
focus_log     date, activityKey, title, seconds, rounds   (PK date + activityKey)
```

`focus_log` is kept apart from `timers` (as on the web), so renaming or
deleting a timer never changes the past. Habits, todos and journal tables are
designed in their own phases.

## Consequences
- Easier: live screens via `Flow`, simple queries for reminders and history.
- Easier: no midnight or weekly reset jobs, so less background work.
- Harder: each schema change needs a Room migration (with a test).
