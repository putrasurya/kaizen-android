# Kaizen for Android

Native Android version of [Kaizen](https://github.com/putrasurya/kaizen), written in Kotlin with Jetpack Compose.

See [docs/architecture.md](docs/architecture.md) for the design, the roadmap and the decision records (ADRs).

## Current stage: reminder prototype

This first version only proves the hardest part of the idea: **waking up on time while the app is closed, showing a notification, and speaking the reminder out loud.**

```
AlarmManager (exact time)
   └─> ReminderReceiver        Android wakes the app
         └─> SpeakService      foreground service
               ├─ notification (stays after speaking)
               └─ TextToSpeech  (skipped on silent / vibrate)
```

| File | What it does |
|---|---|
| `reminder/ReminderScheduler.kt` | Asks AlarmManager to wake Kaizen at an exact time |
| `reminder/ReminderReceiver.kt` | Runs when the alarm fires; later it will read tasks and ask the AI |
| `reminder/SpeakService.kt` | Shows the notification, speaks with TextToSpeech, then stops |
| `reminder/Notifications.kt` | Notification channel and the reminder notification |
| `MainActivity.kt` | Test screen: permissions, "remind me in 1 minute", "speak now" |

## How to run

1. Open this folder in **Android Studio** and let Gradle sync (it may offer to update versions; that's fine).
2. Run it on a **real phone** (TextToSpeech voices are often missing on emulators).
3. In the app, tap **Allow notifications** and **Allow alarms & reminders**.
4. Tap **Speak now (test)** to check the voice works.
5. Tap **Remind me in 1 minute**, close the app (swipe it away), and wait.

The voice prefers Indonesian. If you don't hear it, check *Settings → System → Languages → Text-to-speech output* and install the Indonesian voice data.

## Next steps

- Store tasks locally with Room, so the receiver can read what's unfinished
- Call the AI through a small backend (never put the API key in the app)
- Re-schedule alarms after a reboot (`BOOT_COMPLETED`)
- Notification actions: Done / Move to journal / Snooze
- Settings: voice on/off, quiet hours
