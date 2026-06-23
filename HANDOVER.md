# Lifelogger Handover

Generated: 2026-06-05. Latest update: 2026-06-19.

## Latest Session Status — 2026-06-19

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

#### Ask AI Integration (2026-06-19)

Full "Ask LifeLogger" feature — text and voice questions against lifelog data.

**New files:**
- `data/AskModels.kt` — `AskScope`, `AskResponse`, `Citation`, `AskSearchInfo` with JSON parsing
- `data/AskAudioRecorder.kt` — Lightweight `MediaRecorder` wrapper for short voice questions (max 60s, AAC 16kHz), separate from main recording pipeline
- `ui/AskScreen.kt` — Full-screen Compose UI with text input, mic button, recording timer, "Recent 30 days" scope chip, loading state, markdown answer rendering, confidence badge, and citation cards

**Modified files:**
- `data/ApiClient.kt` — Added `askText()` (POST /api/ask) and `askAudio()` (POST /api/ask/audio) methods
- `MainActivity.kt` — Added `"ask"` navigation route + clickable Ask AI dashboard card (4th card in home carousel)

**API contract:** See `docs/server-contracts/ask-ai-integration-2026-06-19.md`

**Key design decisions:**
- Voice clips are **not stored** in lifelogger DB or processed as summaries/reminders — recorded to temp file, deleted after upload
- Max recording duration 60 seconds
- After recording stops, clip uploaded immediately to `/api/ask/audio`
- Server returns `transcript` field which the app displays above the answer
- User can edit transcribed text and resubmit as text query
- "Recent 30 days" toggle sends `scope` with `start_date`/`end_date`; no toggle = no scope field at all (server searches everything)
- App does not cache or modify ask responses — everything displayed comes directly from server response JSON

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

#### Multiple Notifications Per Reminder — App Side Implemented

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

### Server State Update (2026-06-19)

The server team provided the following current state of the Ubuntu machine at `100.78.20.28` (Tailscale IP, `/home/tjjun/voxtral`):

**Runtime:**
- Main API server is running as a **direct Python process**: `/home/tjjun/voxtral/venv/bin/python server_voxtral.py`
- `voxtral.service` is **inactive** — restarting systemd alone won't replace the live server
- `/health` returns OK
- Active model: `mistralai/Voxtral-Small-24B-2507`
- GPU memory: ~13.54 GiB allocated, 14.24 GiB reserved, 15.7 GiB total
- `diarize_daemon.service` is active
- Ollama running locally on port 11434
- WhatsApp ingestor running in Docker (`whatsapp-ingestor`, `node src/index.js` + headless Chromium)

**Disk:** `/` is **97% used** (~3.1G free). Avoid large downloads, duplicate model caches, or bulk artifact generation.

**Database** (`lifelog_data/lifelog_segments.db`):
- segments: 752 | reminders: 38 | tasks: 0 | events: 0
- whatsapp_chats: 84 (14 enabled) | whatsapp_messages: 52
- session_notes: 12

**Ask / Life Query Feature:**
- `POST /api/ask` implemented in `ask_service.py`
- Scope is optional; default sources: summaries, reminders, events, tasks, whatsapp
- **Important:** The ask pipeline searches daily summary files, reminders, tasks, events, and WhatsApp messages. It does **not** search raw transcript segments or session_notes directly.
- Uses lexical/token matching (not embeddings), with glossary.json term expansion
- Answer generated by local Ollama model `gemma4:e4b`
- LLM instructed to answer only from supplied evidence
- Response includes answer, confidence, citations, and searched

**Standing Server-Side Issues:**
1. **Process management:** Live server is a manual Python process while `voxtral.service` is inactive. Before deploying/restarting, confirm and stop/replace the manual process deliberately.
2. **Disk tight:** ~3.1G free — avoid large operations.
3. **Ask is lightweight retrieval:** Not semantic search, does not search raw transcripts/session notes directly.
4. **WhatsApp:** Only enabled chats captured, only text messages stored.
5. **tasks/events tables empty:** Current reminder work centered on newer `reminders`/`notification_jobs` model.
6. **Notion push disabled** in `lifelog_data/config.json`.

---

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
- has a full reminder system with server sync, local AlarmManager notifications, Needs Review workflow, end dates, and custom notification offsets
- has a Life Calendar with agenda view and month map
- has an Ask AI feature for natural-language queries across lifelog data (text and voice)

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
- Room DB with RecordingEntity (version 6 schema, migrations up to 3→4→5→6)
- WorkManager upload (auto: immediate chunk + periodic 15-min, manual: button)
- Upload metadata: `type`, `recorded_at_ms`, `recorded_at_iso`, `recorded_date`, `recorded_timezone`, `prompt_id`, `session_id`
- Three recording modes (NORMAL/SESSION/REMINDER) with state machine coordinator
- Squeeze gesture detection through LSPosed hook (single/double/triple/continuous)
- Squeeze → action mapping (configurable in settings)
- UI: MainScreen, RecordingsScreen (inline player + waveform), ViewerScreen, SummaryDetailScreen (editable), TranscriptScreen, SearchScreen, SettingsScreen
- Prompt system: Room cache, server sync, picker UI, editor UI, pinning in settings
- Reminder system: data models, ApiClient endpoints, local notification infra (AlarmManager), ReminderSyncManager, RemindersScreen (Upcoming + Needs Review tabs), EditReminderScreen, navigation wiring
- Life Calendar: agenda view, month map, colour-coded reminders, multi-day support
- Ask AI: text and voice questions, markdown answers, citations, scope filtering
- 10+ unit tests for RecordingModeCoordinator, SqueezeGestureDetector, ReminderSqueezeMode, VisibleSqueezeActionBridge, RecordingUploadMetadata, ApiClient, CalendarAgendaModels, ReminderNotificationConfig — all passing

### New in This Session (2026-06-19)

#### Ask AI Integration

See "Most Recent App Changes" section above for full details.

Key files:
- `app/src/main/java/com/sam/lifelogger/data/AskModels.kt`
- `app/src/main/java/com/sam/lifelogger/data/AskAudioRecorder.kt`
- `app/src/main/java/com/sam/lifelogger/data/ApiClient.kt` (askText, askAudio methods)
- `app/src/main/java/com/sam/lifelogger/ui/AskScreen.kt`
- `app/src/main/java/com/sam/lifelogger/MainActivity.kt` (ask route + dashboard card)

Server contract: `docs/server-contracts/ask-ai-integration-2026-06-19.md`

---

## API Contract (As the App Expects It)

All endpoints use the server URL stored in SharedPreferences under `local_server_url`.
The app derives the base URL by stripping `/transcribe?task=transcribe` and `/transcribe` suffixes.

Default base URL: `http://100.78.20.28:8000`

### Existing Endpoints

#### GET /api/dates
Returns list of dates with summaries.

#### GET /api/summaries/{date}
Returns summary JSON for a given date (ISO date string).

#### GET /api/transcripts/{date}
Returns transcripts for a given date.

#### GET /api/sessions/{date}
Returns session notes for a given date.

#### GET /api/sessions/{date}/{filename}
Returns a specific session note.

#### GET /api/search?q={query}
Full-text search across transcripts.

#### GET /api/config
Returns server config.

#### POST /api/config
Accepts `{"key": true/false}` JSON body. Used for Notion push setting sync.

#### PUT /api/summaries/{date}
Accepts `{"markdown": "...", "title": "..."}` JSON body. Saves user-edited daily summary.

#### POST /transcribe
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

**Status:** Server was returning 500 (`name 'task' is not defined`). Server team has since confirmed the server is running and `/health` returns OK — may have been fixed.

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

#### GET /api/reminders/upcoming?start=<iso>&end=<iso>&status=pending
Returns upcoming reminders in a date range.

#### GET /api/reminders?status=pending&needs_review=true
Returns reminders needing review.

#### PATCH /api/reminders/{id}
Updates a reminder's fields.

#### POST /api/reminders/{id}/notifications
Creates a single notification job for a reminder.

#### PUT /api/reminders/{id}/notifications
Replaces all notification jobs for a reminder (used by the app's notification config UI).

#### GET /api/weather
Returns current weather data.

#### POST /api/ask
Text question to the LLM.

**Request:**
```json
{
  "question": "When did I last swim with Justin?",
  "scope": {
    "start_date": "2026-06-01",
    "end_date": "2026-06-18",
    "sources": ["summaries", "reminders", "events", "tasks", "whatsapp"]
  }
}
```
Scope is optional. When omitted, server searches everything.

**Response:**
```json
{
  "question": "...",
  "input_type": "text",
  "answer": "...",
  "confidence": "medium",
  "citations": [...],
  "searched": {...}
}
```

#### POST /api/ask/audio
Voice question. Multipart form with `audio` file, optional `scope` (JSON string), `recorded_at_ms`, `recorded_at_iso`, `recorded_timezone`.

**Response** adds `transcript` and `audio_duration_seconds` fields.

### Known API Mismatch (Prompts)

| Aspect | App does | Server expects | Action needed |
|--------|----------|---------------|---------------|
| POST create prompt URL | `POST /api/prompts` | `POST /api/prompts/{id}` | Align one way or the other |
| Create body field for title | `name` | `description` | Align field name |
| Create body `id` | Inside body | In URL path | Align |
| GET prompt title field | Reads `name` | Returns `description` | Align field name |
| GET prompt `is_default` | Reads `is_default` | Not confirmed present | Align if missing |

---

## Feature State Summary

| Feature | Status | Notes |
|---------|--------|-------|
| Core recording pipeline | ✅ Done | Foreground service, MediaRecorder, chunking |
| Room DB + entities | ✅ Done | Version 6, migrations 3→4 (sessionId), 4→5 (reminders), 5→6 (end dates) |
| Upload (WorkManager + manual) | ✅ Done | Sends type, recorded_at fields, prompt_id, session_id |
| Upload metadata | ✅ Done | recorded_date is canonical for server grouping |
| Three recording modes | ✅ Done | NORMAL/SESSION/REMINDER with coordinator |
| Squeeze gesture detection | ✅ Done | LSPosed hook, single/double/triple/continuous |
| Squeeze action mapping | ✅ Done | Configurable in settings |
| Squeeze → recording mode bridge | ✅ Done | Via VisibleSqueezeActionBridge.Handler |
| UI: main, recordings, viewer, etc. | ✅ Done | All Compose screens |
| Inline audio player + waveform | ✅ Done | |
| Server URL → Tailscale IP | ✅ Done | Default updated to 100.78.20.28:8000 |
| Constrained prompt box + pinning | ✅ Done | SettingsScreen: heightIn(max=320.dp), star pinning |
| SessionId chunk grouping | ✅ Done | UUID per session, baked into entity, sent as multipart field |
| Prompt Room table + DAO | ✅ Done | CachedPromptEntity, PromptDao, migration |
| Prompt sync logic | ✅ Done | PromptSyncManager.sync() / createPrompt() |
| Prompt picker UI | ✅ Done | PromptPickerScreen + PromptEditorScreen |
| Upload sends prompt_id | ✅ Done | Via multipart field for session recordings |
| Upload sends session_id | ✅ Done | Via multipart field for session recordings |
| **Reminder system (full)** | ✅ **Done** | Models, API, notifications, sync, screens, navigation |
| **Life Calendar** | ✅ **Done** | Agenda view, month map, colour-coded, multi-day |
| **Multiple notifications per reminder** | ✅ **Done** | None/At time/Custom with up to 3 offsets |
| **Reminder end dates** | ✅ **Done** | endAtLocal/endAtUtc, clearEndAt, DB migration |
| **Needs Review cancellation** | ✅ **Done** | Confirm dialog, status=cancelled, needs_review=false |
| **Ask AI (text + voice)** | ✅ **Done** | AskModels, AskAudioRecorder, AskScreen, API methods |
| **Homepage dashboard refresh** | ✅ **Done** | Carousel, compact buttons, sync/next-chunk indicators |
| **HTML rendering for notes/summaries** | ✅ **Done** | content_html / summary_html fields |
| GET /api/prompts endpoint | 🔴 **Needs server** | Not yet implemented on Ubuntu server |
| POST /api/prompts endpoint | 🔴 **Needs server** | Not yet implemented on Ubuntu server |
| Server reads prompt_id on upload | 🔴 **Needs server** | Must consume field during processing |
| Server reads session_id on upload | 🔴 **Needs server** | Must consume field during processing |
| Server /transcribe endpoint | 🔴 **Was broken** | Was returning 500; server team may have fixed (server is running) |
| Server-side reminder endpoints | 🔴 **Needs server** | Upcoming, Needs Review, PATCH, notifications endpoints |
| Server-side ask pipeline | 🟡 **Partial** | Works but doesn't search raw transcripts/session notes |
| Continuous squeeze re-engagement | 🟡 Needs retesting | Tuning constants at 0.95f / 1000ms |
| Background stop of reminder | 🟡 Needs design | Currently requires app to be visible |
| Server-side recording type filtering | 🟡 Needs server | type field sent but server doesn't use it |

---

## Known Issues

### 1. Server /transcribe Endpoint Status Unknown

The upload endpoint at `POST /transcribe` was returning 500 (`name 'task' is not defined`). The server team's latest update confirms the server is running and `/health` returns OK, but it's unclear if the transcribe endpoint was fixed. **Verify before assuming uploads work.**

### 2. Ask AI: Citations Returned Alongside Negative Answers

When the server answers "No information found about that" but includes a non-empty `citations` array, the app faithfully renders both. The app has no logic to suppress citations based on answer content.

**Likely root cause:** The LLM prompt generates answer and citations independently. When the LLM determines it cannot answer, it still returns whatever search results were found.

**Suggested server fix:** When the final answer indicates no relevant information was found, set `"citations": []` (empty array).

### 3. Ask AI: Future Reminders Not Found by Search

A test reminder for "tomorrow" was not found when asking "when is the next time I'm swimming with Juz" — even without scope filtering.

**Likely root causes (server-side):**
- Future dates not indexed — search/index might only cover past dates
- Reminder source not included in default search sources
- Reminder content in `description` field not searchable (only `title`)
- LLM not incorporating search results into answer

### 4. Ask AI Does Not Search Raw Transcripts or Session Notes

The current ask pipeline searches daily summary files, reminders, tasks, events, and WhatsApp messages. It does **not** search raw transcript segments or session_notes directly. If the app team means "all notes" as in raw transcripts/session notes, that is not implemented yet.

### 5. Continuous Squeeze Re-Engagement Needs Physical Retesting

After the first continuous squeeze → reminder → stop cycle, a subsequent continuous squeeze may not be recognized. Current tuning:
- `CONTINUOUS_HIGH_PROGRESS = 0.95f`
- `CONTINUOUS_HOLD_MS = 1000L`

### 6. Reminder Mode Outlasts the Visible App

If the user starts reminder mode and backgrounds the app, the squeeze handler is unregistered in `onPause()`, so there's no way to stop reminder early without reopening the app. The 2-minute auto-stop still fires.

### 7. Continuous Release Log Noise

High-frequency Elmyra progress callbacks produce ActivityManager broadcast warnings. Cosmetic only, no functional impact.

### 8. Timer Does Not Reset Across Mode Switches

Shows cumulative time since service start, not per-mode elapsed time.

### 9. Wi-Fi Only (No Mobile Data)

The user's Google Pixel 2 XL has no mobile data — it uses Wi-Fi only. The WorkManager upload constraint uses `NetworkType.UNMETERED`, which covers Wi-Fi. But if testing on a tether/hotspot that's metered, uploads would queue and never run.

### 10. Session Recordings Not Displaying Correctly

Session recordings (long continuous recordings with prompt-based processing) are not displaying correctly in the app UI. The exact nature of the display issue is not yet diagnosed — could be a grouping problem, a missing summary, or a rendering issue.

### 11. Server Disk is Tight

Server `/` is at 97% usage (~3.1G free). Avoid large downloads, duplicate model caches, or bulk artifact generation.

### 12. Server Process Management

The live FastAPI server is a manual Python process while `voxtral.service` is inactive. Before deploying/restarting, confirm and stop/replace the manual process deliberately.

---

## Current Server Contract Handoff

Primary server handoff documents:

1. **`docs/server-contracts/reminder-calendar-notification-contract-2026-06-09.md`**
   - Reminder response shape
   - End-date fields
   - Needs Review cancellation
   - `PUT /api/reminders/{id}/notifications`
   - No-notification behavior
   - Timezone/DST expectations
   - HTML fields for notes/summaries

2. **`docs/server-contracts/ask-ai-integration-2026-06-19.md`**
   - Ask AI API contract (text + voice)
   - Request/response shapes
   - Error responses
   - Issues to investigate (citations with negative answers, future reminders not found)

---

## Next Likely Work Items

### App-Side

1. **Verify /transcribe endpoint** — Test if the server transcribe endpoint is now working (was returning 500). If fixed, test end-to-end upload flow.
2. **Test reminder save against server** — Once `PUT /api/reminders/{id}/notifications` is deployed on server, test the full reminder edit/save flow.
3. **Session recordings display fix** — Investigate and fix why session recordings don't display correctly in the app UI.
4. **Build Habit reminders** — Recurring reminder support.
5. **Build simple Workout tracker** — Exercise logging feature.
6. **Add Financial alerts/news API** — Heaviest future item.

### Server-Side (Coordination Needed)

1. **Fix /transcribe endpoint** — If still broken, fix the Python `NameError` (`name 'task' is not defined`).
2. **Implement prompt endpoints** — GET /api/prompts and POST /api/prompts, consume prompt_id on upload.
3. **Resolve prompt API mismatch** — Align app and server on prompt API contract (POST body vs URL path, name vs description fields).
4. **Implement reminder endpoints** — `/api/reminders/upcoming`, `/api/reminders?status=pending&needs_review=true`, `PATCH /api/reminders/{id}`, `POST /api/reminders/{id}/notifications`, `PUT /api/reminders/{id}/notifications`.
5. **Fix Ask AI issues** — Citations with negative answers, future reminders not found, raw transcript/session note search.
6. **Free disk space** — Server is at 97% usage.

---

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

## Next Docs To Read

- `docs/server-contracts/ask-ai-integration-2026-06-19.md`
- `docs/server-contracts/reminder-calendar-notification-contract-2026-06-09.md`
- `docs/superpowers/specs/2026-06-05-reminder-system-design.md`
- `docs/superpowers/plans/2026-06-05-reminder-system-plan.md`
