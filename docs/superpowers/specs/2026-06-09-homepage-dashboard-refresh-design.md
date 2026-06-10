# Homepage Dashboard Refresh Design

## Goal

Refresh the Android app home screen so it uses screen space more efficiently and feels cleaner. The home screen should become a compact operational dashboard rather than a vertical list of large text buttons.

## Approved Direction

Use the approved v4 mockup:

`file:///G:/android_projects/lifelogger/.superpowers/brainstorm/homepage-dashboard-layout-v4.html`

The home screen keeps a dashboard-first layout with one centered dashboard module card visible at a time. The user can horizontally swipe between modules, but adjacent cards should not peek into view. A three-dot indicator is the only visible cue that additional dashboard cards exist.

## Home Screen Layout

The home screen should use this top-to-bottom structure:

1. Compact title row with app name and a Settings icon button.
2. Dashboard carousel.
3. Compact recording status strip.
4. Square action button grid.
5. Small status tiles for sync status and next-chunk information. The next-chunk tile should only show a value when that value is available.

The previous large "Not recording" heading should be removed. When not recording, the recording strip should show a quiet ready state. When recording, it should show the active mode, elapsed time, and the appropriate stop affordance.

## Dashboard Carousel

The dashboard carousel contains three modules in the first implementation:

1. Reminders
2. Finance
3. Ask AI

Only the Reminders module is data-backed in this pass. It should read from the existing reminder cache and show up to the two most pressing reminders, ordered by scheduled UTC time using the existing cached reminder data. If there are no reminders, it should show a clean empty state such as "No upcoming reminders".

Finance and Ask AI should be polished placeholders marked "Soon". They should not fake data, call new endpoints, or imply working functionality. Their purpose is to reserve the dashboard shape for future work and make the roadmap visible inside the app.

## Actions

Replace the large vertical text buttons with compact square buttons using icons plus small labels. The first pass should include:

- Record
- Life Log
- Reminders
- Recordings
- Prompts
- Settings

The Record button remains the primary action. It starts or stops the current normal recording flow using the existing callbacks. Reminder recording behavior continues to be handled by the existing squeeze/reminder mode flow; this refresh does not add a separate reminder-recording button.

## Manual Sync

Move "Sync manually" off the home screen and into Settings near the top of the settings page. It should reuse the existing `onUploadPending` callback path. The Settings screen should expose it as a compact action near server/upload configuration, because manual sync is an operational setting rather than a primary home action.

## Data Flow

On home screen entry, the dashboard should call the existing best-effort reminder sync/cache path and then display the returned reminders. If sync fails, it must fall back to cached reminders through the existing reminder data path.

No new server endpoints are required.

## UI Constraints

- No visible horizontal scrollbar in the dashboard carousel.
- No preview of adjacent dashboard cards.
- Use page dots for dashboard position.
- Keep cards and controls compact with moderate border radius.
- Avoid a dense or crowded dashboard; empty space is preferable to filling every pixel.
- Placeholder cards should be visually distinct enough to avoid confusion with working modules.

## Out of Scope

- Implementing Finance functionality.
- Implementing Ask AI functionality.
- Adding new backend contracts.
- Redesigning Reminders, Recordings, Viewer, or Prompt screens beyond navigation from the home page.
- Changing recording service behavior.

## Testing

Use focused tests where practical for any extracted dashboard reminder selection logic. Run the debug unit tests and assemble the debug APK after implementation.
