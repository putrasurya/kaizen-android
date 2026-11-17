# ADR-002: Local-only data, fresh start, no sync

## Status
Accepted (2026-11-17)

## Context
The web app (putrasurya/kaizen) syncs through Google Drive and has its own data
format (state version 9). The Android app will be the only Kaizen that keeps
growing. Losing the old web data is acceptable.

## Options considered
- **Keep the web format and Drive sync**: phone and web share data, but the
  sync engine is the most complex part of the web app to port.
- **Fresh start, import a web backup once**: keeps history, but ties the
  first version to the old format.
- **Fresh start, local-only**: simplest; the data model is designed for Android.

## Decision
Fresh start, local-only. No Google Drive sync, no import from the web app.
Android Auto Backup (`android:allowBackup="true"`) stays on for basic
protection. The web app is used as a reference for behaviour, not for data.
Until a feature exists on Android, the web app keeps being used for it.

## Consequences
- Easier: no sync engine, no sign-in, no shared format to keep compatible.
- Easier: the data model can use proper database tables (see ADR-004).
- Harder: data lives on one phone; Auto Backup is the only safety net until a
  manual export is added.
- Reversible: sync can be added later as a separate layer over the database.
