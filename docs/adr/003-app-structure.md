# ADR-003: One module, organised by feature, with plain Kotlin rules

## Status
Proposed

## Context
Kaizen will have six features (timers, habits, todos, journal, notes,
milestones) built one at a time by one person. The code must stay easy to
find and change, and the rules (Pomodoro breaks, habit balance, weekly
reset) must be testable without a phone.

## Options considered
| Option | Good | Bad |
|---|---|---|
| **A. One Gradle module, package per feature** | Simple build, easy to move code | Boundaries are kept by habit, not by the compiler |
| B. One Gradle module per feature | The compiler enforces boundaries | Much more build setup; slower to change early on |
| C. Full "clean architecture" (use-case class per action, interfaces everywhere) | Very testable | Lots of pass-through code for simple screens |

## Decision
Option A. One `app` module, one package per feature, and three small layers
inside each feature:

```
com.putrasurya.kaizen
├── core/            clock, date helpers, database, notifications, navigation
├── timers/
│   ├── domain/      plain Kotlin rules: no Android imports (Pomodoro, focus log)
│   ├── data/        Room entities, DAO, repository
│   └── ui/          Compose screens + ViewModel
├── habits/  todos/  journal/  notes/  milestones/   (same shape)
└── reminder/        alarms, notification and voice (from the prototype)
```

Rules:
- `domain` never imports Android, Room or Compose, so its rules are tested
  with plain JUnit tests.
- `ui` talks to the `data` repository, never to the DAO directly.
- Features don't reach into each other's `data`; shared things go in `core`.
- Time always comes from an injectable `Clock`, so tests can fake "now".
- Dependencies are created by hand in one `AppContainer` (no Hilt yet).
- Screens use Navigation Compose with a bottom bar:
  Timers · Habits · Todos · Journal · More (notes, milestones).

## Consequences
- Easier: one place per feature; rules are unit-tested like the web app's tests.
- Easier: no extra libraries to learn at the start (Hilt, multi-module builds).
- Harder: nothing stops a shortcut across features; code review has to.
- Reversible: a package can later become its own module, and the
  `AppContainer` can be swapped for Hilt if wiring by hand gets painful.
