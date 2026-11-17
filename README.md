# Kaizen for Android

Native Android version of [Kaizen](https://github.com/putrasurya/kaizen), written in Kotlin with Jetpack Compose.

See [docs/architecture.md](docs/architecture.md) for the design, the roadmap and the decision records (ADRs).

## Current stage: Phase 1, foundation

The app opens with a bottom bar (Timers · Habits · Todos · Journal · More).
The feature tabs are placeholders until their phase lands; the Phase 0
reminder prototype lives under **More → Reminder test**.

```
com.putrasurya.kaizen
├── core/        AppContainer, Room database, dates, theme, navigation
├── timers/      data/ (tables for Phase 2)
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

See the roadmap in [docs/architecture.md](docs/architecture.md#roadmap). Next up: **Phase 2a, timers**.
