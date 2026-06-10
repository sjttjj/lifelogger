# Lifelogger Reminder, Calendar, and Notification Server Contract

Date: 2026-06-09

## Context

The Android app has recently added a more complete reminder/calendar workflow:

- Reminder sync from the Ubuntu server.
- Reminder cache and local notification scheduling.
- A new Life Calendar screen with readable agenda rows and a compact month map.
- Reminder end-date support for multi-day events.
- Needs Review reminder cancellation with confirmation.
- HTML rendering support for session notes and daily summaries.
- App-side multiple configurable push notifications per reminder, including the option for no notifications.

This document describes what the app already supports, what the server should return today, and what new server behavior is needed for seamless integration.

## Current App Behavior

### Reminder Sync

The app currently syncs upcoming reminders with:

```http
GET /api/reminders/upcoming?start={utc_iso}&end={utc_iso}&status=pending
```

The app also syncs Needs Review reminders with:

```http
GET /api/reminders?status=pending&needs_review=true
```

The Android app parses a response shaped like:

```json
{
  "reminders": [
    {
      "id": 42,
      "source_segment_id": null,
      "kind": "event",
      "title": "Dentist appointment",
      "description": "Clinic",
      "status": "pending",
      "needs_review": false,
      "timezone": "Australia/Sydney",
      "scheduled_at_local": "2026-06-10T17:00:00+10:00",
      "scheduled_at_utc": "2026-06-10T07:00:00Z",
      "end_at_local": "2026-06-12T23:59:00+10:00",
      "end_at_utc": "2026-06-12T13:59:00Z",
      "schedule_precision": "datetime",
      "used_default_time": false,
      "location": null,
      "people": null,
      "amount": null,
      "recurrence_text": null,
      "notification_jobs": [
        {
          "id": 9001,
          "reminder_id": 42,
          "notify_at_utc": "2026-06-10T06:00:00Z",
          "notification_title": "Dentist appointment",
          "notification_body": "Clinic",
          "channel": "push",
          "status": "pending"
        }
      ]
    }
  ]
}
```

The app can already schedule multiple local Android push notifications if the server returns multiple pending push `notification_jobs`.

### Life Calendar

The app now uses reminder dates to populate a Life Calendar.

Relevant fields:

- `scheduled_at_local`: start datetime in local timezone, with offset.
- `scheduled_at_utc`: start datetime in UTC.
- `end_at_local`: optional end datetime in local timezone, with offset.
- `end_at_utc`: optional end datetime in UTC.

If `end_at_local` is present, the app treats the reminder as a multi-day calendar item and displays it on every local date from `scheduled_at_local` through `end_at_local`, inclusive.

If end fields are missing or null, the app treats the reminder as a single-date item.

### Reminder Edit / Review

The app can patch reminder details with:

```http
PATCH /api/reminders/{id}
```

Current app patch fields may include:

```json
{
  "title": "Dentist appointment",
  "description": "Clinic",
  "kind": "event",
  "status": "pending",
  "needs_review": false,
  "timezone": "Australia/Sydney",
  "scheduled_at_local": "2026-06-10T17:00:00+10:00",
  "scheduled_at_utc": "2026-06-10T07:00:00Z",
  "end_at_local": "2026-06-12T23:59:00+10:00",
  "end_at_utc": "2026-06-12T13:59:00Z",
  "schedule_precision": "datetime",
  "used_default_time": false
}
```

If the app clears the end date, it sends:

```json
{
  "end_at_local": null,
  "end_at_utc": null
}
```

The server should persist this as a cleared end date.

### Needs Review Cancellation

The app now lets the user cancel Needs Review reminders after a confirmation dialog.

The app sends:

```http
PATCH /api/reminders/{id}
```

```json
{
  "status": "cancelled",
  "needs_review": false
}
```

Expected server behavior:

- Set `status = cancelled`.
- Set `needs_review = false`.
- Remove it from `GET /api/reminders?status=pending&needs_review=true`.
- Remove it from upcoming pending reminders.
- Cancel, delete, or stop returning pending notification jobs for that reminder.

## App-Side Feature Implemented: Multiple Notification Controls

The Android app now lets users configure notifications per reminder:

- No notification.
- At reminder time.
- Up to 3 custom notifications before the reminder start time.

Examples:

- `2 days before`
- `1 day before`
- `1 hour before`

Notifications are anchored to the reminder start time, not the end date.

Multi-day reminder end dates are for calendar display/duration. Notification offsets should use `scheduled_at_utc` unless a future contract explicitly supports end-date notifications.

## Required Server Change Still Needed

Older app code could create a single notification job via:

```http
POST /api/reminders/{id}/notifications
```

That is not enough for clean multiple-notification editing, because repeated edits can create duplicate jobs unless old jobs are replaced.

The current Android app now calls the replace-style endpoint below when saving reminder notification settings.

Please implement this replace-style endpoint:

```http
PUT /api/reminders/{id}/notifications
```

### Request: Notifications Enabled

```json
{
  "notifications_enabled": true,
  "jobs": [
    {
      "notify_at_utc": "2026-06-08T07:00:00Z",
      "notification_title": "Dentist appointment",
      "notification_body": "Clinic",
      "channel": "push"
    },
    {
      "notify_at_utc": "2026-06-09T07:00:00Z",
      "notification_title": "Dentist appointment",
      "notification_body": "Clinic",
      "channel": "push"
    },
    {
      "notify_at_utc": "2026-06-10T06:00:00Z",
      "notification_title": "Dentist appointment",
      "notification_body": "Clinic",
      "channel": "push"
    }
  ]
}
```

### Request: No Notifications

```json
{
  "notifications_enabled": false,
  "jobs": []
}
```

### Server Behavior

For `PUT /api/reminders/{id}/notifications`, the server should:

- Replace all existing pending notification jobs for that reminder with the supplied jobs.
- If `notifications_enabled = false`, cancel/delete/mark cancelled all pending notification jobs for that reminder.
- Preserve historical fired/delivered jobs if the server needs audit history, but do not return old fired/cancelled jobs as pending jobs.
- Create stable job IDs for new jobs.
- Return the updated reminder, including the current `notification_jobs`.

Recommended response:

```json
{
  "reminder": {
    "id": 42,
    "title": "Dentist appointment",
    "status": "pending",
    "needs_review": false,
    "scheduled_at_local": "2026-06-10T17:00:00+10:00",
    "scheduled_at_utc": "2026-06-10T07:00:00Z",
    "end_at_local": null,
    "end_at_utc": null,
    "notification_jobs": [
      {
        "id": 9001,
        "reminder_id": 42,
        "notify_at_utc": "2026-06-09T07:00:00Z",
        "notification_title": "Dentist appointment",
        "notification_body": "Clinic",
        "channel": "push",
        "status": "pending"
      }
    ]
  }
}
```

The app can also tolerate the normal reminders list shape after a resync:

```json
{
  "reminders": [
    {
      "id": 42,
      "notification_jobs": []
    }
  ]
}
```

## Notification Job Rules

Each `notification_job` returned to the app should include:

```json
{
  "id": 9001,
  "reminder_id": 42,
  "notify_at_utc": "2026-06-10T06:00:00Z",
  "notification_title": "Dentist appointment",
  "notification_body": "Clinic",
  "channel": "push",
  "status": "pending"
}
```

Rules:

- `id` must be stable and unique.
- `notify_at_utc` must be valid ISO 8601 UTC.
- `channel` should be `push` for Android notifications.
- `status` should be `pending` for notifications the app should schedule.
- Cancelled, delivered, failed, or historical jobs should not be returned as pending jobs.
- Jobs in the past should generally not be created, unless intentionally used for immediate/recent notification behavior.

The app filters local scheduling to:

- `channel = push`
- `status = pending`
- `notify_at_utc` in the future, or recently due within the app's due window.

## Reminder End-Date Contract

Please support these fields on reminder responses and patches:

```json
{
  "end_at_local": "2026-06-12T23:59:00+10:00",
  "end_at_utc": "2026-06-12T13:59:00Z"
}
```

Validation recommendations:

- `end_at_utc` should be equal to or later than `scheduled_at_utc`.
- If `end_at_local` is present, `scheduled_at_local` should also be present.
- If either end field is explicitly null in a patch, clear the reminder end date.
- Existing reminders may omit these fields; the app treats missing values as single-date reminders.

## Timezone Contract

The app expects local reminder display to use Australia/Sydney behavior, including daylight savings.

Preferred:

- Preserve `timezone`, e.g. `Australia/Sydney`.
- Return local timestamps with the correct offset for the date:
  - AEST: `+10:00`
  - AEDT: `+11:00`
- Return UTC timestamps that match the local timestamp instant.

Example:

```json
{
  "timezone": "Australia/Sydney",
  "scheduled_at_local": "2026-12-10T17:00:00+11:00",
  "scheduled_at_utc": "2026-12-10T06:00:00Z"
}
```

## Session Notes / Summary HTML Contract Already Added

The app now supports rendered HTML fields for notes and summaries.

Session endpoint:

```http
GET /api/sessions/{date}/{filename}
```

Response:

```json
{
  "date": "2026-06-05",
  "filename": "2026-06-05_meeting_1523_session.md",
  "content": "## Meeting Details\n\n**Date:** ...",
  "content_html": "<h2>Meeting Details</h2>\n<p><strong>Date:</strong> ...</p>"
}
```

Summary endpoint:

```http
GET /api/summaries/{date}
```

Response:

```json
{
  "summary": "## Daily Summary...",
  "summary_html": "<h2>Daily Summary...</h2>"
}
```

Server guidance:

- Return valid lightweight HTML in `content_html` / `summary_html`.
- Convert markdown bold, headings, and lists into HTML.
- Keep raw markdown fields for fallback.
- Avoid returning raw markdown in the HTML fields.

## Acceptance Criteria For Server Work

- Upcoming reminder responses include `notification_jobs` arrays.
- Multiple pending push jobs can be returned for one reminder.
- Android app schedules multiple jobs without duplicates after resync.
- `PUT /api/reminders/{id}/notifications` replaces existing pending jobs.
- `notifications_enabled = false` results in no pending notification jobs for that reminder.
- Android reminder saves that include notification settings return 2xx from both `PATCH /api/reminders/{id}` and `PUT /api/reminders/{id}/notifications`.
- Reminder end dates are accepted on patch and returned on sync.
- Explicit null end dates clear existing end dates.
- Needs Review cancellation removes the reminder from Needs Review and pending upcoming responses.
- Australia/Sydney local timestamps reflect daylight savings correctly.
- Session notes and summaries return usable HTML fields.
