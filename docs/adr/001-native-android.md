# ADR-001: Build Kaizen as a native Android app (Kotlin + Jetpack Compose)

## Status
Accepted (2026-11-16)

## Context
Kaizen is moving from a web app to Android to use what the web can't do well:
exact-time reminders, notifications, work that happens while the app is closed,
and speaking out loud (TextToSpeech). Only Android is planned; no iPhone version.

## Options considered
- **Native (Kotlin + Jetpack Compose)**: direct access to AlarmManager,
  notifications, services and TextToSpeech; Google's docs and samples use it.
- **Flutter**: one codebase for Android + iOS, but background code runs in a
  separate isolate and depends on plugins; a new language (Dart).
- **React Native**: reuses React knowledge from the web app, but background
  work (Headless JS) is limited and needs native Kotlin modules anyway.

## Decision
Native Android with Kotlin and Jetpack Compose.

## Consequences
- Easier: every Android feature is available directly, no plugin or bridge.
- Easier: a prototype (exact alarm → notification → spoken message) already
  works on a real phone.
- Harder: an iPhone version would be a separate app.
