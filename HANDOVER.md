# Lifelogger Handover

Generated: 2026-06-05. Latest update: 2026-06-09.

## Latest Session Status - 2026-06-09

This workspace is `G:\android_projects\lifelogger`. It currently reports `fatal: not a git repository`; do not rely on git commands unless a `.git` directory is restored.

The latest debug APK was built, verified, and installed on the connected `Pixel 2 XL - 15`.

Verification commands that passed at the end of the session:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

### Most Recent App Changes

#### Reminder Sync URL Fix

- `ApiClient.normalizeBaseUrl()` now strips malformed `/transcribe?task=...` style settings down to the real server base URL.
- This fixed the server log issue where reminder API paths were being appended after a transcription query string.
- Test coverage: `app/src/test/java/com/sam/lifelogger/data/ApiClientTest.kt`.

#### Homepage Dashboard Refresh

- Home screen now has a cleaner dashboard carousel with reminder, finance placeholder, and Ask AI placeholder cards.
- Manual sync moved into Settings.
- Home action buttons are compact square buttons.
- Recording status strip now includes sync and next-chunk indicators.
- Reminder dashboard times are shown in Australia/Sydney local time with DST behavior and without raw `+10` suffix.

Key files:

- `app/src/main/java/com/sam/lifelogger/MainActivity.kt`
- `app/src/main/java/com/sam/lifelogger/data/DashboardReminderSelector.kt`
- `app/src/main/java/com/sam/lifelogger/data/ReminderDisplayFormatter.kt`

#### HTML Rendering For Notes And Summaries

- Session notes now parse/use `content_html` when returned by the server.
- Daily summaries now parse/use `summary_html` when returned by the server.
- Fallback still displays raw content if HTML fields are absent.

Key files:

- `app/src/main/java/com/sam/lifelogger/data/SessionModels.kt`
- `app/src/main/java/com/sam/lifelogger/data/SummaryModels.kt`
- `app/src/main/java/com/sam/lifelogger/ui/SessionDetailScreen.kt`
- `app/src/main/java/com/sam/lifelogger/ui/SummaryDetailScreen.kt`

Server note: if bold text such as `**Date:**` still appears raw, the issue is likely server-side markdown-to-HTML conversion/prompting. See previous server instructions in this repo.

#### Reminder End Dates And Needs Review Cancellation

- Reminder model/cache/patch support `end_at_local` and `end_at_utc`.
- Room DB is now version 6 with migration adding `endAtLocal` and `endAtUtc` to cached reminders.
- Edit reminder screen has optional `End date (YYYY-MM-DD, optional)`.
- Clearing an existing end date sends explicit JSON nulls via `clearEndAt`.
- Needs Review reminders can now be cancelled with a confirm dialog; app sends `status=cancelled` and `needs_review=false`.

Key files:

- `app/src/main/java/com/sam/lifelogger/data/ReminderModels.kt`
- `app/src/main/java/com/sam/lifelogger/data/AppDatabase.kt`
- `app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt`
- `app/src/main/java/com/sam/lifelogger/ui/RemindersScreen.kt`

#### Life Calendar

- Home has a Calendar button and route.
- Calendar uses one readable agenda-style screen rather than narrow month/week/list grids.
- It has `Week / Month / All` range selector.
- It has subtle previous/next chevrons.
- It has a compact bottom `Month map`.
- Each reminder gets a stable per-item colour from a 20-colour palette.
- Multiple items on a day split the month-map square into equal colour slices up to 4 items.
- Days with more than 4 items show a small `+N`.
- Multi-day reminders colour every covered local date.
- Today's date has a thick white rounded border in the month map.

Key files:

- `app/src/main/java/com/sam/lifelogger/ui/LifeCalendarScreen.kt`
- `app/src/main/java/com/sam/lifelogger/calendar/LifeCalendarModels.kt`
- `app/src/main/java/com/sam/lifelogger/calendar/CalendarAgendaModels.kt`
- `app/src/test/java/com/sam/lifelogger/calendar/CalendarAgendaModelsTest.kt`

Design docs:

- `docs/superpowers/specs/2026-06-09-calendar-agenda-month-map-design.md`
- `docs/superpowers/plans/2026-06-09-calendar-agenda-month-map.md`

#### Multiple Notifications Per Reminder - App Side Implemented

The app-side multiple-notification controls are implemented and installed.

Reminder edit/review now supports:

- `None`
- `At time`
- `Custom`

`Custom` supports up to 3 offsets before the reminder start time, using minutes/hours/days. Notifications are anchored to `scheduled_at_utc`, not the end date.

The app now sends the server contract:

```http
PUT /api/reminders/{id}/notifications
```

With either:

```json
{
  "notifications_enabled": false,
  "jobs": []
}
```

or:

```json
{
  "notifications_enabled": true,
  "jobs": [
    {
      "notify_at_utc": "2026-06-10T06:00:00Z",
      "notification_title": "Dentist",
      "notification_body": "Clinic",
      "channel": "push"
    }
  ]
}
```

Key files:

- `app/src/main/java/com/sam/lifelogger/data/ReminderNotificationConfig.kt`
- `app/src/main/java/com/sam/lifelogger/data/ApiClient.kt`
- `app/src/main/java/com/sam/lifelogger/data/ReminderSyncManager.kt`
- `app/src/main/java/com/sam/lifelogger/ui/EditReminderScreen.kt`
- `app/src/test/java/com/sam/lifelogger/data/ReminderNotificationConfigTest.kt`

Important: until the server implements `PUT /api/reminders/{id}/notifications`, saving reminder edits that touch notification replacement may fail after the reminder `PATCH` step.

### Current Server Contract Handoff

Primary server handoff:

- `docs/server-contracts/reminder-calendar-notification-contract-2026-06-09.md`

It covers:

- reminder response shape
- end-date fields
- Needs Review cancellation
- `PUT /api/reminders/{id}/notifications`
- no-notification behavior
- timezone/DST expectations
- HTML fields for notes/summaries

### Next Likely Work Items

1. Test reminder save against the server once `PUT /api/reminders/{id}/notifications` is deployed.
2. If server does not implement `PUT` immediately, add a temporary app-side fallback path or save warning.
3. Build Habit reminders.
4. Build simple Workout tracker.
5. Add LLM database query API integration.
6. Add Financial alerts/news API later; this is likely the heaviest future item.

### Useful Latest Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
adb logcat -s MainActivity ReminderSync ReminderNotification ReminderBroadcast RecordingService UploadWorker
```

---

Generated: 2026-06-05 (updated 2026-06-05)

This document is the current project status for the Android app in
`G:\android_projects\lifelogger`. It is intended for the next coding agent.

## Project Summary

Lifelogger is an Android audio recorder paired with an Ubuntu server. The app:

- records microphone audio in chunks through a foreground service
- stores local recording metadata in Room
- uploads pending recordings through WorkManager when unmetered network is available
- sends server-side recording date metadata so delayed uploads are grouped by recording date, not upload date
- displays recordings and daily summaries in a Jetpack Compose UI
- receives Pixel 2 XL Active Edge / Elmyra squeeze gestures through an LSPosed hook
- supports 3 recording modes: NORMAL (5-min chunks), SESSION (60-min chunks), REMINDER (2-min chunks with auto-stop)
- has a session prompt system where the user can create and select processing prompt templates, synced to the server
- links session recording chunks with a shared `sessionId` UUID so the server can group related chunks

## Environment

- Workspace: `G:\android_projects\lifelogger`
- Shell: PowerShell on Windows
- Device: Google Pixel 2 XL, LineageOS 22.2, rooted with Magisk
- Squeeze source package on device: `org.protonaosp.elmyra`
- App package: `com.sam.lifelogger`
- LSPosed hook package: `com.sam.lifelogger.squeezehooks`
- ADB path:
  `C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Default server: `http://100.78.20.28:8000` (Tailscale IP)

Useful commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -s SqueezeHook SqueezeReceiver SqueezeDetector SqueezeActionMapper MainActivity RecordingService UploadWorker PromptSync
```

Note: this folder currently reports `fatal: not a git repository` if `git status`
is run from `G:\android_projects\lifelogger`. Do not rely on git diff unless the
repo is reattached or you confirm a `.git` directory exists.

## Current Working State

### Stable and Working

- Core recording pipeline (foreground service, MediaRecorder AAC 192kbps, chunking)
- Room DB with RecordingEntity (version 4 schema, migration 3→4 adds sessionId column)
- WorkManager upload (auto: immediate chunk + periodic 15-min, manual: button)
- Upload metadata: `type`, `recorded_at_ms`, `recorded_at_iso`, `recorded_date`, `recorded_timezone`, `prompt_id`, `session_id`
- Three recording modes (NORMAL/SESSION/REMINDER) with state machine coordinator
- Squeeze gesture detection through LSPosed hook (single/double/triple/continuous)
- Squeeze → action mapping (configurable in settings)
- UI: MainScreen, RecordingsScreen (inline player + waveform), ViewerScreen, SummaryDetailScreen (editable), TranscriptScreen, SearchScreen, SettingsScreen
- 10 unit tests for RecordingModeCoordinator, plus SqueezeGestureDetector, ReminderSqueezeMode, VisibleSqueezeActionBridge, RecordingUploadMetadata tests — all passing

### New in This Session (2026-06-05)

#### Constrained Prompt Box + Pinning in Settings

The session prompts section in Settings now has:
- A constrained scrolling container (`Box` with `heightIn(max = 320.dp)`) so ~4-5 prompts are visible at once. If there are more, the user scrolls within the box instead of taking over the whole settings page.
- Pin/favourite functionality: a star icon button on each prompt card. Filled star = pinned, outline star = unpinned.
- Pinned prompts sort to the top of the list (`sortedByDescending { it.id in pinnedIds }`).
- Pin state is stored locally in SharedPreferences as a `StringSet` under `pinned_prompt_ids`. Not synced to the server — it's purely a UI convenience.
- Added `material-icons-extended` library dependency for `Icons.Filled.Star` / `Icons.Filled.StarBorder`.

Files changed:
- `app/src/main/java/com/sam/lifelogger/ui/SettingsScreen.kt` — added `KEY_PINNED_IDS`, `pinnedIds` state, `togglePinned()`, `sortedPrompts`, constrained `Box` container, `isPinned`/`onTogglePin` params on `PromptSettingsCard`, star icon button
- `gradle/libs.versions.toml` — added `androidx-compose-material-icons-extended` dependency
- `app/build.gradle.kts` — added `implementation(libs.androidx.compose.material.icons.extended)`

#### sessionId for Session Recording Chunk Grouping

When a session recording runs, a UUID is generated at session start and stored in SharedPreferences as `active_session_id`. Each audio chunk's Room entity gets this `sessionId` stamped at creation time. The ID is:
- **Generated fresh** on `ACTION_START` when mode is `SESSION`
- **Generated on transition** from NORMAL→SESSION (via `ACTION_SWITCH_MODE`) if no ID exists yet
- **Preserved across reminder interruptions** — when a reminder interrupts a session, the sessionId stays in SharedPreferences so chunks that follow the reminder are linked to the same session
- **Cleared** on `ACTION_STOP` / `stopRecordingInternal()`
- Also cleared on stale-prefs cleanup in `MainActivity.onCreate()` (crash recovery)

The `session_id` is sent as a multipart form field on upload (only when non-null). The server can use it to group related chunks before processing.

Three new methods in `RecordingService`: `generateSessionId()`, `readSessionId()`, `clearSessionId()`.

Database migration: `MIGRATION_3_4` adds `ALTER TABLE recordings ADD COLUMN sessionId TEXT DEFAULT NULL`. DB version bumped from 3 to 4.

Edge case handled: zombies. If the app crashes mid-session, a stale UUID sits in prefs but the service is dead. Next `ACTION_START` generates a fresh UUID (via `generateSessionId()` which always overwrites), so old chunks don't get mixed with new.

Files changed:
- `app/src/main/java/com/sam/lifelogger/data/RecordingEntity.kt` — added `sessionId: String? = null` column
- `app/src/main/java/com/sam/lifelogger/data/AppDatabase.kt` — added `MIGRATION_3_4`, version 4
- `app/src/main/java/com/sam/lifelogger/recording/RecordingService.kt` — added sessionId lifecycle management (generate/read/clear), baked into entity creation, cleared on stop
- `app/src/main/java/com/sam/lifelogger/data/UploadRecordingsWorker.kt` — `uploadFileStatic()` and `uploadToLocal()` accept optional `sessionId`, sent as multipart field; `doWork()` passes `rec.sessionId`
- `app/src/main/java/com/sam/lifelogger/data/Uploader.kt` — manual sync passes `rec.sessionId` through to `uploadFileStatic`

#### Reminder System — Historical Note, Superseded

This 2026-06-05 note is superseded by the 2026-06-09 latest status section at the top of this file. The reminder system, Life Calendar, end-date support, Needs Review cancellation, and app-side multiple notification controls have now been implemented and installed. Keep the old design/plan references below only as historical context.

**Design spec:** `docs/superpowers/specs/2026-06-05-reminder-system-design.md`
**Implementation plan:** `docs/superpowers/plans/2026-06-05-reminder-system-plan.md`

The reminder system covers 7 tasks in dependency order:
1. **Data models** — `Reminder`, `NotificationJob`, `ReminderPatch` in `data/ReminderModels.kt` with `snake_case` JSON parsing and `optBoolCompat()` helper for SQLite integer booleans
2. **ApiClient methods** — `getUpcomingReminders()`, `getNeedsReviewReminders()`, `patchReminder()`, `createNotificationJob()`; this was the original 2026-06-05 plan and is now historical context
3. **Local notification infra** — `ReminderNotificationHelper` (channel, AlarmManager with exact-alarm fallback), `ReminderNotificationBroadcastReceiver`, `ReminderBootReceiver`
4. **ReminderSyncManager** — `syncUpcoming()`, `syncNeedsReview()`, `patchAndResync()` (two-step review save)
5. **RemindersScreen** — Two-tab UI (Upcoming grouped by Today/Tomorrow/date, Needs Review list)
6. **EditReminderScreen** — Full edit form with review mode guard requiring date before confirming
7. **Navigation wiring** — Button on MainScreen, NavHost routes, manifest permissions and receivers

**Key decisions:**
- DST-safe: `date.atTime(time).atZone(zone)` not `OffsetDateTime.of(date, time, zone.rules.getOffset(Instant.now()))`
- Review mode blocks save without a date
- Two-step save: `PATCH` then `POST /notifications`
- Date-bucket grouping for Upcoming tab
- Exact alarm check on Android 12+, fallback to inexact
- Boot receiver clears tracking only (no auto-reschedule)

**Historical server gaps at the time:**
- Needs Review endpoint `GET /api/reminders?status=pending&needs_review=true` was not on server yet at the time this note was written.
- All reminder `/api/reminders/*` endpoints needed server implementation at the time this note was written.

For current server requirements, use `docs/server-contracts/reminder-calendar-notification-contract-2026-06-09.md`.

#### Discovered: Server `/transcribe` Endpoint Returns 500 for All Uploads

The upload endpoint at `POST http://100.78.20.28:8000/transcribe?task=transcribe` returns a **500 Internal Server Error** for all recording types (normal, session, reminder):

```json
{"error":"Processing failed: name 'task' is not defined"}
```

This is a Python `NameError` on the server side — variable `task` is referenced but not defined / out of scope. This means **no recordings (of any type) are successfully processing on the server right now**. The app successfully records chunks, stores them in Room, and sends them via HTTP, but the server fails to process them.

The user is on Wi-Fi only (no mobile data), and the upload constraint uses `NetworkType.UNMETERED` which should work fine on Wi-Fi. The WorkManager uploads should be enqueued and attempted — but they'll fail because the server is returning 500.

**Root cause is on the server (Ubuntu machine at 100.78.20.28), not in the Android app.** No Python server code exists in this repository.

---

## API Contract (As the App Expects It)

All endpoints use the server URL stored in SharedPreferences under `local_server_url`.
The app derives the base URL by stripping `/transcribe?task=transcribe` and `/transcribe` suffixes.

Default base URL: `http://100.78.20.28:8000`

### Existing Endpoints (Working)

#### GET /api/dates
Returns list of dates with summaries.

#### GET /api/summaries/{date}
Returns summary JSON for a given date (ISO date string).

#### GET /api/transcripts/{date}
Returns transcripts for a given date.

#### GET /api/search?q={query}
Full-text search across transcripts.

#### GET /api/config
Returns server config.

#### POST /api/config
Accepts `{"key": true/false}` JSON body. Used for Notion push setting sync.

#### PUT /api/summaries/{date}
Accepts `{"markdown": "...", "title": "..."}` JSON body. Saves user-edited daily summary.

#### POST /transcribe (upload endpoint — currently broken, returns 500)
- **Method:** POST
- **Content-Type:** multipart/form-data
- **Fields:**
  | Field | Type | Required | Description |
  |-------|------|----------|-------------|
  | `audio` | file | yes | The audio file (MP4/AAC) |
  | `type` | string | yes | `"normal"`, `"session"`, or `"reminder"` |
  | `recorded_at_ms` | string | yes | Epoch milliseconds of recording start |
  | `recorded_at_iso` | string | yes | ISO 8601 with offset |
  | `recorded_date` | string | yes | ISO local date (canonical for grouping) |
  | `recorded_timezone` | string | yes | IANA timezone ID |
  | `prompt_id` | string | no | Prompt template ID to use for processing (sent for session recordings only) |
  | `session_id` | string | no | UUID linking related session chunks (sent for session recordings only) |

**Current issue:** Server returns HTTP 500 with `{"error":"Processing failed: name 'task' is not defined"}` — a Python `NameError` in the server's transcribe handler.

### New Endpoints Needed (Not Yet on Server)

#### GET /api/prompts
Returns all prompt templates stored on the server.

- **Response format:**
  ```json
  {
    "prompts": [
      {
        "id": "meeting-notes",
        "name": "Meeting Notes",
        "prompt": "You are an assistant...",
        "is_default": true,
        "updated_at": 1717430400000
      }
    ]
  }
  ```

#### POST /api/prompts
Creates or updates a prompt template on the server.

- **Request format:**
  ```json
  {
    "id": "meeting-notes",
    "name": "Meeting Notes",
    "prompt": "You are an assistant...",
    "is_default": false
  }
  ```
- The server stores this in `prompts.json` (or equivalent).

---

## Files Changed or Created

### New Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/sam/lifelogger/data/CachedPromptEntity.kt` | Room entity for locally cached prompts. Fields: `id` (PK), `name`, `prompt`, `isDefault`, `updatedAt`. |
| `app/src/main/java/com/sam/lifelogger/data/PromptDao.kt` | Room DAO for cached prompts. `getAllPrompts()` (Flow for UI), `getPromptById()`, `getDefaultPrompt()`, `deleteAll()` + `insertAll()` for atomic cache replacement. |
| `app/src/main/java/com/sam/lifelogger/data/PromptSyncManager.kt` | Singleton that manages server ↔ phone sync: `sync()` fetches from server and replaces cache; `createPrompt()` POSTs to server then syncs; `getAllPrompts()` returns Flow; `generateId()` creates slugs from names. |
| `app/src/main/java/com/sam/lifelogger/ui/PromptPickerScreen.kt` | Compose screen showing all synced prompts as selectable cards. Selected prompt persisted to SharedPreferences (`selected_prompt_id`). Has "New Prompt" button, "No prompt" clear option. |
| `app/src/main/java/com/sam/lifelogger/ui/PromptEditorScreen.kt` | Compose screen with name + prompt text fields. Saves via `PromptSyncManager.createPrompt()`. |
| `docs/superpowers/specs/2026-06-03-session-prompt-system-design.md` | Design document for the session prompt system. |

### Modified Files

| File | Change |
|------|--------|
| `app/src/main/java/com/sam/lifelogger/data/ApiClient.kt` | Added `getPrompts()` (GET /api/prompts) and `createPrompt()` (POST /api/prompts). Changed default URL from `192.168.86.41` to `100.78.20.28`. |
| `app/src/main/java/com/sam/lifelogger/data/AppDatabase.kt` | Added `CachedPromptEntity` to entities, added `promptDao()`, added `MIGRATION_2_3` that creates `cached_prompts` table. Added `MIGRATION_3_4` that adds `sessionId` column to recordings. DB version bumped from 2 to 3 (prompts), then 3 to 4 (sessionId). |
| `app/src/main/java/com/sam/lifelogger/data/RecordingEntity.kt` | Added `sessionId: String? = null` column. |
| `app/src/main/java/com/sam/lifelogger/data/UploadRecordingsWorker.kt` | `uploadFileStatic()` and `uploadToLocal()` now accept optional `promptId` and `sessionId`. `doWork()` reads selected prompt from prefs and passes both promptId (for session) and sessionId. Added `KEY_SELECTED_PROMPT_ID` constant. Default URL changed to Tailscale IP. |
| `app/src/main/java/com/sam/lifelogger/data/Uploader.kt` | Manual upload (`uploadPendingNow`) reads selected prompt from prefs and passes `promptId` (for session) and `rec.sessionId` to `uploadFileStatic`. |
| `app/src/main/java/com/sam/lifelogger/recording/RecordingService.kt` | Added sessionId lifecycle: `generateSessionId()`, `readSessionId()`, `clearSessionId()`, `KEY_ACTIVE_SESSION_ID` constant. Fresh UUID on START, preserved on SWITCH_MODE (only generated if null for NORMAL→SESSION transition), cleared on STOP. SessionId baked into `RecordingEntity` at chunk creation. |
| `app/src/main/java/com/sam/lifelogger/MainActivity.kt` | Added `lifecycleScope.launch { PromptSyncManager.sync(this@MainActivity) }` in `onCreate`. Added `/prompts` and `/prompts/new` NavHost routes. Added `onOpenPrompts` parameter to `AppNavigation` and `MainScreen`. Added "Session Prompt" button to main screen. Added `lifecycleScope` import. |
| `app/src/main/java/com/sam/lifelogger/ui/SettingsScreen.kt` | Default URL changed to Tailscale IP. Added constrained scrolling container (`heightIn(max=320.dp)`) for prompt list. Added pin/favourite functionality with star icons. Added `Icons.Filled.Star`, `Icons.Filled.StarBorder`, `Icons.Filled.Add` imports. |
| `gradle/libs.versions.toml` | Added `androidx-compose-material-icons-extended` dependency. |
| `app/build.gradle.kts` | Added `implementation(libs.androidx.compose.material.icons.extended)`. |

---

## Feature State Summary

| Feature | Status | Notes |
|---------|--------|-------|
| Core recording pipeline | ✅ Done | Foreground service, MediaRecorder, chunking |
| Room DB + entities | ✅ Done | Version 4, migration 3→4 adds sessionId |
| Upload (WorkManager + manual) | ✅ Done | Sends type, recorded_at fields, prompt_id, session_id |
| Upload metadata | ✅ Done | recorded_date is canonical for server grouping |
| Three recording modes | ✅ Done | NORMAL/SESSION/REMINDER with coordinator |
| Squeeze gesture detection | ✅ Done | LSPosed hook, single/double/triple/continuous |
| Squeeze action mapping | ✅ Done | Configurable in settings |
| Squeeze → recording mode bridge | ✅ Done | Via VisibleSqueezeActionBridge.Handler |
| UI: main, recordings, viewer, etc. | ✅ Done | All Compose screens |
| Inline audio player + waveform | ✅ Done | |
| Server URL → Tailscale IP | ✅ Done | Default updated to 100.78.20.28:8000 |
| **Constrained prompt box + pinning** | ✅ **Done** | SettingsScreen: heightIn(max=320.dp), star pinning, sorted pinned-first |
| **SessionId chunk grouping** | ✅ **Done** | UUID per session, baked into entity, sent as multipart field, preserved across reminders |
| **Prompt Room table + DAO** | ✅ **Done** | CachedPromptEntity, PromptDao, migration |
| **Prompt sync logic** | ✅ **Done** | PromptSyncManager.sync() / createPrompt() |
| **Prompt picker UI** | ✅ **Done** | PromptPickerScreen + PromptEditorScreen |
| **Upload sends prompt_id** | ✅ **Done** | Via multipart field for session recordings |
| **Upload sends session_id** | ✅ **Done** | Via multipart field for session recordings |
| **Reminder system design + plan** | ✅ **Done** | Spec at `docs/superpowers/specs/2026-06-05-reminder-system-design.md`, plan at `docs/superpowers/plans/2026-06-05-reminder-system-plan.md` |
| Reminder data models | 🟡 **Planned, not built** | Task 1 in implementation plan |
| ApiClient reminder endpoints | 🟡 **Planned, not built** | Task 2 in implementation plan |
| Local notification infra | 🟡 **Planned, not built** | Task 3 in implementation plan |
| ReminderSyncManager | 🟡 **Planned, not built** | Task 4 in implementation plan |
| RemindersScreen | 🟡 **Planned, not built** | Task 5 in implementation plan |
| EditReminderScreen | 🟡 **Planned, not built** | Task 6 in implementation plan |
| Navigation + manifest wiring | 🟡 **Planned, not built** | Task 7 in implementation plan |
| **GET /api/prompts endpoint** | 🔴 **Needs server** | Not yet implemented on Ubuntu server |
| **POST /api/prompts endpoint** | 🔴 **Needs server** | Not yet implemented on Ubuntu server |
| **Server reads prompt_id on upload** | 🔴 **Needs server** | Must consume field during processing |
| **Server reads session_id on upload** | 🔴 **Needs server** | Must consume field during processing |
| **Server /transcribe endpoint broken** | 🔴 **Needs server** | Returns 500: `name 'task' is not defined` — Python NameError |
| Continuous squeeze re-engagement | 🟡 Needs retesting | Tuning constants at 0.95f / 1000ms |
| Background stop of reminder | 🟡 Needs design | Currently requires app to be visible |
| Server-side recording type filtering | 🟡 Needs server | type field sent but server doesn't use it |

---

## Assumptions About Server Behavior

1. **GET /api/prompts** returns `{"prompts": [...]}` — the app parses `id`, `name`, `prompt`, `is_default`, `updated_at` from each object in the array. The `count` field in the response is ignored. If `name` is not present, the app also supports the server sending `description` as a fallback (the app reads `name` specifically — **see mismatch below**).

2. **POST /api/prompts** accepts the ID inside the JSON body (not in the URL path). The app POSTs to `/api/prompts` with `{"id": "...", "name": "...", "prompt": "...", "is_default": false}`. If the server prefers `POST /api/prompts/{id}`, the app side needs updating.

3. **Upload prompt_id** — the `prompt_id` multipart field is only sent for recordings where `type == "session"` and a prompt is selected. The server should treat it as optional; if absent or unrecognized, fall back to the default prompt or no prompt.

4. **Upload session_id** — the `session_id` multipart field is only sent for recordings where `sessionId` is non-null (session recordings). The server should use it to group related chunks before processing. If absent (normal/reminder recordings), no grouping needed.

5. **Sync on app launch** — the app calls `PromptSyncManager.sync()` once on startup. If the server is unreachable, it silently uses the last cached data. No retry logic is implemented.

6. **Prompt creation** — the app auto-generates the ID from the name (e.g. "Meeting Notes" → "meeting-notes"). If the server expects the ID in the URL path, the app needs adjustment.

### Known API Mismatch

The server-side Claude Code instance has been built with a different API contract:

| Aspect | App does | Server expects | Action needed |
|--------|----------|---------------|---------------|
| POST create prompt URL | `POST /api/prompts` | `POST /api/prompts/{id}` | Align one way or the other |
| Create body field for title | `name` | `description` | Align field name |
| Create body `id` | Inside body | In URL path | Align |
| GET prompt title field | Reads `name` | Returns `description` | Align field name |
| GET prompt `is_default` | Reads `is_default` | Not confirmed present | Align if missing |

## Challenges and Known Issues

### 1. Server `/transcribe` Endpoint Returns 500 (CRITICAL)

**All uploads fail** because the server handler at `POST /transcribe?task=transcribe` crashes with:
```
{"error":"Processing failed: name 'task' is not defined"}
```

This is a Python `NameError` — the variable `task` is used but not defined/out of scope in the server's transcribe handler. Fix is on the Ubuntu machine, **not in this codebase**.

### 2. Continuous squeeze re-engagement needs physical retesting

After the first continuous squeeze → reminder → stop cycle, a subsequent
continuous squeeze may not be recognized. Current tuning:
- `CONTINUOUS_HIGH_PROGRESS = 0.95f`
- `CONTINUOUS_HOLD_MS = 1000L`

### 3. Reminder mode outlasts the visible app

If the user starts reminder mode and backgrounds the app, the squeeze handler
is unregistered in `onPause()`, so there's no way to stop reminder early
without reopening the app. The 2-minute auto-stop still fires.

### 4. Continuous release log noise

High-frequency Elmyra progress callbacks produce ActivityManager broadcast
warnings. Cosmetic only, no functional impact.

### 5. Timer does not reset across mode switches

Shows cumulative time since service start, not per-mode elapsed time.

### 6. Server-side endpoints not yet built

GET /api/prompts and POST /api/prompts need to be implemented on the server.
The prompt_id and session_id fields from uploads also need to be consumed by the processing
pipeline. See API Contract section above.

### 7. Wi-Fi only (no mobile data)

The user's Google Pixel 2 XL has no mobile data — it uses Wi-Fi only. The WorkManager
upload constraint uses `NetworkType.UNMETERED`, which covers Wi-Fi, so this should be fine.
But if testing on a tether/hotspot that's metered, uploads would queue and never run.

### 8. Session recordings not displaying correctly in the app

Session recordings (long continuous recordings with prompt-based processing) are not
displaying correctly in the app UI. The exact nature of the display issue is not yet
diagnosed — it could be a grouping problem, a missing summary, or a rendering issue.
This needs investigation before the session recording feature can be considered usable.

## Testing Expectations

Always run at least:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

For squeeze behavior, install and test with logs:

```powershell
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -c
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -s SqueezeHook SqueezeReceiver SqueezeDetector SqueezeActionMapper MainActivity RecordingService UploadWorker PromptSync
```

Synthetic broadcast examples for testing:

```powershell
# Discrete squeeze
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am broadcast -a com.sam.lifelogger.SQUEEZE_DETECTED --es event_type detected -p com.sam.lifelogger

# Continuous start
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am broadcast -a com.sam.lifelogger.SQUEEZE_DETECTED --es event_type progress --ef progress 1.0 -p com.sam.lifelogger

# Continuous release
C:\Users\st_at\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am broadcast -a com.sam.lifelogger.SQUEEZE_DETECTED --es event_type progress --ef progress 0.0 -p com.sam.lifelogger
```

## Implementation Direction For Next Work

### Critical (Next Session — Execute the Reminder System Plan)

Execute `docs/superpowers/plans/2026-06-05-reminder-system-plan.md` in order:

1. **Task 1: Data models** — Create `data/ReminderModels.kt` with `Reminder`, `NotificationJob`, `ReminderPatch`, `optBoolCompat()` helper
2. **Task 2: ApiClient** — Add `getUpcomingReminders()`, `getNeedsReviewReminders()` (placeholder), `patchReminder()`, `createNotificationJob()` to existing `ApiClient.kt`
3. **Task 3: Local notification infra** — Create `notification/` package with `ReminderNotificationHelper`, `ReminderNotificationBroadcastReceiver`, `ReminderBootReceiver`
4. **Task 4: ReminderSyncManager** — Create `data/ReminderSyncManager.kt` following `PromptSyncManager` pattern
5. **Task 5: RemindersScreen** — Create `ui/RemindersScreen.kt` with Upcoming (date-grouped) + Needs Review tabs
6. **Task 6: EditReminderScreen** — Create `ui/EditReminderScreen.kt` with full edit form and review mode
7. **Task 7: Navigation + Manifest** — Add Reminders button to MainScreen, NavHost routes, manifest permissions and receivers

Each task has complete code blocks, test steps, and commit commands. The plan was reviewed and corrected with 10 specific fixes (DST-safe date conversion, `optBoolCompat`, review mode guard, date grouping, extracted save function, etc.).

After each task, rebuild and run:
```powershell
.\gradlew.bat :app:assembleDebug
```

### Important Caveats When Implementing

- **Needs Review endpoint doesn't exist on server yet** — the API method is a placeholder. The tab UI will return empty until the server adds `GET /api/reminders?status=pending&needs_review=true`
- **Server `/transcribe` still returns 500** — the upload pipeline works (files reach the server), but the server crashes with `name 'task' is not defined` before processing them. Reminder audio files will upload but the server won't create reminders until this is fixed on the Ubuntu machine
- **Boot receiver does not auto-reschedule** — it only clears stale tracking state. Notifications won't fire after reboot until the user opens the Reminders screen (which triggers syncUpcoming)
- **No Room cache for reminders** — the server is canonical. All data is fetched on each screen entry

### Medium Priority (Server-Side)
3. **Fix server `/transcribe` endpoint** — The Python `NameError` (`name 'task' is not defined`) blocks all recording processing. This is the single biggest bottleneck.
4. **Server-side prompt endpoints** — Build GET /api/prompts and POST /api/prompts, consume prompt_id on upload
5. **Resolve API mismatch** — Align app and server on prompt API contract (POST body vs URL path, name vs description fields)
6. **Server-side reminder endpoints** — `/api/reminders/upcoming`, `/api/reminders?status=pending&needs_review=true`, `PATCH /api/reminders/{id}`, `POST /api/reminders/{id}/notifications`

## Next Docs To Read

- `docs/superpowers/specs/2026-06-05-reminder-system-design.md`
- `docs/superpowers/plans/2026-06-05-reminder-system-plan.md`
- `docs/claude-next-prompt.md`

