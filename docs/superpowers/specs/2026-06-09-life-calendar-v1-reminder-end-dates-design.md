# Life Calendar v1 and Reminder End Dates Design

## Goal

Add a general Life Calendar surface that can eventually show reminders, habits, workouts, financial alerts, and other life events. The first implementation populates reminders only, while the data model and UI filters are designed so future item types can plug in cleanly.

Also add support for optional reminder end dates so reminders can represent multi-day events.

## Product Scope

### In Scope for App v1

- Add a Calendar entry point from the home screen.
- Add a calendar screen with three views:
  - Month
  - Week
  - List
- Add item-type filters:
  - Reminders enabled for v1.
  - Habits, Workouts, Financial Alerts shown as disabled or empty filter options for future use.
- Populate the calendar from synced/cached reminders.
- Render reminders as calendar items.
- Support reminders with optional end date/time when the server returns those fields.
- Preserve existing reminders that only have `scheduled_at_*`.
- Add confirm dialog when cancelling a Needs Review reminder.

### Out of Scope for App v1

- Habit creation.
- Workout tracker.
- Financial alert API.
- LLM query API.
- Server-side implementation of new reminder end-date fields.
- Recurring calendar expansion for habits or repeated reminders.
- Drag-and-drop calendar editing.

## Calendar Concept

Introduce a small app-side calendar item abstraction:

```kotlin
data class LifeCalendarItem(
    val id: String,
    val sourceType: LifeCalendarItemType,
    val sourceId: Long,
    val title: String,
    val description: String?,
    val startUtc: String?,
    val startLocal: String?,
    val endUtc: String?,
    val endLocal: String?,
    val status: String,
    val needsReview: Boolean
)

enum class LifeCalendarItemType {
    Reminder,
    Habit,
    Workout,
    FinancialAlert
}
```

For v1, `LifeCalendarItem` is derived from `Reminder`. Future modules should add their own mappers into this calendar model rather than making the calendar know each module's internal schema.

## Reminder Date Semantics

Existing reminder fields continue to mean:

- `scheduled_at_local`: primary start local datetime/date.
- `scheduled_at_utc`: primary start UTC datetime.
- `schedule_precision`: `date` or `datetime`.

New optional end fields:

- `end_at_local`: local ISO 8601 end datetime/date with offset.
- `end_at_utc`: UTC ISO 8601 end datetime.

If `end_at_*` is missing, the reminder is a single-day/single-time item. If end fields are present, the calendar renders the item across every day from start date through end date.

If a reminder has no start date, it appears in Needs Review but does not appear on the calendar until dated.

## Calendar Views

### Month View

- Shows a standard month grid.
- Each day cell can show a compact count and up to two visible item titles.
- Multi-day reminders appear on each covered date.
- Tapping a day opens the List view filtered to that date.
- Tapping an item opens the existing reminder edit screen.

### Week View

- Shows a compact seven-day agenda on mobile: one vertical section per day for the selected week.
- Each day shows the reminders for that date.
- Multi-day reminders appear on each covered day.
- Tapping an item opens reminder edit.

### List View

- Shows calendar items grouped by date.
- This view reuses the current reminder-list mental model but is filter-aware and calendar-aware.
- Useful for scanning all upcoming items.

## Filters

The calendar has filter chips or a filter row:

- Reminders
- Habits
- Workouts
- Financial Alerts

Only Reminders are active in v1. Future filters can remain visible but disabled, or hidden behind a "Coming soon" treatment. The preferred v1 treatment is visible-but-disabled so the screen clearly becomes the Life Calendar hub.

## Home Screen Integration

Add a new Home action button:

- Label: `Calendar`
- Icon: calendar/event icon.
- Opens the Life Calendar screen.

Keep the existing six-button grid and add a third row for future expansion. V1 third row contains:

- `Calendar`

Future third-row slots can be used for Habits and Workout when those modules are built.

## Needs Review Cancel Flow

Current issue: Needs Review reminders cannot be cancelled from the list because the Needs Review tab passes a no-op patch handler.

Desired behavior:

1. User taps `Cancel` on a Needs Review reminder.
2. App shows confirm dialog:
   - Title: `Cancel reminder?`
   - Body: `This will remove it from Needs Review and mark it cancelled.`
   - Buttons: `Keep` and `Cancel reminder`
3. If confirmed, app calls existing `PATCH /api/reminders/{id}` with:

```json
{
  "status": "cancelled",
  "needs_review": false
}
```

4. App refreshes Needs Review.

Server work is only required if the existing endpoint rejects this patch for needs-review reminders.

## App Data Changes

Add optional fields to:

- `Reminder`
- `ReminderPatch`
- `ReminderEntity`
- Room migration

Fields:

```kotlin
val endAtLocal: String?
val endAtUtc: String?
```

JSON field names:

```json
"end_at_local": "2026-06-12T17:00:00+10:00",
"end_at_utc": "2026-06-12T07:00:00Z"
```

Room migration:

- Increment database version.
- Add nullable `endAtLocal TEXT`.
- Add nullable `endAtUtc TEXT`.

## Server Contract Notes

### Reminder Response Fields

Please add optional end-date fields to every endpoint that returns reminders:

- `GET /api/reminders/upcoming`
- `GET /api/reminders?status=pending&needs_review=true`
- Any future reminder detail endpoint

Example:

```json
{
  "id": 42,
  "kind": "event",
  "title": "Conference",
  "description": "Multi-day event",
  "status": "pending",
  "needs_review": false,
  "timezone": "Australia/Sydney",
  "scheduled_at_local": "2026-06-10T09:00:00+10:00",
  "scheduled_at_utc": "2026-06-09T23:00:00Z",
  "end_at_local": "2026-06-12T17:00:00+10:00",
  "end_at_utc": "2026-06-12T07:00:00Z",
  "schedule_precision": "datetime",
  "used_default_time": false,
  "notification_jobs": []
}
```

Backward compatibility:

- Existing reminders may omit `end_at_local` and `end_at_utc`.
- App treats missing end fields as a single-date item.

### Reminder Patch Fields

Please accept these optional fields in `PATCH /api/reminders/{id}`:

```json
{
  "end_at_local": "2026-06-12T17:00:00+10:00",
  "end_at_utc": "2026-06-12T07:00:00Z"
}
```

If either end field is explicitly null, the server should clear the end date.

Validation recommendation:

- If `end_at_utc` is present, it should be equal to or later than `scheduled_at_utc`.
- If `end_at_local` is present, `scheduled_at_local` should also be present.
- Preserve the provided timezone or infer it from the local timestamp offset.

### Needs Review Cancellation

Please confirm `PATCH /api/reminders/{id}` supports cancelling Needs Review reminders with:

```json
{
  "status": "cancelled",
  "needs_review": false
}
```

Expected behavior:

- Reminder no longer appears in Needs Review.
- Reminder no longer appears in upcoming pending reminders.
- Existing pending notification jobs for that reminder should be cancelled or omitted from future sync responses.

### Calendar Query

For app v1, no new calendar endpoint is required. The app can use `GET /api/reminders/upcoming`.

Future optional server improvement:

```http
GET /api/calendar?start=<iso>&end=<iso>&types=reminder,habit,workout,financial_alert
```

Possible response:

```json
{
  "items": [
    {
      "id": "reminder:42",
      "source_type": "reminder",
      "source_id": 42,
      "title": "Conference",
      "description": "Multi-day event",
      "start_at_local": "2026-06-10T09:00:00+10:00",
      "start_at_utc": "2026-06-09T23:00:00Z",
      "end_at_local": "2026-06-12T17:00:00+10:00",
      "end_at_utc": "2026-06-12T07:00:00Z",
      "status": "pending"
    }
  ]
}
```

This future endpoint would let the server merge reminders, habits, workouts, and alerts into a single canonical calendar feed.

## Testing

App tests should cover:

- Reminder model parses missing end fields as null.
- Reminder model parses `end_at_local` and `end_at_utc`.
- Reminder patch serializes end fields.
- Calendar mapper expands multi-day reminder dates.
- Needs Review cancel calls a real patch path, not a no-op.

Manual checks:

- Calendar opens from Home.
- Month, Week, and List views switch correctly.
- Filters show Reminders active and future types disabled/empty.
- Reminder appears on the correct day.
- Multi-day reminder appears across each covered day when end fields are present.
- Needs Review cancel asks for confirmation before patching.
