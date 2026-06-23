# Recurring Reminders App Design

Date: 2026-06-23

## Goal

Improve how the Android app handles reminders that the server identifies as recurring, without changing one-off reminder editing in this pass.

The app should:

- Keep all existing reminder detail fields intact.
- Make recurring reminder controls feel like part of the normal edit form.
- Fix the current squashed repeat action buttons.
- Infer a first visible occurrence when the server marks a reminder as recurring but omits a concrete scheduled occurrence and the rule is unambiguous.
- Leave ambiguous recurrence rules in Needs Review.

Server-side recurrence creation and full recurrence conversion/editing endpoints will be handled in a later session.

## Current Problems

1. A WhatsApp reminder such as "I need to test our app every Monday" can arrive as recurring but with no `scheduled_at_local`, `scheduled_at_utc`, or `recurrence_occurrence_local`.
2. The app then has no concrete date to place in the calendar and shows the reminder in Needs Review.
3. The reminder detail screen shows a separate colored `Repeats` section that does not fit with the rest of the form.
4. The repeat action buttons are laid out horizontally, so on narrow screens one button becomes compressed and hard to read.

## Scope

In scope:

- Existing recurring reminders only.
- App-side fallback date inference for clear recurrence rules.
- UI polish for recurring reminder fields and series actions.
- Tests for recurrence inference behavior.

Out of scope for this pass:

- Showing repeat controls for one-off reminders.
- Converting a one-off reminder into a repeating reminder.
- Creating or updating recurrence series on the server.
- Full server contract work. That is planned for the next session.

## Recurrence Date Inference

The server remains the preferred source of truth. The app only infers a first occurrence when all of these are true:

- The reminder is recurring.
- The reminder has no concrete scheduled date from `scheduled_at_local`, `scheduled_at_utc`, or `recurrence_occurrence_local`.
- The recurrence rule is specific enough to compute the closest occurrence that has not passed yet.

The inferred occurrence should use the device timezone unless the reminder/recurrence includes a timezone.

### Weekly Rules

For weekly recurrence with a specific day of week:

- If today is that day, use today.
- If that day has already passed this week, use the same day next week.
- If that day is later this week, use that upcoming day.

Example: today is Tuesday, 2026-06-23. A weekly Monday reminder starts on Monday, 2026-06-29.

### Monthly Rules

For monthly recurrence with a specific day of month:

- Use that day in the current month if it has not passed.
- Otherwise use that day in the next valid month.
- If the target month has fewer days than the requested day, use the last valid day only if that is already how the server models the recurrence; otherwise keep Needs Review to avoid guessing.

Example: monthly on the 6th starts on the closest 6th that has not passed, regardless of weekday.

For monthly weekday rules:

- Keep Needs Review when the rule says only "monthly on Monday" without an ordinal or day-of-month.
- Keep Needs Review for ordinal weekday wording such as "first Monday of the month" or "second Monday of the month" in this pass, because the server does not yet support that structured recurrence shape.
- The app should not guess whether a vague monthly weekday means first Monday, every Monday, last Monday, or another pattern.

## Reminder Detail UI

All existing fields remain intact and in their current flow:

- Title
- Description
- Kind
- Date
- Time
- End date
- Notifications
- Custom notification offsets
- Status
- Save

For reminders already marked recurring, add recurrence controls styled like the existing dropdown fields:

- `Repeat frequency`
- `Repeat day`

`Repeat day` is shown when the recurrence frequency needs a day selection, such as weekly rules. Monthly rules may later require a specific day-of-month or ordinal weekday control, but this pass should not overbuild unsupported server editing.

The old standalone colored `Repeats` panel should be replaced with neutral form controls and a small explanatory note only when useful.

## Series Actions

For recurring reminders only, show full-width stacked action buttons below the repeat fields:

- `Cancel this time`
- `Stop repeats`
- `Archive all repeats`

Each button keeps the existing confirmation dialog behavior. The buttons should not be arranged horizontally.

## Data Flow

When loading a reminder:

1. Read cached/server reminder fields through the existing load path.
2. Determine the display date using this priority:
   - `scheduled_at_local`
   - `recurrence_occurrence_local`
   - app-inferred recurrence occurrence
3. If an app-inferred date is used, populate the Date field and allow normal confirmation/save behavior.
4. If inference is impossible, leave the reminder in Needs Review and require the user to choose a date.

When saving:

- Existing patch behavior remains unchanged.
- If the user confirms a Needs Review recurring reminder after an inferred date is populated, the app sends the scheduled date just like a manually selected date.
- Repeat frequency/day dropdowns are display/editing scaffolding for existing recurring reminders in this pass; server-side recurrence update semantics are deferred unless an existing endpoint already safely supports them.

## Error Handling

- Invalid or incomplete recurrence rules do not crash the screen.
- Ambiguous rules remain Needs Review.
- Failed series actions continue to show a snackbar error.
- If recurrence metadata is missing but `recurrence_text` exists, display the text but do not infer unless the structured rule is specific enough.

## Testing

Add focused unit tests for recurrence occurrence inference:

- Weekly Monday when today is Monday returns today.
- Weekly Monday when today is Wednesday returns next Monday.
- Monthly day-of-month returns the closest future matching day.
- Monthly weekday wording, including ordinal wording such as first Monday, returns no inference in this pass.
- Missing recurrence data returns no inference.

Run:

- `.\gradlew.bat :app:testDebugUnitTest`
- `.\gradlew.bat :app:assembleDebug`

## Follow-Up Server Work

Next session, define and implement the server-side recurrence contract so the server creates concrete occurrences for recurring reminders. The app fallback should remain for older or incomplete responses, but the server should become canonical.

Server work should also cover:

- Creating recurrence from one-off reminders.
- Updating recurrence frequency/day/monthly pattern.
- Returning unambiguous structured recurrence fields for weekly and day-of-month monthly rules.
- Later, if ordinal weekday monthly rules are added on the server, define a new structured field for that pattern before the app infers it.
