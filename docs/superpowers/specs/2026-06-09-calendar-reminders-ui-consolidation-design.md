# Calendar + Reminders UI Consolidation Design

## Goal

Merge the upcoming-reminders and needs-review workflows into the Calendar screen, remove the duplicated RemindersScreen, remove the unused home-screen Prompts button, and simplify the home grid.

## Scope

- Remove Prompts button from home screen (prompt system itself untouched)
- Remove Reminders button and RemindersScreen (data layer untouched)
- Rename "Life Calendar" → "Calendar"
- Shorten range labels: "Week" → "W", "Month" → "M"
- Add a Calendar / Needs Review toggle to the Calendar screen
- Needs Review list replaces agenda + month map when active; range selectors disabled
- Crossfade transition between the two modes
- Home grid becomes 2 rows × 3 columns (Record/LifeLog/Calendar, Recordings/blank/Settings)

## Architecture

The Calendar screen (`LifeCalendarScreen.kt`) gains a view-mode toggle and hosts the transplanted Needs Review UI. The `RemindersScreen.kt` file is deleted. The `ReminderSyncManager` and `Reminder` data layer are untouched. `MainActivity.kt` removes the old routes/callbacks and re-lays-out the home grid.

## Files Changed

| File | Change |
|------|--------|
| `MainActivity.kt` | Remove Prompts/Reminders buttons, remove `composable("reminders")`, remove `onOpenReminders`, switch dashboard to Calendar, reflow grid to 2×3 |
| `LifeCalendarScreen.kt` | Rename, add view toggle, add Needs Review UI + cancel dialog, crossfade, disable ranges in review mode |
| `CalendarAgendaModels.kt` | `Week("W")`, `Month("M")` |
| `RemindersScreen.kt` | **Deleted** |

## Out of Scope

- Prompt system internals (Room cache, sync, server endpoints)
- Reminder data models, sync, notifications
- EditReminderScreen, recording, squeeze, settings