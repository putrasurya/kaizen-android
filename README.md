# Kaizen for Android

Native Android version of [Kaizen](https://github.com/putrasurya/kaizen), written in Kotlin with Jetpack Compose.

See [docs/architecture.md](docs/architecture.md) for the design, the roadmap and the decision records (ADRs).

## Current stage: Phase 2b, timer reps

**Timers** work: add a timer to one or more weekdays, start, pause, resume and
reset it. The screen opens on today's weekday. A running timer keeps going when
the app is closed: its end time is saved, an exact alarm rings when it's due
(with sound), and a notification shows the countdown with Pause / Reset
buttons. After a phone restart, running timers are put back. (ADR-005)

Each finished round adds a **rep** on the date it finished. Cards show
"3 reps today · 1 h 15 min · 42 all time"; on another weekday, the reps of
that day in the current week.

The other tabs are placeholders until their phase lands; the Phase 0 reminder
prototype lives under **More → Reminder test**.

```
com.putrasurya.kaizen
├── core/        AppContainer, Room database, dates, theme, navigation, boot receiver
├── timers/
│   ├── domain/  TimerMath: countdown rules (unit-tested)
│   ├── data/    tables, DAO, TimerRepository (database + alarm + notification in step)
│   ├── alarm/   exact alarms, notifications, receiver for alarm and buttons
│   └── ui/      Timers screen, weekday row, add/edit dialog
├── more/        the More tab
└── reminder/    alarm, notification, voice + the test screen
```

## How to run

1. Open this folder in **Android Studio** and let Gradle sync (it may offer to update versions; that's fine).
2. Run it on a **real phone** (TextToSpeech voices are often missing on emulators).
3. Run the unit tests: `./gradlew test` (or right-click `app/src/test` → Run tests).
4. Open **More → Reminder test**, tap **Allow notifications** and **Allow alarms & reminders**.
5. Tap **Speak now (test)** to check the voice works.
6. Tap **Remind me in 1 minute**, close the app (swipe it away), and wait.

The voice prefers Indonesian. If you don't hear it, check *Settings → System → Languages → Text-to-speech output* and install the Indonesian voice data.

## Next steps

See the roadmap in [docs/architecture.md](docs/architecture.md#roadmap). Next up: **Phase 2c, Pomodoro breaks**.
