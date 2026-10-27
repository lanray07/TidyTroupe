# Domain engine

This module has no Android dependencies. Dates are local calendar dates; callers convert notification dates to instants using the device's current `ZoneId` at scheduling time. Changing timezone does not rewrite a task's calendar day.

`ChoreEngine.isDue` decides whether a specific date is an occurrence. Weekly schedules align to Monday-based calendar weeks anchored at the first due date. Monthly schedules preserve the original day and clamp to the last day in shorter months. `nextDue` is inclusive. Paused and archived tasks have no active occurrences. A passed one-time task stays in storage; callers may show it as overdue and pass its original due date to complete that occurrence.

Completions are keyed by `(choreId, occurrence)`. Submissions are idempotent and snapshot the assigned profile and points. Pending submissions contribute no spendable points. Reassigning or editing a chore does not change past ownership or points. Undo removes the completion; already spent points remain spent, so the balance can become negative after a correction. A new redemption is rejected unless sufficient points remain. Redemptions require unique request IDs for idempotence.

The Android repository must perform access checks inside mutations, persist changes transactionally, constrain child actors to their own assignments, validate imported records, and apply unique database keys. Domain role parameters express business rules; they do not authenticate a caller. Never trust imported `pointsAwarded` values without parent-authorised import and validation.

Voice parsing produces intents only. English is the implemented command dictionary; other languages explicitly return `UnsupportedLanguage`. All mutation intents request confirmation. `Complete(null)` means the selected child's next task and must be resolved in a bounded child context. A weekday without a date means its next occurrence, including the following week if that weekday is today. Microphone capability, OS/service recognition language support, permission, transcript preview, TTS support, child enablement and command execution belong in the Android layer. No claim is made about offline recognition.

Run `gradlew :core:test` for calendar, point ledger and parser tests.
