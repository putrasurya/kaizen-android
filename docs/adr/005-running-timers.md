# ADR-005: Running timers are timestamps plus an exact alarm, not a ticking service

## Status
Accepted (2026-11-17)

## Context
Timers are the most used feature. A running timer must finish on time with
the screen off, with the app closed, and even if Android kills the app to
save memory. Several timers can run at once. When a round ends: play a sound,
add a rep, add to the focus log, and (with Pomodoro) start the break.

## Options considered
| Option | Survives app being killed? | Battery | Complexity |
|---|---|---|---|
| A. Count down in the ViewModel | No: the timer dies with the app | Good | Low |
| B. Foreground service that ticks every second | Mostly, but some phones (Xiaomi, Oppo…) still kill it | Poor | Medium |
| **C. Save the end time; exact alarm wakes the app when it's due** | Yes: the alarm is kept by Android | Good | Medium |

## Decision
Option C. **The saved end time is the truth; nothing ticks in the background.**

- **Start:** save `endsAt = now + remaining` in `timer_runs`, schedule an
  exact alarm (AlarmManager) for `endsAt`, show an ongoing notification whose
  countdown Android draws itself (`setUsesChronometer` +
  `setChronometerCountDown`), so the app never updates it every second.
- **Pause:** save `remainingMs = endsAt - now`, clear `endsAt`, cancel the alarm.
- **Screen:** shows `endsAt - now`, recomputed every second only while visible.
- **Alarm fires:** a receiver adds the rep and focus time, posts a "round
  done" notification with sound, and, if Pomodoro auto-break is on, saves the
  break's `endsAt` and schedules the next alarm.
- **Phone restarted:** a `BOOT_COMPLETED` receiver re-schedules alarms for
  running timers; any that ended while the phone was off are finished then.
- **Notification buttons:** Pause / Resume / Stop, handled by a receiver
  without opening the app.

## Consequences
- Easier: works when the app is killed; almost no battery use; the same idea
  as the web app's countdown (start time + elapsed) and habit auto-taps.
- Easier: reuses the prototype's pieces (exact alarm, receiver, notification).
- Harder: needs the "Alarms & reminders" permission (already requested by
  the prototype) and `RECEIVE_BOOT_COMPLETED`.
- Harder: on Android 14+ the user can swipe the ongoing notification away;
  the timer still finishes because the alarm doesn't depend on it.
- If testing shows alarms arriving late on some phones, add a foreground
  service only while a timer runs (option B as a backup, not a replacement).
