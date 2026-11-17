# Kaizen for Android

Native Android version of [Kaizen](https://github.com/putrasurya/kaizen), written in Kotlin with Jetpack Compose.

See [docs/architecture.md](docs/architecture.md) for the design, the roadmap and the decision records (ADRs).

## Features

Everything from the web app's daily use is here (ADR-002: local-only, no sync).

- **Timers** per weekday that keep running when the app is closed (saved end
  time + exact alarm, ADR-005), with sound, notification countdown and
  Pause / Reset buttons. Reps per day, **Pomodoro** breaks (auto or wait,
  long break every N rounds, skip), and a **focus history** per activity:
  streak, this week vs last, 12-week heatmap.
- **Habits**: daily (fill N circles) and routines (− / + with a weekly
  balance and drift marker, undo). **Auto tap** every N minutes, worked out
  from the time, so it counts while the app is closed. 8-week history.
- **Todos**: a checklist per weekday that comes back every week; ticks count
  for this week only. Copy to other days. **Evening reminder** that says
  today's unfinished todos out loud; "Couldn't finish? Note it in today's journal".
- **Journal**: an entry per date, saved as you type, with how your habits
  went that day, and past entries.
- **Notes** (under More): quick notes for a weekday, or every day.
- **Milestones** (under More): what you reached this year, goals still to
  come, and what to do with last year's unreached goals.

```
com.putrasurya.kaizen
├── core/        AppContainer, Room database, dates, theme, navigation, boot receiver
├── timers/      domain (TimerMath, Pomodoro, FocusMath) · data · alarm · ui
├── habits/      domain (HabitMath: daily, routines, auto tap) · data · ui
├── todos/       domain (TodoMath) · data · reminder · ui
├── journal/     data · ui
├── notes/       notes (one file)
├── milestones/  domain · data · ui
├── more/        the More tab
└── reminder/    voice (TextToSpeech) service + the Phase 0 test screen
```

Rules live in `domain/` packages as plain Kotlin, covered by unit tests in
`app/src/test` (52 tests).

## How to run

1. Open this folder in **Android Studio** and let Gradle sync (it may offer to update versions; that's fine).
2. Run it on a **real phone** (TextToSpeech voices are often missing on emulators).
3. Run the unit tests: `./gradlew test` (or right-click `app/src/test` → Run tests).
4. Open **More → Reminder test**, tap **Allow notifications** and **Allow alarms & reminders**.
5. Tap **Speak now (test)** to check the voice works.
6. Tap **Remind me in 1 minute**, close the app (swipe it away), and wait.

The voice prefers Indonesian. If you don't hear it, check *Settings → System → Languages → Text-to-speech output* and install the Indonesian voice data.

## Next steps

All planned phases are done. The backlog (AI-written reminder messages) is in [docs/architecture.md](docs/architecture.md#roadmap).
