# Calendar Agenda Month Map Design

## Goal

Replace the current narrow 7-column calendar month view with a portrait-friendly calendar screen that keeps reminders readable while still showing where items sit in the month.

## Problem

On an upright phone, a 7-column month grid leaves each day cell too narrow to read reminder titles. The app needs a calendar view that:

- Shows reminder titles in readable rows.
- Preserves month-level visual context.
- Handles multi-day reminders.
- Handles multiple reminders on the same day without category-only colouring.

## Proposed Screen

Use one combined calendar screen instead of separate `Month`, `Week`, and `List` tabs.

The screen has:

- Top app bar: `Life Calendar`, back, refresh.
- Month/range row:
  - Subtle unboxed left chevron.
  - Current range label, e.g. `June 2026`.
  - Subtle unboxed right chevron.
  - Compact segmented range selector: `Week`, `Month`, `All`.
- Main content: scrollable agenda list grouped by date.
- Bottom overlay: compact persistent month map.

## Range Modes

`Week`:

- Agenda shows the selected week.
- Month map still shows the current month for context.
- Left/right arrows move by one week.

`Month`:

- Agenda shows items in the selected month.
- Month map shows the selected month.
- Left/right arrows move by one month.

`All`:

- Agenda shows all currently loaded reminders.
- Month map shows the month containing the first visible or selected item.
- Left/right arrows move the map month, not the full list range.

For v1, using loaded upcoming reminders is acceptable. No new server endpoint is required.

## Agenda List

The agenda list groups items by local date. Each date group has:

- Compact date badge, e.g. `18 Thu`.
- One or more reminder cards.
- Each reminder card shows:
  - A thin colour accent stripe.
  - Reminder title.
  - Time or date context.
  - Optional short description/location if available.

The list should have bottom padding so the fixed month map does not permanently hide the last items.

## Month Map

The month map is a compact 7-column grid fixed to the bottom of the calendar screen.

It shows:

- Weekday initials.
- One square per visible calendar day.
- Dimmed leading/trailing days from adjacent months if needed.
- Coloured squares for dates with reminders.

Interactions:

- Tapping a coloured day square scrolls/jumps the agenda list to that date.
- Tapping an empty day may jump to the date header if present, otherwise does nothing in v1.

## Item Colours

Colours are assigned per reminder/event, not by category.

Use a deterministic palette of at least 20 visually distinct colours. The same reminder should keep the same colour between refreshes by deriving the colour from a stable key, preferably reminder id.

For example:

```kotlin
val color = palette[(reminder.id.absoluteValue % palette.size).toInt()]
```

The palette should avoid being dominated by one hue family and should include a balanced mix of blues, greens, teals, yellows, oranges, reds, pinks, and purples.

## Same-Day Items

When a day has multiple reminders:

- 1 item: fill the square with that item's colour.
- 2 items: split the square into 2 equal vertical colour slices.
- 3 items: split into 3 equal vertical colour slices.
- 4 items: split into 4 equal vertical colour slices.
- More than 4 items: show the first 4 colour slices and a small `+N` marker for the remaining items.

Do not use diagonal stripes or category bands for same-day items.

## Multi-Day Items

If a reminder has `end_at_local`, it should appear on every covered local date from `scheduled_at_local` through `end_at_local`, inclusive.

In the month map, every covered day uses that reminder's colour. If the multi-day reminder overlaps with other reminders on a day, that day uses the same split-cell rules as above.

## Data Source

Use the existing reminder sync/cache path:

- `ReminderSyncManager.syncUpcomingOrUseCache(context)`
- `LifeCalendarItem.fromReminder(reminder)`
- `LifeCalendarItem.coveredDates()`

Future habit/workout/financial-alert sources can feed the same `LifeCalendarItem` model later.

## Testing

Add focused unit tests for:

- Range date selection for week/month/all.
- Month map day grouping.
- Multi-day reminder expansion.
- Deterministic colour assignment.
- Same-day cell slice limiting and `+N` overflow count.

Compose UI should be compile-verified with `:app:compileDebugKotlin`; unit tests should pass with `:app:testDebugUnitTest`.

## Acceptance Criteria

- Reminder titles are readable in portrait orientation.
- Month view no longer relies on narrow 7-column cells for title text.
- Month map remains compact and visible at the bottom.
- Left/right chevrons navigate ranges without boxed buttons.
- Each reminder has a stable colour from a palette of at least 20 colours.
- Same-day items are split into equal colour slices up to 4 items.
- Days with more than 4 items show a `+N` marker.
- Multi-day reminders colour every covered date in the month map.
